package com.qtekfun.ultimatephone.core.sync

import java.io.IOException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.Credentials as BasicCredentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * WebDAV over OkHttp with app-password Basic authentication.
 *
 * - HTTPS only: a root or a redirect that is not `https` is refused before any credential is sent.
 * - Redirects are followed here, not by OkHttp, because OkHttp turns a redirected PUT into a GET. The method and the
 *   body are kept, the hop count is bounded, and the `Authorization` header never leaves the original host.
 * - The password lives only in the Authorization header of each request; nothing is logged.
 *
 * [requireHttps] exists for tests against a local plain-HTTP server; the app never turns it off.
 */
class OkHttpWebDav(
    private val root: HttpUrl,
    credentials: Credentials,
    private val client: OkHttpClient = defaultClient(),
    private val requireHttps: Boolean = true
) : WebDav {
    private val authorization = BasicCredentials.basic(credentials.username, credentials.password, Charsets.UTF_8)

    init {
        if (requireHttps && root.scheme != "https") throw SyncException(SyncError.INSECURE_URL, "Only https is allowed")
    }

    override suspend fun stat(path: String): DavStat? {
        val body = PROPFIND_BODY.toRequestBody(XML)
        return execute("PROPFIND", urlOf(path), body, mapOf("Depth" to "0")).use { response ->
            when (response.code) {
                207 -> {
                    val text = readText(response)
                    val me = MultiStatus.parse(text).firstOrNull { it.status in 200..299 } ?: return@use null
                    DavStat(me.isCollection, me.etag)
                }
                404 -> null
                405, 501 -> throw SyncException(SyncError.SERVER_TOO_OLD, "The server does not speak WebDAV here (HTTP ${response.code})")
                else -> throw failure(response)
            }
        }
    }

    override suspend fun mkcol(path: String) {
        execute("MKCOL", urlOf(path), null).use { response ->
            when (response.code) {
                201, 405 -> Unit // Created, or already there.
                else -> throw failure(response)
            }
        }
    }

    override suspend fun get(path: String, ifNoneMatch: String?): DavGet {
        val headers = if (ifNoneMatch != null) mapOf("If-None-Match" to ifNoneMatch) else emptyMap()
        return execute("GET", urlOf(path), null, headers).use { response ->
            when (response.code) {
                200 -> DavGet.Content(readText(response), etagOf(response))
                304 -> DavGet.NotModified
                404 -> DavGet.NotFound
                else -> throw failure(response)
            }
        }
    }

    override suspend fun put(path: String, text: String, precondition: Precondition): DavPut {
        val header = when (precondition) {
            Precondition.MustNotExist -> "If-None-Match" to "*"
            is Precondition.Etag -> "If-Match" to precondition.etag
        }
        val body = text.toRequestBody(TEXT)
        return execute("PUT", urlOf(path), body, mapOf(header)).use { response ->
            when (response.code) {
                200, 201, 204 -> DavPut.Stored(etagOf(response))
                412 -> DavPut.PreconditionFailed
                else -> throw failure(response)
            }
        }
    }

    private fun urlOf(path: String): HttpUrl {
        val builder = root.newBuilder()
        path.split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private suspend fun execute(method: String, url: HttpUrl, body: RequestBody?, headers: Map<String, String> = emptyMap()): Response =
        withContext(Dispatchers.IO) {
            var target = url
            var hops = 0
            while (true) {
                if (requireHttps && target.scheme != "https") throw SyncException(SyncError.INSECURE_URL, "Redirected to a non-https address")
                val builder = Request.Builder().url(target).method(method, body)
                headers.forEach { (name, value) -> builder.header(name, value) }
                // The password only goes to the server the user typed.
                if (target.host == root.host && target.port == root.port) builder.header("Authorization", authorization)
                val response = try {
                    runInterruptible { client.newCall(builder.build()).execute() }
                } catch (e: IOException) {
                    throw translate(e)
                }
                val location = if (response.code in REDIRECTS) response.header("Location") else null
                if (location == null) return@withContext response
                val next = response.request.url.resolve(location)
                response.close()
                if (next == null || ++hops > MAX_REDIRECTS) throw SyncException(SyncError.NETWORK, "Too many or invalid redirects")
                target = next
            }
            @Suppress("UNREACHABLE_CODE") // The loop only leaves through return or throw; the compiler needs a value of the lambda's type.
            error("unreachable")
        }

    private fun readText(response: Response): String {
        val body = response.body ?: return ""
        val source = body.source()
        val buffer = okio.Buffer()
        var total = 0L
        while (true) {
            val read = source.read(buffer, READ_CHUNK)
            if (read < 0) break
            total += read
            if (total > MAX_BODY_BYTES) throw SyncException(SyncError.REMOTE_CORRUPT, "The remote answer is too large")
        }
        return buffer.readString(Charsets.UTF_8)
    }

    private fun etagOf(response: Response): String? = response.header("ETag") ?: response.header("OC-ETag")

    private fun failure(response: Response): SyncException = when (val code = response.code) {
        401, 403 -> SyncException(SyncError.AUTH, "HTTP $code")
        408, 423, 429, in 500..599 -> SyncException(SyncError.NETWORK, "HTTP $code")
        else -> SyncException(SyncError.UNKNOWN, "HTTP $code")
    }

    private fun translate(e: IOException): SyncException = when {
        e is SSLException || e.cause is CertificateException -> SyncException(SyncError.TLS, "TLS: ${e.javaClass.simpleName}", e)
        e is SocketTimeoutException -> SyncException(SyncError.NETWORK, "Timed out", e)
        else -> SyncException(SyncError.NETWORK, e.javaClass.simpleName, e)
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val MAX_BODY_BYTES = 32L * 1024 * 1024
        private const val READ_CHUNK = 64L * 1024
        private val REDIRECTS = setOf(301, 302, 307, 308)
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val TEXT = "text/plain; charset=utf-8".toMediaType()
        private const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:getetag/><d:resourcetype/></d:prop></d:propfind>"""

        /** Timeouts are generous for mobile links but bounded, so a stuck server cannot hold a worker forever. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}
