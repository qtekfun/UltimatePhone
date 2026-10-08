package com.qtekfun.ultimatephone.feature.spam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.spam.lists.EntryKind
import com.qtekfun.ultimatephone.core.spam.lists.ListEntry
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.core.spam.lists.SpamListsRepository
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What went wrong when adding an entry. */
enum class ListInputError { INVALID_NUMBER, INVALID_PREFIX }

data class SpamListState(val list: ListType, val entries: List<ListEntry> = emptyList(), val error: ListInputError? = null, val isLoading: Boolean = true)

/** Route value for the list in the navigation argument. */
fun ListType.routeName(): String = when (this) {
    ListType.OWN -> "own"
    ListType.WHITELIST -> "allowed"
}

fun listTypeOfRoute(name: String?): ListType = if (name == "allowed") ListType.WHITELIST else ListType.OWN

/** One view model for both user lists: the spam list and the allowed numbers behave the same. */
@HiltViewModel
class SpamListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val lists: SpamListsRepository,
    private val normalizer: PhoneNormalizer,
    private val regions: RegionProvider
) : ViewModel() {
    private val list: ListType = listTypeOfRoute(savedStateHandle.get<String>(LIST_ARG))
    private val error = MutableStateFlow<ListInputError?>(null)

    val state: StateFlow<SpamListState> = combine(lists.observe(list), error) { entries, err ->
        SpamListState(list, entries, err, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SpamListState(list))

    /**
     * Adds a number or a prefix. Returns true when it was accepted; otherwise the state carries the error. Adding what
     * is already there updates its label and note.
     */
    fun add(kind: EntryKind, rawValue: String, label: String, note: String): Boolean {
        val value = ListInput.validate(kind, rawValue, normalizer, regions.defaultRegion())
        if (value == null) {
            error.value = if (kind == EntryKind.NUMBER) ListInputError.INVALID_NUMBER else ListInputError.INVALID_PREFIX
            return false
        }
        error.value = null
        viewModelScope.launch {
            lists.add(list, kind, value, label.trim().ifEmpty { null }, note.trim().ifEmpty { null })
            // An entry in one list contradicts the same entry in the other: the newest choice wins.
            val other = if (list == ListType.OWN) ListType.WHITELIST else ListType.OWN
            lists.entries(other).filter { !it.deleted && it.kind == kind && it.value == value }.forEach { lists.remove(other, it.id) }
        }
        return true
    }

    fun remove(entry: ListEntry) {
        viewModelScope.launch { lists.remove(list, entry.id) }
    }

    fun clearError() {
        error.update { null }
    }

    companion object {
        const val LIST_ARG = "list"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
