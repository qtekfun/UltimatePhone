package com.qtekfun.ultimatephone.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.contacts.ContactsManager
import com.qtekfun.ultimatephone.core.contacts.T9
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** What the list screen shows; [Loading] until the first read of the contacts finishes. */
sealed interface ContactsListState {
    data object Loading : ContactsListState

    data class Ready(val content: ContactListContent, val totalContacts: Int) : ContactsListState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsListViewModel @Inject constructor(private val contacts: ContactsManager) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val numberMatches = _query.mapLatest { text ->
        val digits = T9.digitsOf(text)
        // Only look numbers up for what clearly is a number, not for names that happen to contain a digit.
        if (digits.length >= MIN_NUMBER_DIGITS && text.all { it.isDigit() || it in NUMBER_SEPARATORS }) {
            contacts.suggest(digits, MAX_NUMBER_MATCHES).map { it.lookupKey }.toSet()
        } else {
            emptySet()
        }
    }

    val state: StateFlow<ContactsListState> = combine(contacts.observeContacts(), _query, numberMatches) { all, text, matches ->
        ContactsListState.Ready(ContactListBuilder.build(all, text, matches), all.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ContactsListState.Loading)

    fun onQueryChange(text: String) {
        _query.value = text
    }

    private companion object {
        const val MIN_NUMBER_DIGITS = 3
        const val MAX_NUMBER_MATCHES = 500
        const val STOP_TIMEOUT_MS = 5_000L
        const val NUMBER_SEPARATORS = "+-() .#*"
    }
}
