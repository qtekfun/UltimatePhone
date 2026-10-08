package com.qtekfun.ultimatephone.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface SourceResponse {
    /** The server answered 304: what we have is current. */
    data object NotModified : SourceResponse

    /** The body was written to the target file. */
    data class Downloaded(val etag: String?, val lastModified: String?) : SourceResponse
}

/** The server answered with an error status. */
class SourceHttpException(val status: Int) : IOException("HTTP $status")

/** The body is bigger than the limit; the download was stopped. */
class SourceTooLargeException : IOException("Source too large")

/** Conditional download of a custom source, behind an interface so the pipeline can be tested without a network. */
interface SourceFetcher {
    /**
     * Downloads [url] into [target] unless the server says the copy identified by [etag] / [lastModified] is current.
     * @throws SourceHttpException for an HTTP error status, [SourceTooLargeException] past [maxBytes], other [IOException]s for network trouble.
     */
    @Throws(IOException::class)
    fun fetch(url: String, etag: String?, lastModified: String?, target: File, maxBytes: Long): SourceResponse
}

/** HTTPS only. A redirect to plain http is not followed by [HttpURLConnection], so it surfaces as an HTTP error. */
class HttpsSourceFetcher(private val userAgent: String = "UltimatePhone") : SourceFetcher {
    override fun fetch(url: String, etag: String?, lastModified: String?, target: File, maxBytes: Long): SourceResponse {
        if (!SourceUrls.isValid(url)) throw IOException("Only https is allowed")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent)
            if (etag != null) connection.setRequestProperty("If-None-Match", etag)
            if (lastModified != null) connection.setRequestProperty("If-Modified-Since", lastModified)
            return when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> SourceResponse.NotModified
                HttpURLConnection.HTTP_OK -> {
                    copyLimited(connection, target, maxBytes)
                    SourceResponse.Downloaded(connection.getHeaderField("ETag"), connection.getHeaderField("Last-Modified"))
                }
                else -> throw SourceHttpException(status)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun copyLimited(connection: HttpURLConnection, target: File, maxBytes: Long) {
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(BUFFER)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw SourceTooLargeException()
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val BUFFER = 64 * 1024
    }
}
