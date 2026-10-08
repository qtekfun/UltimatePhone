package com.qtekfun.ultimatephone.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.contacts.ContactAccount
import com.qtekfun.ultimatephone.core.contacts.ContactGroup
import com.qtekfun.ultimatephone.core.contacts.ContactsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsUiState(val accounts: List<ContactAccount> = listOf(ContactAccount.LOCAL), val failed: Boolean = false)

@HiltViewModel
class GroupsViewModel @Inject constructor(private val contacts: ContactsManager) : ViewModel() {
    /** Null until the first read, so the screen does not flash "no groups". */
    val groups: StateFlow<List<ContactGroup>?> = contacts.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state

    init {
        viewModelScope.launch {
            val accounts = contacts.accounts()
            _state.update { it.copy(accounts = accounts) }
        }
    }

    fun create(title: String, account: ContactAccount) = launchAction { contacts.createGroup(title, account) }

    fun rename(group: ContactGroup, title: String) = launchAction { contacts.renameGroup(group.id, title) }

    fun delete(group: ContactGroup) = launchAction { contacts.deleteGroup(group.id) }

    fun failureShown() {
        _state.update { it.copy(failed = false) }
    }

    private fun launchAction(action: suspend () -> Boolean) {
        viewModelScope.launch {
            if (!action()) _state.update { it.copy(failed = true) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
