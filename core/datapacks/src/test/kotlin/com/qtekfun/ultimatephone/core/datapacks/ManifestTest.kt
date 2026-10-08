package com.qtekfun.ultimatephone.core.datapacks

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestTest {
    private fun parse(json: String) = ManifestParser.parse(json.toByteArray())

    @Test
    fun parsesAManifestAndIgnoresFieldsFromNewerVersions() {
        val manifest = parse(manifestJson(packJson("businesses-es", "https://x/a.xz", "ab", 10)))
        assertEquals(1, manifest.packs.size)
        val pack = manifest.find("businesses-es")!!
        assertEquals("ES", pack.region)
        assertEquals("xz", pack.compression)
        assertEquals("© OpenStreetMap contributors", pack.attribution)
        assertNull(manifest.find("nope"))
    }

    @Test
    fun rejectsOtherSchemasMalformedJsonAndDuplicateIds() {
        assertThrows(ManifestException::class.java) { parse(manifestJson(schema = 2)) }
        assertThrows(ManifestException::class.java) { parse("not json") }
        assertThrows(ManifestException::class.java) { parse("""{"schema":1}""") }
        val dup = packJson("a", "https://x/a", "1", 1)
        assertThrows(ManifestException::class.java) { parse(manifestJson(dup, dup)) }
    }

    @Test
    fun rejectsPackIdsThatCouldEscapeTheDirectory() {
        listOf("../evil", "a/b", "A", "", "-x", "a..b", "x".repeat(65)).forEach {
            assertFalse("'$it' must be invalid", PackIds.isValid(it))
        }
        assertTrue(PackIds.isValid("businesses-es"))
        assertTrue(PackIds.isValid("spam-phoneblock.v2"))
        assertThrows(ManifestException::class.java) { parse(manifestJson(packJson("../x", "https://x/a", "1", 1))) }
    }

    @Test
    fun verifiesTheRfc8032TestVector() {
        // RFC 8032 section 7.1, TEST 1: empty message. Proves interoperability with any standard Ed25519 signer.
        val publicKey = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        val signature = hex(
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"
        )
        val verifier = ManifestVerifier(Base64.getEncoder().encodeToString(publicKey))
        assertTrue(verifier.verify(ByteArray(0), Base64.getEncoder().encodeToString(signature)))
        assertFalse(verifier.verify(byteArrayOf(1), Base64.getEncoder().encodeToString(signature)))
    }

    @Test
    fun acceptsOnlyTheRightSignatureOverTheExactBytes() {
        val key = TestKey()
        val verifier = ManifestVerifier(key.publicBase64)
        val message = manifestJson().toByteArray()
        val signature = key.sign(message)
        assertTrue(verifier.verify(message, signature))
        assertTrue(verifier.verify(message, "$signature\n"))
        assertFalse("one changed byte", verifier.verify(message + 1, signature))
        assertFalse("other key", ManifestVerifier(TestKey().publicBase64).verify(message, signature))
        assertFalse("not base64", verifier.verify(message, "%%%"))
        assertFalse("wrong length", verifier.verify(message, Base64.getEncoder().encodeToString(ByteArray(10))))
        assertFalse("empty", verifier.verify(message, ""))
    }

    @Test
    fun theEmbeddedKeyIsAValidEd25519PublicKey() {
        assertEquals(32, Base64.getDecoder().decode(DataPackKeys.MANIFEST_PUBLIC_KEY).size)
        ManifestVerifier()
    }

    private fun hex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
