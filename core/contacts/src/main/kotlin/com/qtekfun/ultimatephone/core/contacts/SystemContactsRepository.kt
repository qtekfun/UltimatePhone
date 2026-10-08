package com.qtekfun.ultimatephone.core.contacts

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.OperationApplicationException
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.PhoneLookup
import android.provider.ContactsContract.RawContacts
import android.util.Log
import androidx.core.content.ContextCompat
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.phonenumber.e164OrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [ContactsManager] on top of ContactsContract. Reads need READ_CONTACTS (without it everything is empty/null), writes
 * need WRITE_CONTACTS. Phone numbers are never logged.
 *
 * @param regionProvider ISO region used to read numbers without a country code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemContactsRepository(
    context: Context,
    private val normalizer: PhoneNormalizer,
    private val regionProvider: () -> String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ContactsManager {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    private val indexLock = Mutex()

    @Volatile
    private var index: List<SuggestionEntry>? = null

    @Volatile
    private var generation = 0L

    init {
        val invalidator = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                generation++
                index = null
            }
        }
        resolver.registerContentObserver(Contacts.CONTENT_URI, true, invalidator)
    }

    private fun hasPermission(permission: String) = ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun canRead() = hasPermission(Manifest.permission.READ_CONTACTS)

    private fun canWrite() = canRead() && hasPermission(Manifest.permission.WRITE_CONTACTS)

    /** Emits once on collection and again on every change below [uri]. */
    private fun changes(uri: Uri): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(uri, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    /** Runs [load] now and after each change, waiting a moment after a change so a burst of sync writes loads once. */
    private fun <T> reloading(uri: Uri, load: () -> T): Flow<T> = flow {
        val first = AtomicBoolean(true)
        emitAll(
            changes(uri).mapLatest {
                if (!first.getAndSet(false)) delay(RELOAD_DELAY_MS)
                load()
            }
        )
    }.flowOn(ioDispatcher)

    // ---- ContactsRepository ----

    override fun observeContacts(): Flow<List<ContactSummary>> = reloading(Contacts.CONTENT_URI) { if (canRead()) queryContacts() else emptyList() }

    private fun queryContacts(): List<ContactSummary> {
        val projection = arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED, Contacts.PHOTO_THUMBNAIL_URI)
        return query(Contacts.CONTENT_URI, projection, null, null, "${Contacts.SORT_KEY_PRIMARY} ASC") { c ->
            val out = ArrayList<ContactSummary>(c.count)
            while (c.moveToNext()) {
                val key = c.getString(1) ?: continue
                out += ContactSummary(key, c.getLong(0), c.getString(2).orEmpty(), c.getInt(3) == 1, c.getString(4))
            }
            out
        } ?: emptyList()
    }

    override suspend fun lookupByNumber(number: String): ContactMatch? {
        if (number.isBlank() || !canRead()) return null
        return withContext(ioDispatcher) {
            lookupRaw(number) ?: normalizer.normalize(number, regionProvider()).e164OrNull()?.takeIf { it != number }?.let(::lookupRaw)
        }
    }

    private fun lookupRaw(number: String): ContactMatch? {
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(PhoneLookup.LOOKUP_KEY, PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_THUMBNAIL_URI)
        return query(uri, projection, null, null, null) { c ->
            if (c.moveToFirst()) c.getString(0)?.let { ContactMatch(it, c.getString(1).orEmpty(), c.getString(2)) } else null
        }
    }

    override suspend fun suggest(query: String, limit: Int): List<DialerSuggestion> {
        if (T9.digitsOf(query).isEmpty() || !canRead()) return emptyList()
        return SuggestionMatcher.suggest(currentIndex(), query, limit)
    }

    private suspend fun currentIndex(): List<SuggestionEntry> {
        index?.let { return it }
        return indexLock.withLock {
            index ?: withContext(ioDispatcher) {
                val startedAt = generation
                buildIndex().also { built -> if (generation == startedAt) index = built }
            }
        }
    }

    private fun buildIndex(): List<SuggestionEntry> {
        val projection = arrayOf(Phone.LOOKUP_KEY, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.PHOTO_THUMBNAIL_URI)
        return query(Phone.CONTENT_URI, projection, null, null, "${Phone.SORT_KEY_PRIMARY} ASC") { c ->
            val seen = HashSet<String>()
            val out = ArrayList<SuggestionEntry>(c.count)
            while (c.moveToNext()) {
                val key = c.getString(0) ?: continue
                val number = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                if (seen.add(key + '|' + T9.digitsOf(number))) out += SuggestionEntry(key, c.getString(1).orEmpty(), number, c.getString(3))
            }
            out
        } ?: emptyList()
    }

    // ---- Detail ----

    override suspend fun getContact(lookupKey: String): ContactDetail? = if (canRead()) withContext(ioDispatcher) { loadContact(lookupKey) } else null

    override fun observeContact(lookupKey: String): Flow<ContactDetail?> = reloading(Contacts.CONTENT_URI) { if (canRead()) loadContact(lookupKey) else null }

    private fun contactUri(lookupKey: String): Uri? = try {
        Contacts.lookupContact(resolver, Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, Uri.encode(lookupKey)))
    } catch (e: SecurityException) {
        Log.w(TAG, "lookup failed: ${e.javaClass.simpleName}")
        null
    }

    private class RawContact(val id: Long, val account: ContactAccount)

    private fun loadContact(lookupKey: String): ContactDetail? {
        val uri = contactUri(lookupKey) ?: return null
        val summary = arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED, Contacts.PHOTO_URI)
        val info = query(uri, summary, null, null, null) { c ->
            if (c.moveToFirst()) ContactHead(c.getLong(0), c.getString(1) ?: lookupKey, c.getString(2).orEmpty(), c.getInt(3) == 1, c.getString(4)) else null
        } ?: return null
        val raws = rawContacts(info.contactId)
        val primary = raws.firstOrNull()
        val builder = DetailBuilder(info, primary)
        val dataUri = Uri.withAppendedPath(uri, Contacts.Data.CONTENT_DIRECTORY)
        val projection = arrayOf(Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3)
        query(dataUri, projection, null, null, "${Data.RAW_CONTACT_ID} ASC, ${Data._ID} ASC") { c ->
            while (c.moveToNext()) builder.add(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getString(4))
        }
        return builder.build()
    }

    private class ContactHead(val contactId: Long, val lookupKey: String, val displayName: String, val starred: Boolean, val photoUri: String?)

    private inner class DetailBuilder(private val head: ContactHead, private val primary: RawContact?) {
        private var editableName = ""
        private var organization = ""
        private var notes = ""
        private var birthday: ContactDate? = null
        private val phones = ArrayList<StoredValue>()
        private val emails = ArrayList<StoredValue>()

        fun add(rawId: Long, mime: String?, data1: String?, type: Int, label: String?) {
            val text = data1?.takeIf { it.isNotBlank() } ?: return
            when (mime) {
                Phone.CONTENT_ITEM_TYPE -> phones += StoredValue(LabeledValue(text, type, label), Phone.getTypeLabel(appContext.resources, type, label).toString(), rawId)
                Email.CONTENT_ITEM_TYPE -> emails += StoredValue(LabeledValue(text, type, label), Email.getTypeLabel(appContext.resources, type, label).toString(), rawId)
                StructuredName.CONTENT_ITEM_TYPE -> if (rawId == primary?.id && editableName.isEmpty()) editableName = text
                Organization.CONTENT_ITEM_TYPE -> if (organization.isEmpty()) organization = text
                Note.CONTENT_ITEM_TYPE -> if (notes.isEmpty()) notes = text
                Event.CONTENT_ITEM_TYPE -> if (type == Event.TYPE_BIRTHDAY && birthday == null) birthday = ContactDate.parse(text)
            }
        }

        fun build() = ContactDetail(
            lookupKey = head.lookupKey,
            contactId = head.contactId,
            displayName = head.displayName,
            editableName = editableName,
            organization = organization,
            phones = phones,
            emails = emails,
            birthday = birthday,
            notes = notes,
            photoUri = head.photoUri,
            starred = head.starred,
            primaryRawContactId = primary?.id,
            account = primary?.account
        )
    }

    private fun rawContacts(contactId: Long): List<RawContact> {
        val projection = arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE)
        return query(RawContacts.CONTENT_URI, projection, "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0", arrayOf(contactId.toString()), "${RawContacts._ID} ASC") { c ->
            buildList { while (c.moveToNext()) add(RawContact(c.getLong(0), accountOf(c.getString(1), c.getString(2)))) }
        } ?: emptyList()
    }

    private fun accountOf(name: String?, type: String?) = if (name == null && type == null) ContactAccount.LOCAL else ContactAccount(name, type)

    // ---- Accounts ----

    override suspend fun accounts(): List<ContactAccount> = withContext(ioDispatcher) {
        val found = LinkedHashSet<ContactAccount>()
        if (canRead()) {
            val settings = arrayOf(ContactsContract.Settings.ACCOUNT_NAME, ContactsContract.Settings.ACCOUNT_TYPE)
            query(ContactsContract.Settings.CONTENT_URI, settings, null, null, null) { c ->
                while (c.moveToNext()) addAccount(found, c.getString(0), c.getString(1))
            }
            val raws = arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE)
            query(RawContacts.CONTENT_URI, raws, "${RawContacts.DELETED}=0", null, null) { c ->
                while (c.moveToNext()) addAccount(found, c.getString(0), c.getString(1))
            }
        }
        listOf(ContactAccount.LOCAL) + found.sortedWith(compareBy({ it.name }, { it.type }))
    }

    private fun addAccount(into: MutableSet<ContactAccount>, name: String?, type: String?) {
        if (!name.isNullOrBlank() && !type.isNullOrBlank()) into += ContactAccount(name, type)
    }

    // ---- Writing ----

    override suspend fun createContact(draft: ContactDraft): String? {
        if (!canWrite()) return null
        return withContext(ioDispatcher) {
            guarded("create") {
                val ops = ArrayList<ContentProviderOperation>()
                ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, draft.account.type)
                    .withValue(RawContacts.ACCOUNT_NAME, draft.account.name)
                    .build()
                ContactDataRows.of(draft).forEach { row ->
                    val insert = ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                        .withValue(Data.MIMETYPE, row.mimeType)
                    row.values.forEach { (column, value) -> insert.withValue(column, value) }
                    ops += insert.build()
                }
                val rawUri = resolver.applyBatch(ContactsContract.AUTHORITY, ops).firstOrNull()?.uri ?: return@guarded null
                lookupKeyOfRaw(ContentUris.parseId(rawUri))
            }
        }
    }

    private fun lookupKeyOfRaw(rawContactId: Long): String? {
        val contactId = query(RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID), "${RawContacts._ID}=?", arrayOf(rawContactId.toString()), null) { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        } ?: return null
        return query(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), arrayOf(Contacts.LOOKUP_KEY), null, null, null) { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }

    override suspend fun updateContact(draft: ContactDraft): Boolean {
        val key = draft.lookupKey
        if (key == null || !canWrite()) return false
        return withContext(ioDispatcher) {
            guarded("update") {
                val uri = contactUri(key) ?: return@guarded false
                val rawId = rawContacts(ContentUris.parseId(uri)).firstOrNull()?.id ?: return@guarded false
                val ops = ArrayList<ContentProviderOperation>()
                ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                    .withSelection(ContactDataRows.replaceSelection(), ContactDataRows.replaceSelectionArgs(rawId))
                    .build()
                ContactDataRows.of(draft).forEach { row ->
                    val insert = ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, rawId)
                        .withValue(Data.MIMETYPE, row.mimeType)
                    row.values.forEach { (column, value) -> insert.withValue(column, value) }
                    ops += insert.build()
                }
                resolver.applyBatch(ContactsContract.AUTHORITY, ops)
                true
            } == true
        }
    }

    override suspend fun deleteContact(lookupKey: String): Boolean {
        if (!canWrite()) return false
        return withContext(ioDispatcher) {
            guarded("delete") {
                val uri = contactUri(lookupKey) ?: return@guarded false
                resolver.delete(uri, null, null) > 0
            } == true
        }
    }

    override suspend fun setStarred(lookupKey: String, starred: Boolean): Boolean {
        if (!canWrite()) return false
        return withContext(ioDispatcher) {
            guarded("star") {
                val uri = contactUri(lookupKey) ?: return@guarded false
                val values = ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) }
                resolver.update(uri, values, null, null) > 0
            } == true
        }
    }

    // ---- Helpers ----

    /** Runs a provider write; the usual failures are logged by class only (messages can carry data) and become null. */
    private fun <T> guarded(what: String, block: () -> T?): T? = try {
        block()
    } catch (e: SecurityException) {
        failed(what, e)
    } catch (e: RemoteException) {
        failed(what, e)
    } catch (e: OperationApplicationException) {
        failed(what, e)
    } catch (e: IllegalArgumentException) {
        failed(what, e)
    }

    private fun failed(what: String, e: Exception): Nothing? {
        Log.w(TAG, "$what failed: ${e.javaClass.simpleName}")
        return null
    }

    /** Queries and hands the cursor to [read]; null when the provider is unavailable or access is denied. */
    private fun <T> query(uri: Uri, projection: Array<String>, selection: String?, args: Array<String>?, order: String?, read: (Cursor) -> T): T? = try {
        resolver.query(uri, projection, selection, args, order)?.use(read)
    } catch (e: SecurityException) {
        failed("query", e)
    } catch (e: IllegalArgumentException) {
        failed("query", e)
    }

    private companion object {
        const val TAG = "SystemContacts"
        const val RELOAD_DELAY_MS = 300L
    }
}
