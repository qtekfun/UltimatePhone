package com.qtekfun.ultimatephone.feature.contacts

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.contacts.ContactDetail
import com.qtekfun.ultimatephone.core.contacts.ContactGroupState
import com.qtekfun.ultimatephone.core.contacts.ContactsManager
import com.qtekfun.ultimatephone.core.telecom.CallPlacer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-off outcomes the detail screen reports. */
enum class DetailMessage { Exported, ExportFailed, GroupFailed }

sealed interface ContactDetailState {
    data object Loading : ContactDetailState

    data object NotFound : ContactDetailState

    data class Ready(val contact: ContactDetail) : ContactDetailState
}

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val contacts: ContactsManager,
    private val callPlacer: CallPlacer,
    private val transfer: VCardTransfer
) : ViewModel() {
    private val lookupKey: String = savedState.get<String>(ARG_LOOKUP_KEY).orEmpty()

    val state: StateFlow<ContactDetailState> = contacts.observeContact(lookupKey)
        .map { detail -> if (detail == null) ContactDetailState.NotFound else ContactDetailState.Ready(detail) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ContactDetailState.Loading)

    /** Every group with whether this contact is in it, for the "Groups" section. */
    val groups: StateFlow<List<ContactGroupState>> = contacts.observeContactGroups(lookupKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _messages = Channel<DetailMessage>(Channel.BUFFERED)

    /** One-off messages (export finished, group change failed). */
    val messages = _messages.receiveAsFlow()

    fun setGroupMembership(groupId: Long, member: Boolean) {
        viewModelScope.launch {
            if (!contacts.setGroupMembership(lookupKey, groupId, member)) _messages.send(DetailMessage.GroupFailed)
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val count = transfer.export(listOf(lookupKey), uri)
            _messages.send(if (count < 0) DetailMessage.ExportFailed else DetailMessage.Exported)
        }
    }

    /** Places the call; false when Telecom refused (for example no CALL_PHONE permission), so the caller can fall back to the dialer. */
    fun call(number: String): Boolean = callPlacer.placeCall(number)

    fun toggleStar(current: Boolean) {
        viewModelScope.launch { contacts.setStarred(lookupKey, !current) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (contacts.deleteContact(lookupKey)) onDeleted()
        }
    }

    companion object {
        const val ARG_LOOKUP_KEY = "lookupKey"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
