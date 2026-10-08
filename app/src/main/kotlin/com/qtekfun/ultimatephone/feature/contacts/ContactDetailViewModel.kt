package com.qtekfun.ultimatephone.feature.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.contacts.ContactDetail
import com.qtekfun.ultimatephone.core.contacts.ContactsManager
import com.qtekfun.ultimatephone.core.telecom.CallPlacer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ContactDetailState {
    data object Loading : ContactDetailState

    data object NotFound : ContactDetailState

    data class Ready(val contact: ContactDetail) : ContactDetailState
}

@HiltViewModel
class ContactDetailViewModel @Inject constructor(savedState: SavedStateHandle, private val contacts: ContactsManager, private val callPlacer: CallPlacer) :
    ViewModel() {
    private val lookupKey: String = savedState.get<String>(ARG_LOOKUP_KEY).orEmpty()

    val state: StateFlow<ContactDetailState> = contacts.observeContact(lookupKey)
        .map { detail -> if (detail == null) ContactDetailState.NotFound else ContactDetailState.Ready(detail) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ContactDetailState.Loading)

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
