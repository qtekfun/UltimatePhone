package com.qtekfun.ultimatephone.core.contacts

import kotlinx.coroutines.flow.Flow

/** A contact as shown in lists. [lookupKey] is the stable system identifier; use it in routes and links. */
data class ContactSummary(val lookupKey: String, val contactId: Long, val displayName: String, val starred: Boolean, val photoThumbUri: String?)

/** The contact a phone number belongs to, if any. */
data class ContactMatch(val lookupKey: String, val displayName: String, val photoThumbUri: String?)

/** One row of the dialer's search-as-you-type: a contact and the number that matched. */
data class DialerSuggestion(val lookupKey: String, val displayName: String, val number: String, val photoThumbUri: String?)

/**
 * Read access to the system contacts. Implemented on top of ContactsContract. Everything here must keep working
 * (returning empty results) when READ_CONTACTS has not been granted.
 *
 * This is the stable contract other features depend on: add members if you need to, but do not change these.
 */
interface ContactsRepository {
    /** All contacts ordered by display name; re-emits when the contacts database changes. */
    fun observeContacts(): Flow<List<ContactSummary>>

    /** The contact owning [number] (any format), or null. Must be fast: it runs while a call is ringing. */
    suspend fun lookupByNumber(number: String): ContactMatch?

    /**
     * Contacts matching what is typed on the dialer: [query] is digits only, matched as a partial phone number and
     * as T9 against the start of each word of the name. Must answer in under 100 ms with 10 000 contacts.
     */
    suspend fun suggest(query: String, limit: Int = DEFAULT_SUGGESTIONS): List<DialerSuggestion>

    companion object {
        const val DEFAULT_SUGGESTIONS = 8
    }
}
