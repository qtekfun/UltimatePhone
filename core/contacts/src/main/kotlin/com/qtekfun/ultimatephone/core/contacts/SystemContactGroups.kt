package com.qtekfun.ultimatephone.core.contacts

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** [ContactGroups] on top of ContactsContract.Groups and GroupMembership data rows. */
internal class SystemContactGroups(private val access: ProviderAccess) : ContactGroups {
    override fun observeGroups(): Flow<List<ContactGroup>> = access.reloading(access.everything) { if (access.canRead()) queryGroups() else emptyList() }

    /** Groups the user can see: not deleted, with no system id (that hides "My Contacts", "Starred" and similar), with member counts. */
    private fun queryGroups(): List<ContactGroup> {
        val projection = arrayOf(
            Groups._ID,
            Groups.TITLE,
            Groups.ACCOUNT_NAME,
            Groups.ACCOUNT_TYPE,
            Groups.SYSTEM_ID,
            Groups.GROUP_IS_READ_ONLY,
            Groups.SUMMARY_COUNT,
            Groups.FAVORITES
        )
        val groups = access.query(Groups.CONTENT_SUMMARY_URI, projection, "${Groups.DELETED}=0", null, null) { c ->
            buildList {
                while (c.moveToNext()) {
                    val title = c.getString(1)?.trim().orEmpty()
                    if (title.isEmpty() || !c.getString(4).isNullOrEmpty() || c.getInt(7) == 1) continue
                    add(ContactGroup(c.getLong(0), title, access.accountOf(c.getString(2), c.getString(3)), c.getInt(6), editable = c.getInt(5) == 0))
                }
            }
        } ?: emptyList()
        return groups.sortedWith(compareBy({ it.title.lowercase() }, { it.id }))
    }

    private fun findGroup(id: Long): ContactGroup? = queryGroups().firstOrNull { it.id == id }

    override suspend fun createGroup(title: String, account: ContactAccount): Boolean {
        val clean = title.trim()
        if (clean.isEmpty() || !access.canWrite()) return false
        return withContext(access.ioDispatcher) {
            access.guarded("create group") {
                val values = ContentValues().apply {
                    put(Groups.TITLE, clean)
                    put(Groups.ACCOUNT_NAME, account.name)
                    put(Groups.ACCOUNT_TYPE, account.type)
                    put(Groups.GROUP_VISIBLE, 1)
                }
                access.resolver.insert(Groups.CONTENT_URI, values) != null
            } == true
        }
    }

    override suspend fun renameGroup(groupId: Long, title: String): Boolean {
        val clean = title.trim()
        if (clean.isEmpty() || !access.canWrite()) return false
        return withContext(access.ioDispatcher) {
            access.guarded("rename group") {
                if (findGroup(groupId)?.editable != true) return@guarded false
                val values = ContentValues().apply { put(Groups.TITLE, clean) }
                access.resolver.update(ContentUris.withAppendedId(Groups.CONTENT_URI, groupId), values, null, null) > 0
            } == true
        }
    }

    override suspend fun deleteGroup(groupId: Long): Boolean {
        if (!access.canWrite()) return false
        return withContext(access.ioDispatcher) {
            access.guarded("delete group") {
                // Only groups that are listed (user created) and writable.
                if (findGroup(groupId)?.editable != true) return@guarded false
                access.resolver.delete(ContentUris.withAppendedId(Groups.CONTENT_URI, groupId), null, null) > 0
            } == true
        }
    }

    override fun observeGroupMembers(groupId: Long): Flow<Set<String>> = access.reloading(access.everything) {
        if (!access.canRead()) {
            emptySet()
        } else {
            access.query(
                Data.CONTENT_URI,
                arrayOf(Data.LOOKUP_KEY),
                "${Data.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID}=?",
                arrayOf(GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()),
                null
            ) { c -> buildSet { while (c.moveToNext()) c.getString(0)?.let(::add) } } ?: emptySet()
        }
    }

    override fun observeContactGroups(lookupKey: String): Flow<List<ContactGroupState>> = access.reloading(access.everything) {
        if (access.canRead()) loadContactGroups(lookupKey) else emptyList()
    }

    private fun loadContactGroups(lookupKey: String): List<ContactGroupState> {
        val contactId = access.contactId(lookupKey) ?: return emptyList()
        val accounts = access.rawContacts(contactId).map { it.account }.toSet()
        val memberOf = memberships(contactId)
        return queryGroups().map { ContactGroupState(it, member = it.id in memberOf, canChange = it.account in accounts) }
    }

    private fun memberships(contactId: Long): Set<Long> = access.query(
        Data.CONTENT_URI,
        arrayOf(GroupMembership.GROUP_ROW_ID),
        "${Data.CONTACT_ID}=? AND ${Data.MIMETYPE}=?",
        arrayOf(contactId.toString(), GroupMembership.CONTENT_ITEM_TYPE),
        null
    ) { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } } ?: emptySet()

    override suspend fun setGroupMembership(lookupKey: String, groupId: Long, member: Boolean): Boolean {
        if (!access.canWrite()) return false
        return withContext(access.ioDispatcher) {
            access.guarded("group membership") {
                val contactId = access.contactId(lookupKey) ?: return@guarded false
                val group = findGroup(groupId) ?: return@guarded false
                // The membership row lives in the raw contact of the group's own account.
                val raws = access.rawContacts(contactId).filter { it.account == group.account }
                if (raws.isEmpty()) return@guarded false
                if (member) addMembership(raws.first().id, groupId, contactId) else removeMembership(raws.map { it.id }, groupId)
                true
            } == true
        }
    }

    private fun addMembership(rawContactId: Long, groupId: Long, contactId: Long) {
        if (groupId in memberships(contactId)) return
        val insert = ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValue(Data.RAW_CONTACT_ID, rawContactId)
            .withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
            .withValue(GroupMembership.GROUP_ROW_ID, groupId)
            .build()
        access.resolver.applyBatch(ContactsContract.AUTHORITY, arrayListOf(insert))
    }

    private fun removeMembership(rawContactIds: List<Long>, groupId: Long) {
        val marks = rawContactIds.joinToString(",") { "?" }
        val args = listOf(GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()) + rawContactIds.map { it.toString() }
        access.resolver.delete(
            Data.CONTENT_URI,
            "${Data.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID}=? AND ${Data.RAW_CONTACT_ID} IN ($marks)",
            args.toTypedArray()
        )
    }
}
