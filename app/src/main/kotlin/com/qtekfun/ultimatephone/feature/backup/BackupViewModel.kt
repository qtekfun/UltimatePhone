package com.qtekfun.ultimatephone.feature.backup

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.backup.BackupSection
import com.qtekfun.ultimatephone.core.settings.BackupException
import com.qtekfun.ultimatephone.core.settings.BackupFailure
import com.qtekfun.ultimatephone.core.settings.BackupService
import com.qtekfun.ultimatephone.core.settings.OpenedBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One restorable part as the screen lists it. */
data class SectionItem(val id: String, @StringRes val title: Int)

/** Result shown after an action. [sections] are the titles that go into the text, when it has a list. */
sealed interface BackupMessage {
    data object ExportDone : BackupMessage

    data object ExportFailed : BackupMessage

    data class ImportFailed(@StringRes val text: Int) : BackupMessage

    data class ImportDone(val sections: List<Int>) : BackupMessage

    data class ImportPartial(val sections: List<Int>) : BackupMessage
}

/** The user is choosing what to restore from a backup that already passed authentication. */
data class ImportChoice(val createdAt: Long, val available: List<SectionItem>, val selected: Set<String>)

data class BackupState(
    val sections: List<SectionItem> = emptyList(),
    val busy: Boolean = false,
    val askExportPassword: Boolean = false,
    val askImportPassword: Boolean = false,
    val choice: ImportChoice? = null,
    val message: BackupMessage? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: BackupService,
    sections: @JvmSuppressWildcards Set<BackupSection>
) : ViewModel() {
    private val items = sections.sortedBy { it.id }.map { SectionItem(it.id, it.title) }
    private val _state = MutableStateFlow(BackupState(sections = items))
    val state: StateFlow<BackupState> = _state

    // Secrets and file contents live only in memory, and are wiped as soon as the action ends.
    private var exportPassword: CharArray? = null
    private var importFile: ByteArray? = null
    private var opened: OpenedBackup? = null

    fun askExport() = _state.update { it.copy(askExportPassword = true, message = null) }

    fun cancelExport() {
        wipe()
        _state.update { it.copy(askExportPassword = false) }
    }

    /** Keeps [password] until the user has chosen the file; the screen then opens the system file picker. */
    fun exportPasswordChosen(password: String) {
        exportPassword = password.toCharArray()
        _state.update { it.copy(askExportPassword = false) }
    }

    fun exportTo(uri: Uri?) {
        val password = exportPassword
        exportPassword = null
        if (uri == null || password == null) {
            password?.fill(' ')
            return
        }
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val ok = try {
                val bytes = withContext(Dispatchers.Default) { service.export(password) }
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No stream")
                    out.use { it.write(bytes) }
                }
                true
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                false
            } finally {
                password.fill(' ')
            }
            _state.update { it.copy(busy = false, message = if (ok) BackupMessage.ExportDone else BackupMessage.ExportFailed) }
        }
    }

    fun filePicked(uri: Uri?) {
        if (uri == null) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { readAtMost(it, MAX_FILE_BYTES + 1) }
                } catch (_: IOException) {
                    null
                }
            }
            if (bytes == null || bytes.size > MAX_FILE_BYTES) {
                _state.update { it.copy(busy = false, message = BackupMessage.ImportFailed(R.string.backup_import_unreadable)) }
            } else {
                importFile = bytes
                _state.update { it.copy(busy = false, askImportPassword = true) }
            }
        }
    }

    fun cancelImport() {
        wipe()
        _state.update { it.copy(askImportPassword = false, choice = null) }
    }

    fun openWithPassword(password: String) {
        val file = importFile ?: return
        val chars = password.toCharArray()
        _state.update { it.copy(busy = true, askImportPassword = false) }
        viewModelScope.launch {
            try {
                val backup = withContext(Dispatchers.Default) { service.open(file, chars) }
                val known = items.filter { it.id in backup.sectionIds }
                opened = backup
                _state.update {
                    it.copy(
                        busy = false,
                        message = if (known.isEmpty()) BackupMessage.ImportFailed(R.string.backup_import_nothing) else null,
                        choice = if (known.isEmpty()) null else ImportChoice(backup.createdAt, known, known.mapTo(HashSet()) { s -> s.id })
                    )
                }
            } catch (e: BackupException) {
                _state.update { it.copy(busy = false, message = BackupMessage.ImportFailed(failureText(e.failure))) }
                wipe()
            } finally {
                chars.fill(' ')
            }
        }
    }

    fun toggle(id: String) = _state.update { s ->
        val choice = s.choice ?: return@update s
        val next = if (id in choice.selected) choice.selected - id else choice.selected + id
        s.copy(choice = choice.copy(selected = next))
    }

    fun restore() {
        val backup = opened ?: return
        val choice = _state.value.choice ?: return
        _state.update { it.copy(busy = true, choice = null) }
        viewModelScope.launch {
            val message = try {
                val report = service.restore(backup, choice.selected)
                val titles = { ids: Collection<String> -> items.filter { it.id in ids }.map { it.title } }
                if (report.failed.isEmpty()) BackupMessage.ImportDone(titles(report.restored)) else BackupMessage.ImportPartial(titles(report.failed.keys))
            } catch (_: BackupException) {
                BackupMessage.ImportFailed(R.string.backup_import_invalid)
            }
            wipe()
            _state.update { it.copy(busy = false, message = message) }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        wipe()
    }

    private fun wipe() {
        exportPassword?.fill(' ')
        exportPassword = null
        importFile?.fill(0)
        importFile = null
        opened = null
    }

    @StringRes
    private fun failureText(failure: BackupFailure): Int = when (failure) {
        BackupFailure.WRONG_PASSWORD_OR_ALTERED -> R.string.backup_import_wrong
        BackupFailure.NOT_A_BACKUP -> R.string.backup_import_not_backup
        BackupFailure.UNSUPPORTED_VERSION -> R.string.backup_import_newer
        BackupFailure.CORRUPT, BackupFailure.INVALID_PAYLOAD -> R.string.backup_import_corrupt
    }

    /** At most [limit] bytes, so a huge file picked by mistake cannot exhaust memory. */
    private fun readAtMost(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (out.size() < limit) {
            val read = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val MAX_FILE_BYTES = 64 * 1024 * 1024
    }
}
