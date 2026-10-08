package com.qtekfun.ultimatephone.core.contacts

import kotlinx.coroutines.flow.Flow

/** The contacts repository plus detail, writing and account listing. Writes need WRITE_CONTACTS; failures return null/false. */
interface ContactsManager :
    ContactsRepository,
    ContactGroups,
    ContactDuplicates,
    ContactVCards {
    suspend fun getContact(lookupKey: String): ContactDetail?

    /** Emits the contact now and again whenever it changes; emits null when it no longer exists. */
    fun observeContact(lookupKey: String): Flow<ContactDetail?>

    /** The local "device" account first, then every account that holds contacts or has contact settings (DAVx5 and other sync accounts). */
    suspend fun accounts(): List<ContactAccount>

    /** Creates the contact described by an already validated [draft] in its account; returns its lookup key, or null on failure. */
    suspend fun createContact(draft: ContactDraft): String?

    /** Replaces the editable data of the contact's primary raw contact; the account of the draft is ignored. */
    suspend fun updateContact(draft: ContactDraft): Boolean

    suspend fun deleteContact(lookupKey: String): Boolean

    suspend fun setStarred(lookupKey: String, starred: Boolean): Boolean
}
