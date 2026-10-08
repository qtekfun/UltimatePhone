package com.qtekfun.ultimatephone.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.sync.SyncCounters
import com.qtekfun.ultimatephone.core.sync.SyncError
import com.qtekfun.ultimatephone.core.sync.SyncResult
import com.qtekfun.ultimatephone.sync.SyncRepository
import com.qtekfun.ultimatephone.sync.SyncUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Outcome of the last button the user pressed on the screen. */
sealed interface SyncAction {
    data object None : SyncAction

    data object Testing : SyncAction

    data object TestOk : SyncAction

    data object Saved : SyncAction

    data class Done(val counters: SyncCounters) : SyncAction

    data class Failed(val error: SyncError) : SyncAction
}

/** What the user typed. The password lives here only until it is saved, then the field is cleared. */
data class SyncForm(val serverUrl: String = "", val username: String = "", val password: String = "", val loaded: Boolean = false)

data class SyncScreenState(val sync: SyncUiState = SyncUiState(), val form: SyncForm = SyncForm(), val action: SyncAction = SyncAction.None)

@HiltViewModel
class SyncViewModel @Inject constructor(private val repository: SyncRepository) : ViewModel() {
    private val form = MutableStateFlow(SyncForm())
    private val action = MutableStateFlow<SyncAction>(SyncAction.None)

    val state: StateFlow<SyncScreenState> = combine(repository.state, form, action, ::SyncScreenState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SyncScreenState())

    init {
        // The saved address and user fill the form once; after that the form belongs to the user.
        viewModelScope.launch {
            val saved = repository.state.first()
            form.update { if (it.loaded) it else SyncForm(saved.serverUrl, saved.username, it.password, loaded = true) }
        }
    }

    fun setServer(value: String) = edit { it.copy(serverUrl = value) }

    fun setUser(value: String) = edit { it.copy(username = value) }

    fun setPassword(value: String) = edit { it.copy(password = value) }

    private fun edit(change: (SyncForm) -> SyncForm) {
        form.update { change(it.copy(loaded = true)) }
        action.value = SyncAction.None
    }

    fun test() {
        val f = state.value.form
        action.value = SyncAction.Testing
        viewModelScope.launch {
            val result = repository.testConnection(f.serverUrl, f.username, f.password.ifEmpty { null })
            action.value = if (result.isSuccess) SyncAction.TestOk else SyncAction.Failed(result.error ?: SyncError.UNKNOWN)
        }
    }

    /** Saves the account and, when [enable] is set, turns sync on and asks for a first sync. Returns through [onResult]. */
    fun save(enable: Boolean = false, onResult: (SyncResult) -> Unit = {}) {
        val f = state.value.form
        viewModelScope.launch {
            val result = repository.saveAccount(f.serverUrl, f.username, f.password.ifEmpty { null })
            if (result.isSuccess) {
                form.update { it.copy(password = "") }
                if (enable) repository.setEnabled(true)
                action.value = SyncAction.Saved
            } else {
                action.value = SyncAction.Failed(result.error ?: SyncError.UNKNOWN)
            }
            onResult(result)
        }
    }

    /** Tests the typed values first so a wrong password is shown before anything is saved, then saves and enables. */
    fun connect(onConnected: () -> Unit) {
        val f = state.value.form
        action.value = SyncAction.Testing
        viewModelScope.launch {
            val test = repository.testConnection(f.serverUrl, f.username, f.password.ifEmpty { null })
            if (!test.isSuccess) {
                action.value = SyncAction.Failed(test.error ?: SyncError.UNKNOWN)
                return@launch
            }
            save(enable = true) { if (it.isSuccess) onConnected() }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(enabled) }
    }

    fun syncNow() {
        action.value = SyncAction.Testing
        viewModelScope.launch {
            val result = repository.syncNow()
            action.value = if (result.isSuccess) SyncAction.Done(result.counters) else SyncAction.Failed(result.error ?: SyncError.UNKNOWN)
        }
    }

    fun forget() {
        viewModelScope.launch {
            repository.forget()
            form.value = SyncForm(loaded = true)
            action.value = SyncAction.None
        }
    }

    fun refreshPending() {
        viewModelScope.launch { repository.refreshPending() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
