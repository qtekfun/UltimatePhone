package com.qtekfun.ultimatephone.core.calllog

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.qtekfun.ultimatephone.core.telecom.SimAccount
import com.qtekfun.ultimatephone.core.telecom.SimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [CallLogRepository] on top of `CallLog.Calls`. Reads the newest [maxEntries] rows and filters in memory with
 * [matches], so the filter semantics live in one tested place.
 */
class ContentResolverCallLogRepository(
    private val context: Context,
    private val simRepository: SimRepository,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) : CallLogRepository {
    private val resolver: ContentResolver get() = context.contentResolver

    override fun observe(filter: CallLogFilter): Flow<List<CallLogEntry>> = changes()
        .map { if (canRead()) query().filtered(filter) else emptyList() }
        .flowOn(Dispatchers.IO)

    /** Emits once on collection and again on every change of the call log. */
    private fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        try {
            resolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        } catch (_: SecurityException) {
            // Without the permission there is nothing to observe; the single initial emission is empty.
        }
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    override suspend fun delete(ids: Collection<Long>): Int = withContext(Dispatchers.IO) {
        if (ids.isEmpty() || !canWrite()) return@withContext 0
        try {
            // Ids are numbers, so they can be written into the clause; chunked to stay under SQLite's variable limit.
            ids.distinct().chunked(DELETE_CHUNK).sumOf { chunk ->
                resolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID} IN (${chunk.joinToString(",")})", null)
            }
        } catch (_: SecurityException) {
            0
        }
    }

    override suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        if (!canWrite()) return@withContext 0
        try {
            resolver.delete(CallLog.Calls.CONTENT_URI, null, null)
        } catch (_: SecurityException) {
            0
        }
    }

    override suspend fun markAllRead() {
        withContext(Dispatchers.IO) {
            if (!canWrite()) return@withContext
            val values = ContentValues().apply {
                put(CallLog.Calls.NEW, 0)
                put(CallLog.Calls.IS_READ, 1)
            }
            val unseen = "${CallLog.Calls.NEW} = 1 OR (${CallLog.Calls.IS_READ} = 0 AND ${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE})"
            try {
                resolver.update(CallLog.Calls.CONTENT_URI, values, unseen, null)
            } catch (_: SecurityException) {
                // Not allowed to write: the badge simply stays.
            }
        }
    }

    private fun canRead() = granted(Manifest.permission.READ_CALL_LOG)

    private fun canWrite() = granted(Manifest.permission.WRITE_CALL_LOG)

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun query(): List<CallLogEntry> {
        val args = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, maxEntries)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        }
        val cursor = try {
            resolver.query(CallLog.Calls.CONTENT_URI, PROJECTION, args, null)
        } catch (_: SecurityException) {
            null
        } ?: return emptyList()
        return cursor.use { readAll(it) }
    }

    private fun readAll(cursor: Cursor): List<CallLogEntry> {
        val id = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
        val number = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val type = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val date = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
        val duration = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
        val component = cursor.getColumnIndexOrThrow(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)
        val accountId = cursor.getColumnIndexOrThrow(CallLog.Calls.PHONE_ACCOUNT_ID)
        val name = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
        val new = cursor.getColumnIndexOrThrow(CallLog.Calls.NEW)
        // SIMs are resolved once per distinct account, not once per row.
        val sims = HashMap<Pair<String?, String?>, SimAccount?>()
        val result = ArrayList<CallLogEntry>(cursor.count)
        while (cursor.moveToNext()) {
            val simComponent = cursor.getString(component)?.takeIf { it.isNotBlank() }
            val simId = cursor.getString(accountId)?.takeIf { it.isNotBlank() }
            result += CallLogEntry(
                id = cursor.getLong(id),
                number = cursor.getString(number).orEmpty(),
                type = CallType.fromCode(cursor.getInt(type)),
                dateMillis = cursor.getLong(date),
                durationSeconds = cursor.getLong(duration),
                simComponent = simComponent,
                simId = simId,
                sim = sims.getOrPut(simComponent to simId) { simRepository.find(simComponent, simId) },
                cachedName = cursor.getString(name)?.takeIf { it.isNotBlank() },
                isNew = cursor.getInt(new) == 1
            )
        }
        return result
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 1000
        const val DELETE_CHUNK = 500

        val PROJECTION = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            CallLog.Calls.PHONE_ACCOUNT_ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NEW
        )
    }
}
