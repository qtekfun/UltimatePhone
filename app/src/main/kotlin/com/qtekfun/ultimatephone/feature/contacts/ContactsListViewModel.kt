package com.qtekfun.ultimatephone.feature.contacts

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.contacts.ContactGroup
import com.qtekfun.ultimatephone.core.contacts.ContactsManager
import com.qtekfun.ultimatephone.core.contacts.T9
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the list screen shows; [Loading] until the first read of the contacts finishes. */
sealed interface ContactsListState {
    data object Loading : ContactsListState

    data class Ready(val content: ContactListContent, val totalContacts: Int) : ContactsListState
}

/** Outcome of an export, shown once as a message. */
sealed interface ExportResult {
    data class Done(val count: Int) : ExportResult

    data object Failed : ExportResult
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsListViewModel @Inject constructor(private val contacts: ContactsManager, private val transfer: VCardTransfer) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val groups: StateFlow<List<ContactGroup>> = contacts.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val chosenGroup = MutableStateFlow<Long?>(null)

    /** The group the list is filtered by; null for everybody, also when the chosen group has been deleted. */
    val activeGroup: StateFlow<Long?> = combine(chosenGroup, groups) { id, all -> id?.takeIf { wanted -> all.any { it.id == wanted } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val groupMembers = activeGroup.flatMapLatest { id -> if (id == null) flowOf(null) else contacts.observeGroupMembers(id) }

    private val numberMatches = _query.mapLatest { text ->
        val digits = T9.digitsOf(text)
        // Only look numbers up for what clearly is a number, not for names that happen to contain a digit.
        if (digits.length >= MIN_NUMBER_DIGITS && text.all { it.isDigit() || it in NUMBER_SEPARATORS }) {
            contacts.suggest(digits, MAX_NUMBER_MATCHES).map { it.lookupKey }.toSet()
        } else {
            emptySet()
        }
    }

    val state: StateFlow<ContactsListState> = combine(contacts.observeContacts(), _query, numberMatches, groupMembers) { all, text, matches, members ->
        val shown = if (members == null) all else all.filter { it.lookupKey in members }
        ContactsListState.Ready(ContactListBuilder.build(shown, text, matches), shown.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ContactsListState.Loading)

    private val _selection = MutableStateFlow<Set<String>>(emptySet())

    /** Lookup keys of the contacts picked for export; empty outside selection mode. */
    val selection: StateFlow<Set<String>> = _selection

    private val _exportResults = Channel<ExportResult>(Channel.BUFFERED)
    val exportResults = _exportResults.receiveAsFlow()

    private var pendingExport: List<String>? = null
    private var pendingAll = false

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun chooseGroup(id: Long?) {
        chosenGroup.value = id
    }

    fun toggleSelection(lookupKey: String) {
        _selection.update { if (lookupKey in it) it - lookupKey else it + lookupKey }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /** Remembers what to export once the user has picked the file; returns the suggested name. */
    fun prepareExport(all: Boolean): String {
        pendingAll = all
        pendingExport = if (all) null else _selection.value.toList()
        return exportFileName(single = !all && _selection.value.size == 1)
    }

    fun exportTo(uri: Uri) {
        val keys = pendingExport
        val all = pendingAll
        if (!all && keys.isNullOrEmpty()) return
        viewModelScope.launch {
            val count = transfer.export(if (all) null else keys, uri)
            _exportResults.send(if (count < 0) ExportResult.Failed else ExportResult.Done(count))
            if (!all) clearSelection()
        }
    }

    private companion object {
        const val MIN_NUMBER_DIGITS = 3
        const val MAX_NUMBER_MATCHES = 500
        const val STOP_TIMEOUT_MS = 5_000L
        const val NUMBER_SEPARATORS = "+-() .#*"
    }
}
