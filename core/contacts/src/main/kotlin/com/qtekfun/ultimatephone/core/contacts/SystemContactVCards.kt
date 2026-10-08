package com.qtekfun.ultimatephone.core.contacts

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.withContext

/** [ContactVCards] on top of ContactsContract. The vCard text itself is made and read by [VCardWriter] and [VCardParser]. */
internal class SystemContactVCards(private val access: ProviderAccess) : ContactVCards {
    private class Head(val id: Long, val displayName: String, val hasPhoto: Boolean)

    override suspend fun readForExport(lookupKeys: List<String>?, sink: (VCardContact) -> Unit): Int {
        if (!access.canRead()) return 0
        return withContext(access.ioDispatcher) {
            val ids = if (lookupKeys == null) allContactIds() else lookupKeys.mapNotNull { access.contactId(it) }.distinct()
            var count = 0
            for (batch in ids.chunked(EXPORT_BATCH)) {
                val heads = heads(batch)
                val data = HashMap<Long, ContactRows>()
                loadRows(batch, data)
                for (head in heads) {
                    sink(toVCard(head, data[head.id] ?: ContactRows()))
                    count++
                }
            }
            count
        }
    }

    private fun allContactIds(): List<Long> = access.query(Contacts.CONTENT_URI, arrayOf(Contacts._ID), null, null, "${Contacts.SORT_KEY_PRIMARY} ASC") { c ->
        buildList { while (c.moveToNext()) add(c.getLong(0)) }
    } ?: emptyList()

    private fun idList(ids: List<Long>) = ids.joinToString(",")

    private fun heads(ids: List<Long>): List<Head> {
        val projection = arrayOf(Contacts._ID, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_URI)
        return access.query(Contacts.CONTENT_URI, projection, "${Contacts._ID} IN (${idList(ids)})", null, "${Contacts.SORT_KEY_PRIMARY} ASC") { c ->
            buildList { while (c.moveToNext()) add(Head(c.getLong(0), c.getString(1).orEmpty(), !c.getString(2).isNullOrEmpty())) }
        } ?: emptyList()
    }

    /** What the data table holds for one contact, as far as vCards go. */
    private class ContactRows {
        var name = VCardName()
        val nicknames = ArrayList<String>()
        var organization = ""
        var title = ""
        val phones = ArrayList<LabeledValue>()
        val emails = ArrayList<LabeledValue>()
        var birthday: ContactDate? = null
        val notes = ArrayList<String>()
        val websites = ArrayList<String>()
        val addresses = ArrayList<VCardAddress>()
    }

    private fun loadRows(ids: List<Long>, into: MutableMap<Long, ContactRows>) {
        val projection = arrayOf(
            Data.CONTACT_ID,
            Data.MIMETYPE,
            Data.DATA1,
            Data.DATA2,
            Data.DATA3,
            Data.DATA4,
            Data.DATA5,
            Data.DATA6
        )
        access.query(Data.CONTENT_URI, projection, "${Data.CONTACT_ID} IN (${idList(ids)})", null, "${Data.RAW_CONTACT_ID} ASC, ${Data._ID} ASC") { c ->
            while (c.moveToNext()) {
                val rows = into.getOrPut(c.getLong(0)) { ContactRows() }
                val text = c.getString(2)
                if (text.isNullOrBlank() && c.getString(1) != StructuredName.CONTENT_ITEM_TYPE) continue
                addRow(rows, c.getString(1), text.orEmpty(), c)
            }
        }
    }

    private fun addRow(rows: ContactRows, mime: String?, text: String, c: Cursor) {
        when (mime) {
            StructuredName.CONTENT_ITEM_TYPE ->
                if (!rows.name.hasParts && rows.name.formatted.isEmpty()) {
                    rows.name = VCardName(
                        given = c.getString(3).orEmpty(),
                        family = c.getString(4).orEmpty(),
                        prefix = c.getString(5).orEmpty(),
                        middle = c.getString(6).orEmpty(),
                        suffix = c.getString(7).orEmpty(),
                        formatted = text
                    )
                }
            Nickname.CONTENT_ITEM_TYPE -> rows.nicknames += text
            Organization.CONTENT_ITEM_TYPE -> if (rows.organization.isEmpty()) {
                rows.organization = text
                rows.title = c.getString(5).orEmpty()
            }
            Phone.CONTENT_ITEM_TYPE -> rows.phones += LabeledValue(text, c.getInt(3), c.getString(4))
            Email.CONTENT_ITEM_TYPE -> rows.emails += LabeledValue(text, c.getInt(3), c.getString(4))
            Event.CONTENT_ITEM_TYPE -> if (c.getInt(3) == Event.TYPE_BIRTHDAY && rows.birthday == null) rows.birthday = ContactDate.parse(text)
            Note.CONTENT_ITEM_TYPE -> rows.notes += text
            Website.CONTENT_ITEM_TYPE -> rows.websites += text
            StructuredPostal.CONTENT_ITEM_TYPE -> rows.addresses += VCardAddress(text, c.getInt(3).takeIf { it in 1..3 } ?: VCardAddress.ADDRESS_OTHER)
        }
    }

    private fun toVCard(head: Head, rows: ContactRows): VCardContact {
        val name = rows.name
        return VCardContact(
            // The aggregated display name wins over a single raw contact's one.
            name = name.copy(formatted = head.displayName.ifBlank { name.formatted }),
            nicknames = rows.nicknames.distinctBy { it.lowercase() },
            organization = rows.organization,
            title = rows.title,
            phones = rows.phones.distinctBy { T9.digitsOf(it.value).ifEmpty { it.value } },
            emails = rows.emails.distinctBy { it.value.trim().lowercase() },
            birthday = rows.birthday,
            note = rows.notes.distinct().joinToString("\n"),
            photo = if (head.hasPhoto) readPhoto(head.id) else null,
            websites = rows.websites.distinct(),
            addresses = rows.addresses.distinctBy { it.formatted }
        )
    }

    /** The full-size photo when the provider has one. */
    private fun readPhoto(contactId: Long): VCardPhoto? = try {
        val uri: Uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
        Contacts.openContactPhotoInputStream(access.resolver, uri, true)?.use { stream ->
            val bytes = stream.readBounded(MAX_PHOTO_BYTES)
            if (bytes.isEmpty()) null else VCardPhoto(bytes)
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    override suspend fun importContacts(contacts: List<VCardContact>, account: ContactAccount): Int {
        if (!access.canWrite()) return 0
        return withContext(access.ioDispatcher) {
            var created = 0
            for (batch in contacts.chunked(IMPORT_BATCH)) {
                created += access.guarded("import") { insertBatch(batch, account) } ?: 0
            }
            created
        }
    }

    private fun insertBatch(batch: List<VCardContact>, account: ContactAccount): Int {
        val ops = ArrayList<ContentProviderOperation>()
        for (contact in batch) {
            val rawIndex = ops.size
            ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                .withValue(RawContacts.ACCOUNT_NAME, account.name)
                .build()
            for (row in VCardDataRows.of(contact)) {
                val insert = ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, rawIndex)
                    .withValue(Data.MIMETYPE, row.mimeType)
                row.values.forEach { (column, value) -> insert.withValue(column, value) }
                ops += insert.build()
            }
        }
        access.resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        return batch.size
    }

    private companion object {
        const val EXPORT_BATCH = 100
        const val IMPORT_BATCH = 20
        const val MAX_PHOTO_BYTES = 8 * 1024 * 1024
    }
}

private const val COPY_BUFFER = 16 * 1024

/** Reads at most [limit] bytes; a photo bigger than that is cut (and so undecodable) rather than filling memory. */
private fun InputStream.readBounded(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(COPY_BUFFER)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
