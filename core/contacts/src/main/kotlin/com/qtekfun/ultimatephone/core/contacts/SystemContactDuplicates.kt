package com.qtekfun.ultimatephone.core.contacts

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.provider.ContactsContract
import android.provider.ContactsContract.AggregationExceptions
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import kotlinx.coroutines.withContext

/**
 * [ContactDuplicates] on top of ContactsContract.
 *
 * Merge strategy (documented choice): **aggregate, never delete**. Every raw contact of the chosen contacts is tied to the
 * primary one with `AggregationExceptions.TYPE_KEEP_TOGETHER`. Android then shows one contact whose data is the union of all
 * raw contacts (identical phones/e-mails are collapsed by the platform's contact screens, and this app's detail screen),
 * the photo is the best one by the platform's own rule, and the name comes from the primary raw contact. Nothing is removed,
 * so each account keeps its rows and sync keeps working; the user can split the contact again in any contacts app. On top of
 * that the primary raw contact receives only what aggregation would not show well: the other names as nicknames (so the
 * person is still found by them), and the notes of all contacts joined (the primary's own note text is part of it).
 */
internal class SystemContactDuplicates(private val access: ProviderAccess) : ContactDuplicates {
    override suspend fun duplicateCandidates(): List<DuplicateContact> {
        if (!access.canRead()) return emptyList()
        return withContext(access.ioDispatcher) {
            val names = access.query(Contacts.CONTENT_URI, arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY), null, null, null) { c ->
                val map = LinkedHashMap<Long, Triple<String, String, MutableList<String>>>(c.count)
                while (c.moveToNext()) {
                    val key = c.getString(1) ?: continue
                    map[c.getLong(0)] = Triple(key, c.getString(2).orEmpty(), ArrayList())
                }
                map
            } ?: return@withContext emptyList()
            val phones = HashMap<Long, MutableList<String>>()
            val emails = HashMap<Long, MutableList<String>>()
            collect(Phone.CONTENT_URI, Phone.CONTACT_ID, Phone.NUMBER, phones)
            collect(Email.CONTENT_URI, Email.CONTACT_ID, Email.ADDRESS, emails)
            names.map { (id, info) ->
                DuplicateContact(id, info.first, info.second, phones[id].orEmpty(), emails[id].orEmpty())
            }
        }
    }

    private fun collect(uri: android.net.Uri, idColumn: String, valueColumn: String, into: MutableMap<Long, MutableList<String>>) {
        access.query(uri, arrayOf(idColumn, valueColumn), null, null, null) { c ->
            while (c.moveToNext()) {
                val value = c.getString(1)
                if (!value.isNullOrBlank()) into.getOrPut(c.getLong(0)) { ArrayList(2) } += value
            }
        }
    }

    override suspend fun mergeSources(lookupKeys: List<String>): List<MergeSource> {
        if (!access.canRead()) return emptyList()
        return withContext(access.ioDispatcher) { lookupKeys.mapNotNull(::loadSource) }
    }

    private class Head(val lookupKey: String, val displayName: String, val starred: Boolean, val photoUri: String?)

    private fun loadSource(lookupKey: String): MergeSource? {
        val contactId = access.contactId(lookupKey) ?: return null
        val head = access.query(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
            arrayOf(Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED, Contacts.PHOTO_URI),
            null,
            null,
            null
        ) { c -> if (c.moveToFirst()) Head(c.getString(0) ?: lookupKey, c.getString(1).orEmpty(), c.getInt(2) == 1, c.getString(3)) else null } ?: return null
        val raws = access.rawContacts(contactId)
        val names = ArrayList<String>()
        val notes = ArrayList<String>()
        val phones = ArrayList<LabeledValue>()
        val emails = ArrayList<LabeledValue>()
        var organization = ""
        var birthday: ContactDate? = null
        val projection = arrayOf(Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3)
        access.query(Data.CONTENT_URI, projection, "${Data.CONTACT_ID}=?", arrayOf(contactId.toString()), "${Data.RAW_CONTACT_ID} ASC, ${Data._ID} ASC") { c ->
            while (c.moveToNext()) {
                val text = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                when (c.getString(0)) {
                    StructuredName.CONTENT_ITEM_TYPE -> names += text
                    Phone.CONTENT_ITEM_TYPE -> phones += LabeledValue(text, c.getInt(2), c.getString(3))
                    Email.CONTENT_ITEM_TYPE -> emails += LabeledValue(text, c.getInt(2), c.getString(3))
                    Organization.CONTENT_ITEM_TYPE -> if (organization.isEmpty()) organization = text
                    Note.CONTENT_ITEM_TYPE -> notes += text
                    Event.CONTENT_ITEM_TYPE -> if (c.getInt(2) == Event.TYPE_BIRTHDAY && birthday == null) birthday = ContactDate.parse(text)
                }
            }
        }
        return MergeSource(
            lookupKey = head.lookupKey,
            contactId = contactId,
            displayName = head.displayName,
            names = names,
            organization = organization,
            phones = phones,
            emails = emails,
            birthday = birthday,
            notes = notes,
            starred = head.starred,
            photoUri = head.photoUri,
            rawContactIds = raws.map { it.id },
            account = raws.firstOrNull()?.account
        )
    }

    override suspend fun mergeContacts(plan: MergePlan): String? {
        val primary = plan.primaryRawContactId
        if (primary == null || plan.rawContactIds.size < 2 || !access.canWrite()) return null
        return withContext(access.ioDispatcher) {
            access.guarded("merge") {
                val ops = ArrayList<ContentProviderOperation>()
                for (other in plan.rawContactIds.filter { it != primary }) {
                    ops += ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI)
                        .withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER)
                        .withValue(AggregationExceptions.RAW_CONTACT_ID1, primary)
                        .withValue(AggregationExceptions.RAW_CONTACT_ID2, other)
                        .build()
                }
                ops += nicknameOps(primary, plan.nicknamesToAdd)
                ops += noteOps(primary, plan.mergedNotes)
                access.resolver.applyBatch(ContactsContract.AUTHORITY, ops)
                val mergedKey = access.lookupKeyOfRaw(primary)
                if (mergedKey != null && plan.starred) star(mergedKey)
                mergedKey
            }
        }
    }

    private fun star(lookupKey: String) {
        val id = access.contactId(lookupKey) ?: return
        val values = ContentValues().apply { put(Contacts.STARRED, 1) }
        access.resolver.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), values, null, null)
    }

    private fun rowsOf(rawContactId: Long, mimeType: String): List<Pair<Long, String>> = access.query(
        Data.CONTENT_URI,
        arrayOf(Data._ID, Data.DATA1),
        "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?",
        arrayOf(rawContactId.toString(), mimeType),
        "${Data._ID} ASC"
    ) { c -> buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1).orEmpty()) } } ?: emptyList()

    private fun nicknameOps(primary: Long, nicknames: List<String>): List<ContentProviderOperation> {
        if (nicknames.isEmpty()) return emptyList()
        val existing = rowsOf(primary, Nickname.CONTENT_ITEM_TYPE).map { it.second.trim().lowercase() }.toHashSet()
        return nicknames.filter { existing.add(it.trim().lowercase()) }.map {
            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValue(Data.RAW_CONTACT_ID, primary)
                .withValue(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE)
                .withValue(Nickname.NAME, it)
                .withValue(Nickname.TYPE, Nickname.TYPE_DEFAULT)
                .build()
        }
    }

    private fun noteOps(primary: Long, merged: String): List<ContentProviderOperation> {
        if (merged.isBlank()) return emptyList()
        val existing = rowsOf(primary, Note.CONTENT_ITEM_TYPE)
        val first = existing.firstOrNull()
        return when {
            first == null -> listOf(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, primary)
                    .withValue(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE)
                    .withValue(Note.NOTE, merged)
                    .build()
            )
            first.second == merged -> emptyList()
            // The merged text starts with the primary's own note, so updating it in place loses nothing.
            else -> listOf(
                ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Data.CONTENT_URI, first.first)).withValue(Note.NOTE, merged).build()
            )
        }
    }
}
