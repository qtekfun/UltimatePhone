package com.qtekfun.ultimatephone.core.sync

import com.qtekfun.ultimatephone.core.spam.lists.ListEntry
import com.qtekfun.ultimatephone.core.spam.lists.ListType

/** Why a sync or a connection test failed. Never carries a credential or a phone number. */
enum class SyncError {
    /** No server, user or password saved. */
    NOT_CONFIGURED,

    /** The server address is not a usable URL. */
    INVALID_URL,

    /** The address is plain `http`; credentials are only ever sent over HTTPS. */
    INSECURE_URL,

    /** 401 or 403: wrong user or app password, or the app password was revoked. */
    AUTH,

    /** Unreachable, timed out, or a server error. Worth retrying later. */
    NETWORK,

    /** The TLS certificate was rejected. */
    TLS,

    /** The file kept changing on the server and the retries ran out. */
    CONFLICT_EXHAUSTED,

    /** The server does not offer what sync needs (WebDAV, ETags, conditional requests). */
    SERVER_TOO_OLD,

    /** The remote file is not a list file; it is left untouched. */
    REMOTE_CORRUPT,

    /** Secure storage for the password is unavailable or lost the key. */
    CREDENTIALS_UNAVAILABLE,

    UNKNOWN;

    /** True when trying again later may succeed without the user doing anything. */
    val isRetryable: Boolean get() = this == NETWORK || this == CONFLICT_EXHAUSTED
}

/** Failure carried up from the WebDAV layer. [detail] is for diagnostics and must not contain secrets. */
class SyncException(val error: SyncError, detail: String? = null, cause: Throwable? = null) : Exception(detail ?: error.name, cause)

/** What one sync changed. Counts are entries, never values. */
data class SyncCounters(
    /** Entries written to this device (new, changed or removed by the remote side). */
    val pulled: Int = 0,
    /** Entries that differed in the remote file and were uploaded. */
    val pushed: Int = 0,
    /** Lines in remote files that were not usable entries and were skipped. */
    val badLines: Int = 0,
    /** Times an upload lost a race (HTTP 412) and the merge was redone. */
    val retries: Int = 0
) {
    operator fun plus(other: SyncCounters) = SyncCounters(pulled + other.pulled, pushed + other.pushed, badLines + other.badLines, retries + other.retries)
}

data class SyncResult(val counters: SyncCounters = SyncCounters(), val error: SyncError? = null, val detail: String? = null) {
    val isSuccess: Boolean get() = error == null
}

/** The app password never reaches `toString`, so it cannot end up in a log line by accident. */
data class Credentials(val serverUrl: String, val username: String, val password: String) {
    override fun toString(): String = "Credentials(serverUrl=$serverUrl, username=<hidden>, password=<hidden>)"
}

/** The local side of a sync: the two lists as stored on this device. Kept free of Room so the engine runs on the JVM. */
interface LocalLists {
    /** Every entry including tombstones. */
    suspend fun entries(list: ListType): List<ListEntry>

    /**
     * Writes [upserts], and physically removes [deleteTombstones] (expired tombstones). An id whose stored row has
     * changed since the sync read it and now beats the given entry must be left alone: the next sync uploads it.
     */
    suspend fun apply(list: ListType, upserts: List<ListEntry>, deleteTombstones: List<String>)
}

/** What the engine remembers between syncs. Values are opaque to the store. */
interface SyncStateStore {
    suspend fun etag(file: String): String?

    suspend fun setEtag(file: String, etag: String?)

    /** Fingerprint of the local list right after the last successful sync of it. */
    suspend fun syncedFingerprint(list: ListType): String?

    suspend fun setSyncedFingerprint(list: ListType, fingerprint: String)
}

/** File names inside the `UltimatePhone/` folder. */
object SyncFiles {
    const val FOLDER = "UltimatePhone"

    fun fileOf(list: ListType): String = when (list) {
        ListType.OWN -> "spam-list.jsonl"
        ListType.WHITELIST -> "whitelist.jsonl"
    }
}
