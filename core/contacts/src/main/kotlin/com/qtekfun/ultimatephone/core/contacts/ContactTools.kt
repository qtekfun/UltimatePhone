package com.qtekfun.ultimatephone.core.contacts

import kotlinx.coroutines.flow.Flow

/** A contact group (label) of an account. [editable] is false for groups the account owns read-only. System groups are not listed. */
data class ContactGroup(val id: Long, val title: String, val account: ContactAccount, val memberCount: Int, val editable: Boolean)

/** One group as seen from one contact: whether it is a [member], and whether the contact can be added to it ([canChange]). */
data class ContactGroupState(val group: ContactGroup, val member: Boolean, val canChange: Boolean)

/** Groups (labels). A group belongs to an account; a contact joins it through its raw contact in that same account. */
interface ContactGroups {
    /** User-visible groups ordered by title; re-emits on change. */
    fun observeGroups(): Flow<List<ContactGroup>>

    suspend fun createGroup(title: String, account: ContactAccount): Boolean

    /** Only editable groups can be renamed. */
    suspend fun renameGroup(groupId: Long, title: String): Boolean

    /** Deletes the group, not its contacts. Only editable, user-created groups can be deleted. */
    suspend fun deleteGroup(groupId: Long): Boolean

    /** Lookup keys of the contacts in the group. */
    fun observeGroupMembers(groupId: Long): Flow<Set<String>>

    /** Every listed group with the membership of the contact; empty when the contact does not exist. */
    fun observeContactGroups(lookupKey: String): Flow<List<ContactGroupState>>

    /** Adds the contact to, or removes it from, the group; false when it cannot (no raw contact in the group's account, no permission). */
    suspend fun setGroupMembership(lookupKey: String, groupId: Long, member: Boolean): Boolean
}

/** Finding and merging duplicate contacts. */
interface ContactDuplicates {
    /** Every contact with its phone numbers and e-mail addresses, for [DuplicateDetector]. */
    suspend fun duplicateCandidates(): List<DuplicateContact>

    /** The full data of the given contacts, for [ContactMerger]; contacts that no longer exist are left out. */
    suspend fun mergeSources(lookupKeys: List<String>): List<MergeSource>

    /**
     * Merges without deleting anything: the raw contacts are aggregated with `AggregationExceptions.TYPE_KEEP_TOGETHER`, so the
     * merged contact shows the union of their data and each account keeps its own rows. The plan's nicknames and notes are
     * written to the primary raw contact. Returns the lookup key of the merged contact, or null on failure.
     */
    suspend fun mergeContacts(plan: MergePlan): String?
}

/** vCard export and import against the system contacts. */
interface ContactVCards {
    /**
     * Reads the contacts (all of them when [lookupKeys] is null) one at a time and hands each to [sink], so a large address
     * book with photos is never held in memory. Returns how many were read.
     */
    suspend fun readForExport(lookupKeys: List<String>?, sink: (VCardContact) -> Unit): Int

    /** Creates the contacts in [account]; returns how many were created (a failed batch counts none). */
    suspend fun importContacts(contacts: List<VCardContact>, account: ContactAccount): Int
}
