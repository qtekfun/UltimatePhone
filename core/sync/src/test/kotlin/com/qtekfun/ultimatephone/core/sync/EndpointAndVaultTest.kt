package com.qtekfun.ultimatephone.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NextcloudEndpointTest {
    private fun root(url: String, user: String = "alice") = NextcloudEndpoint.davRoot(url, user).toString()

    @Test
    fun buildsTheWebDavRootFromAServerAddress() {
        assertEquals("https://cloud.example.com/remote.php/dav/files/alice", root("https://cloud.example.com"))
        assertEquals("https://cloud.example.com/remote.php/dav/files/alice", root("cloud.example.com/"))
        assertEquals("https://example.com/nextcloud/remote.php/dav/files/alice", root("https://example.com/nextcloud/"))
    }

    @Test
    fun acceptsAPastedWebDavAddress() {
        assertEquals(
            "https://cloud.example.com/remote.php/dav/files/alice",
            root("https://cloud.example.com/remote.php/dav/files/alice/Documents")
        )
    }

    @Test
    fun usersWithSpecialCharactersAreEncodedAsOneSegment() {
        assertEquals("https://c.example/remote.php/dav/files/a%20b%2Fc", root("https://c.example", "a b/c"))
    }

    @Test
    fun plainHttpIsRefusedWithAClearError() {
        for (url in listOf("http://cloud.example.com", "HTTP://cloud.example.com")) {
            val e = runCatching { root(url) }.exceptionOrNull() as SyncException
            assertEquals(SyncError.INSECURE_URL, e.error)
        }
    }

    @Test
    fun unusableInputIsInvalid() {
        for ((url, user) in listOf("" to "a", "https://x.example" to " ", "ftp://x.example" to "a", "https://user:pw@x.example" to "a", "https://" to "a")) {
            try {
                root(url, user)
                fail("accepted $url")
            } catch (e: SyncException) {
                assertTrue(e.error == SyncError.INVALID_URL || e.error == SyncError.INSECURE_URL)
            }
        }
    }
}

class MultiStatusTest {
    @Test
    fun parsesResourcesEtagsAndCollections() {
        val xml = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">
            <d:response><d:href>/a/</d:href><d:propstat><d:prop><d:getetag>"1"</d:getetag><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
            <d:response><d:href>/a/f%20x</d:href><d:propstat><d:prop><d:getetag>"2"</d:getetag><d:resourcetype/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
            </d:multistatus>"""
        val result = MultiStatus.parse(xml)
        assertEquals(2, result.size)
        assertEquals(DavResource("/a/", true, "\"1\"", 200), result[0])
        assertEquals(DavResource("/a/f%20x", false, "\"2\"", 200), result[1])
    }

    @Test
    fun aDoctypeOrEntityIsRefusedBeforeParsing() {
        val xxe = "<?xml version=\"1.0\"?><!DOCTYPE m [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>" +
            "<d:multistatus xmlns:d=\"DAV:\"><d:response><d:href>&x;</d:href></d:response></d:multistatus>"
        val bomb = "<?xml version=\"1.0\"?><!DOCTYPE l [<!ENTITY a \"aaaa\"><!ENTITY b \"&a;&a;&a;&a;\">]>" +
            "<d:multistatus xmlns:d=\"DAV:\">&b;</d:multistatus>"
        for (xml in listOf(xxe, bomb)) {
            val e = runCatching { MultiStatus.parse(xml) }.exceptionOrNull()
            assertTrue(e is SyncException)
        }
    }

    @Test
    fun malformedXmlIsATypedError() {
        assertTrue(runCatching { MultiStatus.parse("<d:multistatus") }.exceptionOrNull() is SyncException)
    }
}

/** Not a real cipher: it only needs to be reversible and to differ from the input. */
private class XorCipher(private val broken: Boolean = false) : SecretCipher {
    override fun encrypt(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor 0x5a).toByte() }

    override fun decrypt(blob: ByteArray): ByteArray? = if (broken) null else ByteArray(blob.size) { (blob[it].toInt() xor 0x5a).toByte() }
}

class SecretVaultTest {
    private val password = "fake-app-password-xyz"

    @Test
    fun roundTripsAndNeverStoresThePasswordInTheClear() {
        val sealed = SecretVault(XorCipher()).seal(password)
        assertFalse(sealed.contains(password))
        assertFalse(String(java.util.Base64.getDecoder().decode(sealed)).contains(password))
        assertEquals(password, SecretVault(XorCipher()).open(sealed))
    }

    @Test
    fun anUnreadableBlobGivesNullSoTheUserEntersThePasswordAgain() {
        val sealed = SecretVault(XorCipher()).seal(password)
        assertNull(SecretVault(XorCipher(broken = true)).open(sealed))
        assertNull(SecretVault(XorCipher()).open("not base64 !!"))
        assertNull(SecretVault(XorCipher()).open(""))
    }

    @Test
    fun credentialsDoNotPrintTheirSecrets() {
        val text = Credentials("https://cloud.example.com", "alice", password).toString()
        assertFalse(text.contains(password))
        assertFalse(text.contains("alice"))
        assertFalse(SyncResult(error = SyncError.AUTH, detail = "HTTP 401").toString().contains(password))
    }
}
