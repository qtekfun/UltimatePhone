package com.qtekfun.ultimatephone.core.sync

/** The few WebDAV operations sync needs. Paths are relative to the user's WebDAV root, for example `UltimatePhone/spam-list.jsonl`. */
interface WebDav {
    /** PROPFIND at depth 0: null when the resource does not exist. */
    suspend fun stat(path: String): DavStat?

    /** MKCOL. A folder that already exists is not an error. */
    suspend fun mkcol(path: String)

    /** GET, conditional when [ifNoneMatch] is set. */
    suspend fun get(path: String, ifNoneMatch: String? = null): DavGet

    /** PUT of UTF-8 text under [precondition]. */
    suspend fun put(path: String, text: String, precondition: Precondition): DavPut
}

data class DavStat(val isCollection: Boolean, val etag: String?)

sealed interface DavGet {
    data class Content(val text: String, val etag: String?) : DavGet

    data object NotModified : DavGet

    data object NotFound : DavGet
}

sealed interface DavPut {
    /** [etag] is null when the server did not return one; the caller then asks with a PROPFIND. */
    data class Stored(val etag: String?) : DavPut

    /** HTTP 412: somebody else changed (or created, or deleted) the file first. */
    data object PreconditionFailed : DavPut
}

/** Optimistic concurrency for a PUT. */
sealed interface Precondition {
    /** `If-None-Match: *`: create only, fail if the file exists. */
    data object MustNotExist : Precondition

    /** `If-Match`: replace only the version we merged. */
    data class Etag(val etag: String) : Precondition
}
