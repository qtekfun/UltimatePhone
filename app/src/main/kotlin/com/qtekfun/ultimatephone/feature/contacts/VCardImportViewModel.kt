package com.qtekfun.ultimatephone.feature.contacts

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.contacts.ContactAccount
import com.qtekfun.ultimatephone.core.contacts.ContactsManager
import com.qtekfun.ultimatephone.core.contacts.DuplicateDetector
import com.qtekfun.ultimatephone.core.contacts.ImportDuplicates
import com.qtekfun.ultimatephone.core.contacts.VCardContact
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The cards of the chosen file, what already exists, and the user's choices. */
data class ImportPreview(
    val contacts: List<VCardContact>,
    val invalid: Int,
    /** Indices into [contacts] of cards that look like stored contacts. */
    val duplicates: Set<Int>,
    val accounts: List<ContactAccount>,
    val account: ContactAccount,
    val skipDuplicates: Boolean
) {
    /** The cards that will be created with the current choices. */
    val toImport: List<VCardContact>
        get() = contacts.filterIndexed { index, _ -> !(skipDuplicates && index in duplicates) }
}

/** What the import did. */
data class ImportSummary(val imported: Int, val skippedDuplicates: Int, val invalid: Int, val failed: Int)

sealed interface ImportState {
    /** Waiting for the file picker. */
    data object Idle : ImportState

    data object Reading : ImportState

    /** The file could not be read, or holds no contact. */
    data class Failed(val unreadable: Boolean) : ImportState

    data class Preview(val preview: ImportPreview) : ImportState

    data object Importing : ImportState

    data class Done(val summary: ImportSummary) : ImportState
}

@HiltViewModel
class VCardImportViewModel @Inject constructor(
    private val contacts: ContactsManager,
    private val transfer: VCardTransfer,
    private val normalizer: PhoneNormalizer,
    private val regionProvider: RegionProvider
) : ViewModel() {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state

    /** True once the picker has been opened by the screen, so a rotation does not open it again. */
    var pickerOpened = false

    fun load(uri: Uri) {
        _state.value = ImportState.Reading
        viewModelScope.launch {
            val result = transfer.read(uri)
            if (result == null) {
                _state.value = ImportState.Failed(unreadable = true)
                return@launch
            }
            if (result.contacts.isEmpty()) {
                _state.value = ImportState.Failed(unreadable = false)
                return@launch
            }
            val existing = contacts.duplicateCandidates()
            val region = regionProvider.defaultRegion()
            val duplicates = withContext(Dispatchers.Default) { ImportDuplicates.of(DuplicateDetector(normalizer, region), existing, result.contacts) }
            val accounts = contacts.accounts()
            _state.value = ImportState.Preview(
                ImportPreview(
                    contacts = result.contacts,
                    invalid = result.skipped,
                    duplicates = duplicates,
                    accounts = accounts,
                    account = accounts.first(),
                    skipDuplicates = duplicates.isNotEmpty()
                )
            )
        }
    }

    fun chooseAccount(account: ContactAccount) = updatePreview { it.copy(account = account) }

    fun setSkipDuplicates(skip: Boolean) = updatePreview { it.copy(skipDuplicates = skip) }

    private fun updatePreview(change: (ImportPreview) -> ImportPreview) {
        _state.update { current -> if (current is ImportState.Preview) ImportState.Preview(change(current.preview)) else current }
    }

    fun import() {
        val preview = (_state.value as? ImportState.Preview)?.preview ?: return
        _state.value = ImportState.Importing
        viewModelScope.launch {
            val cards = preview.toImport
            val created = contacts.importContacts(cards, preview.account)
            _state.value = ImportState.Done(
                ImportSummary(
                    imported = created,
                    skippedDuplicates = preview.contacts.size - cards.size,
                    invalid = preview.invalid,
                    failed = cards.size - created
                )
            )
        }
    }
}
