package com.qtekfun.ultimatephone.feature.recents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.calllog.CallLogEntry
import com.qtekfun.ultimatephone.core.calllog.CallLogRepository
import com.qtekfun.ultimatephone.core.calllog.CallStats
import com.qtekfun.ultimatephone.core.calllog.NumberKeys
import com.qtekfun.ultimatephone.core.calllog.entriesForNumber
import com.qtekfun.ultimatephone.core.calllog.isHiddenNumber
import com.qtekfun.ultimatephone.core.calllog.statsOf
import com.qtekfun.ultimatephone.core.contacts.ContactsRepository
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.CallPlacer
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.data.BusinessFinder
import com.qtekfun.ultimatephone.data.NoBusinesses
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CallDetailUiState(
    val number: String,
    val caller: CallerInfo? = null,
    /** Every call to or from the number, newest first. */
    val entries: List<CallLogEntry> = emptyList(),
    val stats: CallStats = statsOf(emptyList()),
    val isLoading: Boolean = true
) {
    val isPrivate: Boolean get() = isHiddenNumber(number)
}

sealed interface CallDetailEvent {
    data object CallFailed : CallDetailEvent

    /** The history of this number was deleted; the screen has nothing left to show. */
    data object Deleted : CallDetailEvent
}

@HiltViewModel
class CallDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val calls: CallLogRepository,
    contacts: ContactsRepository,
    private val placer: CallPlacer,
    private val numberKeys: NumberKeys,
    normalizer: PhoneNormalizer,
    regionProvider: RegionProvider,
    businesses: BusinessFinder = NoBusinesses
) : ViewModel() {
    private val number: String = savedStateHandle.get<String>(NUMBER_ARG).orEmpty()
    private val resolver = CallerResolver(contacts, normalizer, regionProvider, businesses)
    private val refresh = MutableStateFlow(0)
    private val eventChannel = Channel<CallDetailEvent>(Channel.BUFFERED)

    val events: Flow<CallDetailEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<CallDetailUiState> = combine(calls.observe(), refresh) { all, _ -> all }
        .map { all ->
            val entries = entriesForNumber(all, number, numberKeys.keyFunction())
            CallDetailUiState(
                number = number,
                caller = resolver.resolve(number, entries.firstNotNullOfOrNull { it.cachedName }),
                entries = entries,
                stats = statsOf(entries),
                isLoading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CallDetailUiState(number))

    /** Re-reads the contact match, for when the user comes back after creating or editing a contact. */
    fun refreshContacts() {
        if (resolver.clear()) refresh.update { it + 1 }
    }

    fun call() {
        if (isHiddenNumber(number)) return
        if (!placer.placeCall(number)) eventChannel.trySend(CallDetailEvent.CallFailed)
    }

    fun deleteHistory() {
        val ids = uiState.value.entries.map { it.id }
        viewModelScope.launch {
            calls.delete(ids)
            eventChannel.send(CallDetailEvent.Deleted)
        }
    }

    companion object {
        const val NUMBER_ARG = "number"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
