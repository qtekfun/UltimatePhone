package com.qtekfun.ultimatephone.core.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
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
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.RawContacts
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
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

/** A raw contact (one account's copy of a contact) of an aggregated contact. */
internal class RawContactRef(val id: Long, val account: ContactAccount)

/**
 * Shared plumbing of the ContactsContract-backed helpers (groups, duplicates and merging, vCard transfer): permission
 * checks, guarded queries and writes, change flows. Failures are logged by class only, because messages can carry data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ProviderAccess(context: Context, val ioDispatcher: CoroutineDispatcher) {
    val appContext: Context = context.applicationContext
    val resolver: ContentResolver = appContext.contentResolver

    private fun hasPermission(permission: String) = ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    fun canRead() = hasPermission(Manifest.permission.READ_CONTACTS)

    fun canWrite() = canRead() && hasPermission(Manifest.permission.WRITE_CONTACTS)

    /** Runs [load] now and after each change below [uri], waiting a moment after a change so a burst of sync writes loads once. */
    fun <T> reloading(uri: Uri, load: () -> T): Flow<T> = flow {
        val first = AtomicBoolean(true)
        emitAll(
            changes(uri).mapLatest {
                if (!first.getAndSet(false)) delay(RELOAD_DELAY_MS)
                load()
            }
        )
    }.flowOn(ioDispatcher)

    private fun changes(uri: Uri): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        try {
            resolver.registerContentObserver(uri, true, observer)
        } catch (_: SecurityException) {
            // Without the permission there is nothing to observe; the single initial emission loads an empty result.
        }
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    /** Queries and hands the cursor to [read]; null when the provider is unavailable or access is denied. */
    fun <T> query(uri: Uri, projection: Array<String>, selection: String?, args: Array<String>?, order: String?, read: (Cursor) -> T): T? = try {
        resolver.query(uri, projection, selection, args, order)?.use(read)
    } catch (e: SecurityException) {
        failed("query", e)
    } catch (e: IllegalArgumentException) {
        failed("query", e)
    }

    /** Runs a provider write; the usual failures are logged by class only and become null. */
    fun <T> guarded(what: String, block: () -> T?): T? = try {
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

    fun contactId(lookupKey: String): Long? = try {
        Contacts.lookupContact(resolver, Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, Uri.encode(lookupKey)))?.let(ContentUris::parseId)
    } catch (e: SecurityException) {
        failed("lookup", e)
    }

    fun lookupKeyOfRaw(rawContactId: Long): String? {
        val contactId = query(RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID), "${RawContacts._ID}=?", arrayOf(rawContactId.toString()), null) { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        } ?: return null
        return query(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), arrayOf(Contacts.LOOKUP_KEY), null, null, null) { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun rawContacts(contactId: Long): List<RawContactRef> {
        val projection = arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE)
        return query(
            RawContacts.CONTENT_URI,
            projection,
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0",
            arrayOf(contactId.toString()),
            "${RawContacts._ID} ASC"
        ) { c ->
            buildList { while (c.moveToNext()) add(RawContactRef(c.getLong(0), accountOf(c.getString(1), c.getString(2)))) }
        } ?: emptyList()
    }

    fun accountOf(name: String?, type: String?) = if (name == null && type == null) ContactAccount.LOCAL else ContactAccount(name, type)

    /** Everything under the contacts authority: raw contacts, data and groups all notify it. */
    val everything: Uri get() = ContactsContract.AUTHORITY_URI

    companion object {
        private const val TAG = "ContactsTools"
        private const val RELOAD_DELAY_MS = 300L
    }
}
