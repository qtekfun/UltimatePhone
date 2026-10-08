package com.qtekfun.ultimatephone.core.sync

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Turns what the user typed into the WebDAV root of their Nextcloud, and refuses anything that is not HTTPS. */
object NextcloudEndpoint {
    private const val DAV_MARKER = "remote.php/dav"

    private fun reject(error: SyncError, detail: String): Nothing = throw SyncException(error, detail)

    /**
     * Accepts `cloud.example.com`, `https://cloud.example.com/nextcloud` and a pasted
     * `https://cloud.example.com/remote.php/dav/files/alice`. Throws [SyncException] with
     * [SyncError.INSECURE_URL] for `http://` and [SyncError.INVALID_URL] for anything unusable.
     */
    fun davRoot(serverUrl: String, username: String): HttpUrl {
        val typed = serverUrl.trim()
        if (typed.isEmpty() || username.isBlank()) reject(SyncError.INVALID_URL, "Empty server or user")
        if (typed.startsWith("http://", ignoreCase = true)) reject(SyncError.INSECURE_URL, "Only https is allowed")
        val withScheme = if (typed.contains("://")) typed else "https://$typed"
        val parsed = withScheme.toHttpUrlOrNull() ?: reject(SyncError.INVALID_URL, "Not a URL")
        if (parsed.scheme != "https") reject(SyncError.INSECURE_URL, "Only https is allowed")
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            reject(SyncError.INVALID_URL, "Put the user name in its own field, not in the address")
        }
        val segments = parsed.pathSegments.filter { it.isNotEmpty() }
        val builder = parsed.newBuilder().query(null).fragment(null).encodedPath("/")
        val davAt = segments.indexOf("dav").takeIf { it > 0 && segments[it - 1] == "remote.php" }
        if (davAt != null) {
            // A pasted WebDAV address: keep it up to `files/<user>`, or complete it.
            val base = segments.subList(0, davAt + 1)
            base.forEach { builder.addPathSegment(it) }
            builder.addPathSegment("files").addPathSegment(username.trim())
        } else {
            segments.forEach { builder.addPathSegment(it) }
            DAV_MARKER.split('/').forEach { builder.addPathSegment(it) }
            builder.addPathSegment("files").addPathSegment(username.trim())
        }
        return builder.build()
    }
}
