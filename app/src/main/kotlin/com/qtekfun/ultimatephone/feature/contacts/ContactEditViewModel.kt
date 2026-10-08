package com.qtekfun.ultimatephone.feature.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.contacts.ContactAccount
import com.qtekfun.ultimatephone.core.contacts.ContactDate
import com.qtekfun.ultimatephone.core.contacts.ContactDraft
import com.qtekfun.ultimatephone.core.contacts.ContactsManager
import com.qtekfun.ultimatephone.core.contacts.DraftError
import com.qtekfun.ultimatephone.core.contacts.DraftValidation
import com.qtekfun.ultimatephone.core.contacts.LabelTypes
import com.qtekfun.ultimatephone.core.contacts.LabeledValue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactEditState(
    val draft: ContactDraft = ContactDraft(),
    val isNew: Boolean = true,
    val loading: Boolean = true,
    val missing: Boolean = false,
    val accounts: List<ContactAccount> = listOf(ContactAccount.LOCAL),
    val saving: Boolean = false,
    val validationError: DraftError? = null,
    val saveFailed: Boolean = false
)

@HiltViewModel
class ContactEditViewModel @Inject constructor(savedState: SavedStateHandle, private val contacts: ContactsManager) : ViewModel() {
    private val lookupKey: String = savedState.get<String>(ARG_LOOKUP_KEY).orEmpty()
    private val prefilledNumber: String = savedState.get<String>(ARG_NUMBER).orEmpty()

    private val _state = MutableStateFlow(ContactEditState(isNew = lookupKey.isEmpty()))
    val state: StateFlow<ContactEditState> = _state

    private val _saved = Channel<Unit>(Channel.BUFFERED)

    /** Fires once after the contact was written; the screen closes itself. */
    val saved = _saved.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val accounts = contacts.accounts()
        if (lookupKey.isEmpty()) {
            val phone = LabeledValue(prefilledNumber, LabelTypes.MOBILE)
            _state.update { it.copy(draft = ContactDraft(phones = listOf(phone)), accounts = accounts, loading = false) }
        } else {
            val detail = contacts.getContact(lookupKey)
            _state.update {
                if (detail == null) {
                    it.copy(loading = false, missing = true)
                } else {
                    it.copy(draft = detail.toDraft().ensureOnePhone(), accounts = accounts, loading = false, isNew = false)
                }
            }
        }
    }

    private fun ContactDraft.ensureOnePhone() = if (phones.isEmpty()) copy(phones = listOf(LabeledValue("", LabelTypes.MOBILE))) else this

    private fun edit(change: (ContactDraft) -> ContactDraft) = _state.update { it.copy(draft = change(it.draft), validationError = null, saveFailed = false) }

    fun setName(value: String) = edit { it.copy(name = value) }

    fun setOrganization(value: String) = edit { it.copy(organization = value) }

    fun setNotes(value: String) = edit { it.copy(notes = value) }

    fun setBirthday(value: ContactDate?) = edit { it.copy(birthday = value) }

    fun setAccount(value: ContactAccount) = edit { it.copy(account = value) }

    fun setPhone(index: Int, value: LabeledValue) = edit { it.copy(phones = it.phones.replaced(index, value)) }

    fun addPhone() = edit { it.copy(phones = it.phones + LabeledValue("", LabelTypes.MOBILE)) }

    fun removePhone(index: Int) = edit { it.copy(phones = it.phones.filterIndexed { i, _ -> i != index }) }

    fun setEmail(index: Int, value: LabeledValue) = edit { it.copy(emails = it.emails.replaced(index, value)) }

    fun addEmail() = edit { it.copy(emails = it.emails + LabeledValue("", LabelTypes.EMAIL_HOME)) }

    fun removeEmail(index: Int) = edit { it.copy(emails = it.emails.filterIndexed { i, _ -> i != index }) }

    private fun List<LabeledValue>.replaced(index: Int, value: LabeledValue) = mapIndexed { i, old -> if (i == index) value else old }

    fun save() {
        val current = _state.value
        if (current.saving) return
        when (val result = current.draft.validate()) {
            is DraftValidation.Invalid -> _state.update { it.copy(validationError = result.error) }
            is DraftValidation.Valid -> {
                _state.update { it.copy(saving = true, saveFailed = false) }
                viewModelScope.launch {
                    val ok = if (current.isNew) contacts.createContact(result.draft) != null else contacts.updateContact(result.draft)
                    _state.update { it.copy(saving = false, saveFailed = !ok) }
                    if (ok) _saved.send(Unit)
                }
            }
        }
    }

    companion object {
        const val ARG_LOOKUP_KEY = "lookupKey"
        const val ARG_NUMBER = "number"
    }
}
