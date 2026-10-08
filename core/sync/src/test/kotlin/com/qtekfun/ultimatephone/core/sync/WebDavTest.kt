package com.qtekfun.ultimatephone.core.sync

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class WebDavTest {
    private lateinit var server: MockWebServer
    private val creds = Credentials("unused", "alice", "fake-app-password-123")

    @Before
    fun start() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun dav(base: String = "/remote.php/dav/files/alice/") = OkHttpWebDav(server.url(base), creds, requireHttps = false)

    private val multistatus = """<?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns">
          <d:response><d:href>/remote.php/dav/files/alice/UltimatePhone/</d:href>
            <d:propstat><d:prop><d:getetag>&quot;abc123&quot;</d:getetag><d:resourcetype><d:collection/></d:resourcetype></d:prop>
            <d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
        </d:multistatus>"""

    @Test
    fun propfindSendsDepthZeroBasicAuthAndParsesTheAnswer() = runTest {
        server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus))
        val stat = dav().stat("UltimatePhone")
        assertEquals(DavStat(true, "\"abc123\""), stat)
        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("/remote.php/dav/files/alice/UltimatePhone", request.path)
        assertEquals("0", request.getHeader("Depth"))
        assertEquals("Basic " + java.util.Base64.getEncoder().encodeToString("alice:fake-app-password-123".toByteArray()), request.getHeader("Authorization"))
    }

    @Test
    fun missingResourceIsNullAndMkcolCreates() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(201))
        val d = dav()
        assertNull(d.stat("UltimatePhone"))
        d.mkcol("UltimatePhone")
        server.takeRequest()
        assertEquals("MKCOL", server.takeRequest().method)
    }

    @Test
    fun mkcolOnAnExistingFolderIsNotAnError() = runTest {
        server.enqueue(MockResponse().setResponseCode(405))
        dav().mkcol("UltimatePhone")
    }

    @Test
    fun getReturnsBodyAndEtagAndHonoursConditional() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"e1\"").setBody("line\n"))
        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(MockResponse().setResponseCode(404))
        val d = dav()
        assertEquals(DavGet.Content("line\n", "\"e1\""), d.get("UltimatePhone/f"))
        assertEquals(DavGet.NotModified, d.get("UltimatePhone/f", ifNoneMatch = "\"e1\""))
        assertEquals(DavGet.NotFound, d.get("UltimatePhone/g"))
        server.takeRequest()
        assertEquals("\"e1\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun putSendsIfMatchOrIfNoneMatchAndMaps412() = runTest {
        server.enqueue(MockResponse().setResponseCode(204).setHeader("ETag", "\"e2\""))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(412))
        val d = dav()
        assertEquals(DavPut.Stored("\"e2\""), d.put("UltimatePhone/f", "body\n", Precondition.Etag("\"e1\"")))
        assertEquals(DavPut.Stored(null), d.put("UltimatePhone/f", "body\n", Precondition.MustNotExist))
        assertEquals(DavPut.PreconditionFailed, d.put("UltimatePhone/f", "body\n", Precondition.Etag("\"old\"")))
        val first = server.takeRequest()
        assertEquals("PUT", first.method)
        assertEquals("\"e1\"", first.getHeader("If-Match"))
        assertEquals("body\n", first.body.readUtf8())
        assertEquals("*", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun statusCodesMapToTypedErrors() = runTest {
        val cases = mapOf(401 to SyncError.AUTH, 403 to SyncError.AUTH, 503 to SyncError.NETWORK, 429 to SyncError.NETWORK, 418 to SyncError.UNKNOWN)
        for ((code, expected) in cases) {
            server.enqueue(MockResponse().setResponseCode(code))
            val error = runCatching { dav().get("f") }.exceptionOrNull() as SyncException
            assertEquals("HTTP $code", expected, error.error)
        }
        server.enqueue(MockResponse().setResponseCode(405))
        val old = runCatching { dav().stat("f") }.exceptionOrNull() as SyncException
        assertEquals(SyncError.SERVER_TOO_OLD, old.error)
    }

    @Test
    fun aRedirectedPutKeepsItsMethodBodyAndPrecondition() = runTest {
        server.enqueue(MockResponse().setResponseCode(308).setHeader("Location", server.url("/moved/f").toString()))
        server.enqueue(MockResponse().setResponseCode(204).setHeader("ETag", "\"n\""))
        val result = dav().put("f", "data", Precondition.Etag("\"e\""))
        assertEquals(DavPut.Stored("\"n\""), result)
        server.takeRequest()
        val second = server.takeRequest()
        assertEquals("PUT", second.method)
        assertEquals("/moved/f", second.path)
        assertEquals("data", second.body.readUtf8())
        assertEquals("\"e\"", second.getHeader("If-Match"))
    }

    @Test
    fun a301OnAPutIsNotTurnedIntoAGet() = runTest {
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", server.url("/elsewhere/f").toString()))
        server.enqueue(MockResponse().setResponseCode(201))
        dav().put("f", "data", Precondition.MustNotExist)
        server.takeRequest()
        assertEquals("PUT", server.takeRequest().method)
    }

    @Test
    fun theAuthorizationHeaderNeverFollowsARedirectToAnotherHost() = runTest {
        val other = MockWebServer().also { it.start() }
        try {
            other.enqueue(MockResponse().setResponseCode(200).setBody("x").setHeader("ETag", "\"1\""))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/stolen").toString()))
            dav().get("f")
            assertNotNull(server.takeRequest().getHeader("Authorization"))
            assertNull(other.takeRequest().getHeader("Authorization"))
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun redirectLoopsAreBounded() = runTest {
        repeat(10) { server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/again").toString())) }
        val error = runCatching { dav().get("f") }.exceptionOrNull() as SyncException
        assertEquals(SyncError.NETWORK, error.error)
    }

    @Test
    fun plainHttpIsRefusedBeforeAnythingIsSent() {
        try {
            OkHttpWebDav(server.url("/"), creds)
            fail("http must be refused")
        } catch (e: SyncException) {
            assertEquals(SyncError.INSECURE_URL, e.error)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun connectionFailuresAreNetworkErrorsWithoutLeakingTheServerOrPassword() = runTest {
        val url = server.url("/")
        server.shutdown()
        val error = runCatching { OkHttpWebDav(url, creds, requireHttps = false).get("f") }.exceptionOrNull() as SyncException
        assertEquals(SyncError.NETWORK, error.error)
        assertFalse(error.message.orEmpty().contains(creds.password))
        server = MockWebServer().also { it.start() }
    }

    @Test
    fun testConnectionReportsAuthFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(SyncError.AUTH, testConnection(dav()).error)
        server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus))
        assertTrue(testConnection(dav()).isSuccess)
    }
}
