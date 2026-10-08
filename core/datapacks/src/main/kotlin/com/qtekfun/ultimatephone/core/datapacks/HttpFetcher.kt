package com.qtekfun.ultimatephone.core.datapacks

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The result of a conditional GET. */
sealed interface Fetched {
    data class Body(val bytes: ByteArray, val etag: String?) : Fetched

    data object NotModified : Fetched
}

/** Network access, behind an interface so the rest can be tested without a network. Only HTTPS is ever used. */
interface HttpFetcher {
    /** Small documents (the manifest and its signature). Sends If-None-Match when [etag] is given. */
    @Throws(IOException::class)
    fun fetch(url: String, etag: String? = null): Fetched

    /** Large files go straight to disk. [onProgress] receives bytes written so far. */
    @Throws(IOException::class)
    fun download(url: String, target: File, onProgress: (Long) -> Unit = {})
}

class HttpUrlFetcher(private val userAgent: String = "UltimatePhone") : HttpFetcher {
    override fun fetch(url: String, etag: String?): Fetched {
        val connection = open(url)
        try {
            if (etag != null) connection.setRequestProperty("If-None-Match", etag)
            return when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> Fetched.NotModified
                HttpURLConnection.HTTP_OK -> {
                    Fetched.Body(readLimited(connection), connection.getHeaderField("ETag"))
                }
                else -> throw IOException("HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    override fun download(url: String, target: File, onProgress: (Long) -> Unit) {
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            var written = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(connection: HttpURLConnection): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_DOCUMENT_BYTES) throw IOException("Document too large")
            }
        }
        return out.toByteArray()
    }

    private fun open(url: String): HttpURLConnection {
        if (!url.startsWith("https://")) throw IOException("Only https is allowed")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent)
        return connection
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val BUFFER = 64 * 1024
        const val MAX_DOCUMENT_BYTES = 4 * 1024 * 1024
    }
}
