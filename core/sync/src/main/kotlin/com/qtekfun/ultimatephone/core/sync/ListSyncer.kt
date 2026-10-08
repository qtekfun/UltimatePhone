package com.qtekfun.ultimatephone.core.sync

import com.qtekfun.ultimatephone.core.spam.lists.EntryJsonl
import com.qtekfun.ultimatephone.core.spam.lists.ListEntry
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Syncs both lists with the user's WebDAV: download, merge with [ListMerge], apply locally, upload with `If-Match`.
 *
 * Changes made while offline need no queue of their own: they stay in the local lists, and the first sync that
 * reaches the server merges and uploads them. [isDirty] tells whether such changes are waiting.
 *
 * One instance must not run two syncs at once; the app serialises calls.
 */
class ListSyncer(
    private val dav: WebDav,
    private val local: LocalLists,
    private val state: SyncStateStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tombstoneRetentionMs: Long = ListMerge.DEFAULT_TOMBSTONE_RETENTION_MS,
    private val maxAttempts: Int = DEFAULT_ATTEMPTS,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val random: Random = Random.Default
) {
    /** True when a list differs from what the last successful sync left, so there is something to upload. */
    suspend fun isDirty(): Boolean = hasPendingChanges(local, state)

    /** Syncs every list. Stops at the first failure and reports it; the counters cover what was done until then. */
    suspend fun syncAll(): SyncResult {
        var total = SyncCounters()
        for (list in ListType.entries) {
            try {
                total += syncList(list)
            } catch (e: SyncException) {
                return SyncResult(total, e.error, e.message)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Detail is the class only: a message could quote a value from a list.
                return SyncResult(total, SyncError.UNKNOWN, e.javaClass.simpleName)
            }
        }
        return SyncResult(total)
    }

    private suspend fun syncList(list: ListType): SyncCounters {
        val path = "${SyncFiles.FOLDER}/${SyncFiles.fileOf(list)}"
        var counters = SyncCounters()
        repeat(maxAttempts) { attempt ->
            val outcome = attemptOnce(list, path)
            counters += outcome.counters
            if (outcome.done) return counters
            counters += SyncCounters(retries = 1)
            pause(backoff(attempt))
        }
        throw SyncException(SyncError.CONFLICT_EXHAUSTED, "The file kept changing on the server")
    }

    private class Attempt(val counters: SyncCounters, val done: Boolean)

    @Suppress("LongMethod") // One linear protocol: read, merge, apply, write.
    private suspend fun attemptOnce(list: ListType, path: String): Attempt {
        val file = SyncFiles.fileOf(list)
        val before = local.entries(list)
        val knownEtag = state.etag(file)
        val synced = state.syncedFingerprint(list)
        val clean = synced != null && ListMerge.fingerprint(before) == synced
        val remote = when (val got = dav.get(path, ifNoneMatch = if (clean) knownEtag else null)) {
            DavGet.NotModified -> return Attempt(SyncCounters(), done = true)
            DavGet.NotFound -> RemoteFile(emptyList(), 0, null, present = false)
            is DavGet.Content -> parseRemote(got)
        }
        val merged = ListMerge.merge(before, remote.entries, clock(), tombstoneRetentionMs)

        val beforeById = before.associateBy { it.id }
        val upserts = merged.filter { beforeById[it.id] != it }
        val mergedIds = merged.mapTo(HashSet()) { it.id }
        val drops = before.filter { it.deleted && it.id !in mergedIds }.map { it.id }
        if (upserts.isNotEmpty() || drops.isNotEmpty()) local.apply(list, upserts, drops)
        val pulled = upserts.size + drops.size

        val remoteById = remote.entries.associateBy { it.id }
        val changedRemote = merged.count { remoteById[it.id] != it }
        val upToDate = remote.present && ListMerge.canonical(merged) == ListMerge.canonical(remote.entries)
        val badLines = SyncCounters(badLines = remote.badLines)
        if (upToDate) {
            state.setEtag(file, remote.etag)
            state.setSyncedFingerprint(list, ListMerge.fingerprint(merged))
            return Attempt(SyncCounters(pulled = pulled) + badLines, done = true)
        }

        if (!remote.present) ensureFolder()
        val precondition = remote.etag?.let { Precondition.Etag(it) } ?: Precondition.MustNotExist
        return when (val put = dav.put(path, EntryJsonl.encodeAll(merged), precondition)) {
            DavPut.PreconditionFailed -> Attempt(SyncCounters(pulled = pulled) + badLines, done = false)
            is DavPut.Stored -> {
                val newEtag = put.etag ?: dav.stat(path)?.etag
                state.setEtag(file, newEtag)
                state.setSyncedFingerprint(list, ListMerge.fingerprint(merged))
                Attempt(SyncCounters(pulled = pulled, pushed = changedRemote) + badLines, done = true)
            }
        }
    }

    private class RemoteFile(val entries: List<ListEntry>, val badLines: Int, val etag: String?, val present: Boolean)

    private fun parseRemote(got: DavGet.Content): RemoteFile {
        // Conditional writes are the whole concurrency story; without an ETag they cannot work.
        val etag = got.etag ?: throw SyncException(SyncError.SERVER_TOO_OLD, "The server sent no ETag")
        val parsed = EntryJsonl.decodeAll(got.text)
        // A file with content but no usable line is not ours: leave it alone instead of overwriting it.
        if (parsed.entries.isEmpty() && parsed.badLines > 0) throw SyncException(SyncError.REMOTE_CORRUPT, "The remote file has no usable line")
        return RemoteFile(parsed.entries, parsed.badLines, etag, present = true)
    }

    private suspend fun ensureFolder() {
        val folder = dav.stat(SyncFiles.FOLDER)
        if (folder == null) {
            dav.mkcol(SyncFiles.FOLDER)
        } else if (!folder.isCollection) {
            throw SyncException(SyncError.UNKNOWN, "${SyncFiles.FOLDER} exists and is not a folder")
        }
    }

    private fun backoff(attempt: Int): Long = (BASE_BACKOFF_MS shl attempt.coerceAtMost(MAX_SHIFT)) + random.nextLong(BASE_BACKOFF_MS)

    companion object {
        const val DEFAULT_ATTEMPTS = 5
        private const val BASE_BACKOFF_MS = 200L
        private const val MAX_SHIFT = 4
    }
}

/** Checks the server, the user and the app password with one PROPFIND on the WebDAV root. */
suspend fun testConnection(dav: WebDav): SyncResult = try {
    dav.stat("")
    SyncResult()
} catch (e: SyncException) {
    SyncResult(error = e.error, detail = e.message)
}

/** True when a list differs from what the last successful sync left. Needs no network and no credentials. */
suspend fun hasPendingChanges(local: LocalLists, state: SyncStateStore): Boolean = ListType.entries.any { list ->
    val entries = local.entries(list)
    val synced = state.syncedFingerprint(list)
    if (synced == null) entries.isNotEmpty() else ListMerge.fingerprint(entries) != synced
}
