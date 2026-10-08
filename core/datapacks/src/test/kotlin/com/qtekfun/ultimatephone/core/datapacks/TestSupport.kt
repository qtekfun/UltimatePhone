package com.qtekfun.ultimatephone.core.datapacks

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.Base64
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

/** Test files go under the module's build directory, never the system temp directory (which is RAM here). */
fun testDir(name: String): File = File(System.getProperty("user.dir"), "build/test-work/$name").apply {
    deleteRecursively()
    mkdirs()
}

fun xz(data: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    XZOutputStream(out, LZMA2Options(6)).use { it.write(data) }
    return out.toByteArray()
}

/** An Ed25519 key pair created in memory for the test; the private half is never written anywhere. */
class TestKey {
    private val private = Ed25519PrivateKeyParameters(SecureRandom())
    val publicBase64: String = Base64.getEncoder().encodeToString(private.generatePublicKey().encoded)

    fun sign(message: ByteArray): String {
        val signer = Ed25519Signer()
        signer.init(true, private)
        signer.update(message, 0, message.size)
        return Base64.getEncoder().encodeToString(signer.generateSignature())
    }
}

/** Serves canned bodies; downloads write the canned bytes to the target. Records what was requested. */
class FakeFetcher : HttpFetcher {
    val documents = mutableMapOf<String, Pair<ByteArray, String?>>()
    val files = mutableMapOf<String, ByteArray>()
    val requests = mutableListOf<String>()
    var offline = false
    var lastEtagSent: String? = null

    override fun fetch(url: String, etag: String?): Fetched {
        requests += url
        if (offline) throw IOException("offline")
        lastEtagSent = etag
        val (bytes, tag) = documents[url] ?: throw IOException("404")
        return if (etag != null && etag == tag) Fetched.NotModified else Fetched.Body(bytes, tag)
    }

    override fun download(url: String, target: File, onProgress: (Long) -> Unit) {
        requests += url
        if (offline) throw IOException("offline")
        val bytes = files[url] ?: throw IOException("404")
        target.writeBytes(bytes)
        onProgress(bytes.size.toLong())
    }
}

fun manifestJson(vararg packs: String, schema: Int = 1): String =
    """{"schema":$schema,"generatedAt":"2026-11-01T03:00:00Z","packs":[${packs.joinToString(",")}]}"""

fun packJson(
    id: String,
    url: String,
    sha: String,
    bytes: Long,
    version: String = "2026.11.01",
    uncompressedSha: String? = null,
    uncompressedBytes: Long = 0,
    dependsOn: List<String> = emptyList()
): String = """{"id":"$id","type":"businesses","region":"ES","version":"$version","url":"$url","sha256":"$sha","bytes":$bytes,""" +
    (if (uncompressedSha != null) """"uncompressedSha256":"$uncompressedSha",""" else "") +
    """"uncompressedBytes":$uncompressedBytes,"entries":3,"license":"ODbL-1.0","attribution":"© OpenStreetMap contributors",""" +
    """"dependsOn":[${dependsOn.joinToString(",") { "\"$it\"" }}],"unknownFutureField":true}"""
