package com.qtekfun.ultimatephone.feature.recents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.calllog.CallLogEntry
import com.qtekfun.ultimatephone.core.calllog.CallLogFilter
import com.qtekfun.ultimatephone.core.calllog.CallLogRepository
import com.qtekfun.ultimatephone.core.calllog.CallType
import com.qtekfun.ultimatephone.core.calllog.DaySection
import com.qtekfun.ultimatephone.core.calllog.NumberKeys
import com.qtekfun.ultimatephone.core.calllog.groupCalls
import com.qtekfun.ultimatephone.core.calllog.sectionByDay
import com.qtekfun.ultimatephone.core.contacts.ContactsRepository
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.CallPlacer
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.core.telecom.SimAccount
import com.qtekfun.ultimatephone.core.telecom.SimRepository
import com.qtekfun.ultimatephone.data.BusinessFinder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row of the history: a call or several consecutive calls to the same number. */
data class RecentsRow(
    /** Id of the newest call; identifies the row for selection and list keys. */
    val key: Long,
    /** Ids of every call folded into this row. */
    val entryIds: List<Long>,
    val number: String,
    val isPrivate: Boolean,
    val caller: CallerInfo,
    val type: CallType,
    val count: Int,
    val sim: SimAccount?,
    val dateMillis: Long,
    val isNew: Boolean
)

data class RecentsSection(val section: DaySection, val rows: List<RecentsRow>)

data class RecentsUiState(
    val filter: CallLogFilter = CallLogFilter.All,
    /** The phone's SIMs; the filter chips and SIM badges only appear with two or more. */
    val sims: List<SimAccount> = emptyList(),
    val sections: List<RecentsSection> = emptyList(),
    /** Keys of the selected rows; non-empty means selection mode. */
    val selected: Set<Long> = emptySet(),
    val isLoading: Boolean = true
) {
    val isDualSim: Boolean get() = sims.size >= 2
    val isSelecting: Boolean get() = selected.isNotEmpty()
    val isEmpty: Boolean get() = !isLoading && sections.isEmpty()
}

/** One-off things the screen has to tell the user. */
sealed interface RecentsEvent {
    data object CallFailed : RecentsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecentsViewModel internal constructor(
    private val calls: CallLogRepository,
    private val simRepository: SimRepository,
    private val placer: CallPlacer,
    private val numberKeys: NumberKeys,
    private val resolver: CallerResolver,
    private val clock: Clock
) : ViewModel() {
    @Inject
    constructor(
        calls: CallLogRepository,
        contacts: ContactsRepository,
        simRepository: SimRepository,
        placer: CallPlacer,
        numberKeys: NumberKeys,
        normalizer: PhoneNormalizer,
        regionProvider: RegionProvider,
        businesses: BusinessFinder
    ) : this(calls, simRepository, placer, numberKeys, CallerResolver(contacts, normalizer, regionProvider, businesses), Clock.systemDefaultZone())

    private val filter = MutableStateFlow<CallLogFilter>(CallLogFilter.All)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val refresh = MutableStateFlow(0)
    private val eventChannel = Channel<RecentsEvent>(Channel.BUFFERED)

    val events: Flow<RecentsEvent> = eventChannel.receiveAsFlow()

    private data class Content(val sims: List<SimAccount>, val sections: List<RecentsSection>)

    private val content: Flow<Content> = combine(filter, refresh) { f, _ -> f }
        .flatMapLatest { f -> calls.observe(f).map { buildContent(it) } }

    val uiState: StateFlow<RecentsUiState> = combine(filter, selection, content) { f, selected, c ->
        val keys = c.sections.flatMap { s -> s.rows.map { it.key } }.toSet()
        RecentsUiState(f, c.sims, c.sections, selected.intersect(keys), isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecentsUiState())

    private suspend fun buildContent(entries: List<CallLogEntry>): Content {
        val groups = groupCalls(entries, clock.zone, numberKeys.keyFunction())
        val sections = sectionByDay(groups, clock).map { day ->
            RecentsSection(
                day.section,
                day.groups.map { group ->
                    val latest = group.latest
                    RecentsRow(
                        key = latest.id,
                        entryIds = group.entries.map { it.id },
                        number = latest.number,
                        isPrivate = latest.isPrivateNumber,
                        caller = resolver.resolve(latest.number, latest.cachedName),
                        type = latest.type,
                        count = group.count,
                        sim = latest.sim,
                        dateMillis = latest.dateMillis,
                        isNew = group.entries.any { it.isNew }
                    )
                }
            )
        }
        return Content(simRepository.accounts(), sections)
    }

    fun setFilter(newFilter: CallLogFilter) {
        if (newFilter == filter.value) return
        selection.value = emptySet()
        filter.value = newFilter
    }

    fun toggleSelection(key: Long) {
        selection.update { if (key in it) it - key else it + key }
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun deleteSelected() {
        val state = uiState.value
        val ids = state.sections.flatMap { it.rows }.filter { it.key in state.selected }.flatMap { it.entryIds }
        selection.value = emptySet()
        viewModelScope.launch { calls.delete(ids) }
    }

    fun clearHistory() {
        selection.value = emptySet()
        viewModelScope.launch { calls.clearAll() }
    }

    fun markAllRead() {
        viewModelScope.launch { calls.markAllRead() }
    }

    /** Re-reads contact names, for when the user comes back after editing a contact. */
    fun refreshContacts() {
        if (resolver.clear()) refresh.update { it + 1 }
    }

    fun callBack(row: RecentsRow) {
        if (row.isPrivate) return
        if (!placer.placeCall(row.number)) eventChannel.trySend(RecentsEvent.CallFailed)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
