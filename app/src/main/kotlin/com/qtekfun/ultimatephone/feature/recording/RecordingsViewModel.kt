package com.qtekfun.ultimatephone.feature.recording

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.recording.RecordingNames
import com.qtekfun.ultimatephone.core.recording.RecordingStorage
import com.qtekfun.ultimatephone.core.recording.StoredRecording
import com.qtekfun.ultimatephone.recording.RecordingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One recording as listed. [label] and [incoming] come from the file name when it is one of ours; a file the user renamed
 * shows its own name.
 */
data class RecordingItem(
    val uri: Uri,
    val name: String,
    val label: String?,
    val incoming: Boolean?,
    val dateMillis: Long,
    val sizeBytes: Long,
    val durationMs: Long? = null
)

enum class ListMessage { RENAME_FAILED, DELETE_FAILED }

data class RecordingsState(
    val items: List<RecordingItem> = emptyList(),
    val loading: Boolean = true,
    val hasFolder: Boolean = true,
    val message: ListMessage? = null
)

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: RecordingSettings,
    private val storage: RecordingStorage
) : ViewModel() {
    private val mutable = MutableStateFlow(RecordingsState())
    val state: StateFlow<RecordingsState> = mutable

    private val player = RecordingPlayer(context)
    val playerState: StateFlow<PlayerState?> = player.state

    private val durations = HashMap<Uri, Long>()
    private var ticker: Job? = null
    private var loader: Job? = null

    init {
        reload()
    }

    fun reload() {
        loader?.cancel()
        loader = viewModelScope.launch {
            val folder = settings.current().folderUri?.let(Uri::parse)
            if (folder == null) {
                mutable.update { it.copy(items = emptyList(), loading = false, hasFolder = false) }
                return@launch
            }
            val items = withContext(Dispatchers.IO) { storage.list(folder).map(::toItem) }
            mutable.update { it.copy(items = items, loading = false, hasFolder = true) }
            // Durations need to open every file, so they fill in one by one after the list is shown.
            for (item in items.filter { it.durationMs == null }) {
                val duration = withContext(Dispatchers.IO) { durationOf(item.uri) }
                if (duration != null) {
                    durations[item.uri] = duration
                    mutable.update { s -> s.copy(items = s.items.map { if (it.uri == item.uri) it.copy(durationMs = duration) else it }) }
                }
            }
        }
    }

    private fun toItem(stored: StoredRecording): RecordingItem {
        val parsed = RecordingNames.parse(stored.name)
        val date = parsed?.dateTime?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: stored.lastModifiedMillis
        return RecordingItem(stored.uri, stored.name, parsed?.label, parsed?.incoming, date, stored.sizeBytes, durations[stored.uri])
    }

    /** Providers and the media framework fail with many exception types; no duration is the answer then. */
    private fun durationOf(uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        val duration = runCatching {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }.getOrNull()
        runCatching { retriever.release() }
        return duration
    }

    fun togglePlay(item: RecordingItem) {
        player.toggle(item.uri)
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                player.sync()
            }
        }
    }

    fun seek(positionMs: Int) = player.seekTo(positionMs)

    fun delete(item: RecordingItem) {
        viewModelScope.launch {
            if (playerState.value?.uri == item.uri) player.release()
            val deleted = withContext(Dispatchers.IO) { storage.delete(item.uri) }
            if (!deleted) mutable.update { it.copy(message = ListMessage.DELETE_FAILED) }
            reload()
        }
    }

    fun rename(item: RecordingItem, typed: String) {
        val newName = RecordingNames.renamed(typed, item.name) ?: return
        if (newName == item.name) return
        viewModelScope.launch {
            if (playerState.value?.uri == item.uri) player.release()
            val renamed = withContext(Dispatchers.IO) { storage.rename(item.uri, newName) }
            if (renamed == null) mutable.update { it.copy(message = ListMessage.RENAME_FAILED) }
            reload()
        }
    }

    fun messageShown() = mutable.update { it.copy(message = null) }

    override fun onCleared() {
        ticker?.cancel()
        player.release()
        super.onCleared()
    }

    private companion object {
        const val TICK_MS = 250L
    }
}
