package com.qtekfun.ultimatephone.core.datapacks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataPackRepositoryTest {
    private val key = TestKey()
    private val source = DataPackSource("https://example.org/manifest.json")
    private val database = ByteArray(2000) { it.toByte() }
    private val compressed = xz(database)

    private fun pack(id: String, version: String = "1", dependsOn: List<String> = emptyList()) =
        packJson(id, "https://example.org/$id.db.xz", Hashing.sha256Hex(compressed), compressed.size.toLong(), version, dependsOn = dependsOn)

    private fun publish(fetcher: FakeFetcher, vararg packs: String, etag: String? = "\"v1\"", tamper: Boolean = false) {
        val body = manifestJson(*packs).toByteArray()
        fetcher.documents[source.manifestUrl] = body to etag
        val signed = if (tamper) manifestJson(*packs, schema = 1).toByteArray() + 32 else body
        fetcher.documents[source.signatureUrl] = key.sign(signed).toByteArray() to null
        packs.forEach { raw -> Regex(""""id":"([^"]+)"""").find(raw)?.let { fetcher.files["https://example.org/${it.groupValues[1]}.db.xz"] = compressed } }
    }

    private fun repo(name: String, fetcher: FakeFetcher): DataPackRepository {
        val dir = testDir(name)
        return DataPackRepository(fetcher, ManifestVerifier(key.publicBase64), PackStore(java.io.File(dir, "packs")), java.io.File(dir, "cache"), source)
    }

    @Test
    fun refreshVerifiesParsesAndCaches() {
        val fetcher = FakeFetcher().also { publish(it, pack("a")) }
        val repo = repo("refresh", fetcher)
        assertNull(repo.cachedManifest())
        val result = repo.refresh()
        assertTrue(result is RefreshResult.Ok && !result.fromCache)
        assertNotNull(repo.cachedManifest())
    }

    @Test
    fun aManifestWithABadSignatureIsRejectedAndNeverCached() {
        val fetcher = FakeFetcher().also { publish(it, pack("a"), tamper = true) }
        val repo = repo("badsig", fetcher)
        assertEquals(RefreshResult.InvalidSignature, repo.refresh())
        assertNull(repo.cachedManifest())
    }

    @Test
    fun aBadManifestDoesNotReplaceAGoodCachedOne() {
        val fetcher = FakeFetcher().also { publish(it, pack("a")) }
        val repo = repo("keepgood", fetcher)
        repo.refresh()
        publish(fetcher, pack("b"), etag = "\"v2\"", tamper = true)
        assertEquals(RefreshResult.InvalidSignature, repo.refresh())
        assertEquals(listOf("a"), repo.cachedManifest()!!.packs.map { it.id })
    }

    @Test
    fun offlineFallsBackToTheCachedManifest() {
        val fetcher = FakeFetcher().also { publish(it, pack("a")) }
        val repo = repo("offline", fetcher)
        repo.refresh()
        fetcher.offline = true
        val result = repo.refresh()
        assertTrue(result is RefreshResult.Offline && result.manifest != null)
    }

    @Test
    fun anUnchangedManifestIsAnswered304AndServedFromCache() {
        val fetcher = FakeFetcher().also { publish(it, pack("a")) }
        val repo = repo("etag", fetcher)
        repo.refresh()
        val result = repo.refresh()
        assertEquals("\"v1\"", fetcher.lastEtagSent)
        assertTrue(result is RefreshResult.Ok && result.fromCache)
    }

    @Test
    fun aMalformedButCorrectlySignedManifestIsReported() {
        val fetcher = FakeFetcher()
        val body = """{"schema":9,"generatedAt":"x","packs":[]}""".toByteArray()
        fetcher.documents[source.manifestUrl] = body to null
        fetcher.documents[source.signatureUrl] = key.sign(body).toByteArray() to null
        assertTrue(repo("malformed", fetcher).refresh() is RefreshResult.Malformed)
    }

    @Test
    fun installsDependenciesFirstAndDetectsUpdates() {
        val fetcher = FakeFetcher().also { publish(it, pack("base"), pack("child", dependsOn = listOf("base"))) }
        val repo = repo("deps", fetcher)
        val manifest = (repo.refresh() as RefreshResult.Ok).manifest
        assertEquals(InstallResult.Installed("child", "1"), repo.install(manifest, "child"))
        assertEquals(mapOf("base" to "1", "child" to "1"), repo.installedVersions())
        assertEquals(listOf("https://example.org/base.db.xz", "https://example.org/child.db.xz"), fetcher.requests.filter { it.endsWith(".xz") })
        assertTrue(repo.updatesAvailable(manifest).isEmpty())

        publish(fetcher, pack("base", version = "2"), pack("child", dependsOn = listOf("base")), etag = "\"v2\"")
        val newer = (repo.refresh() as RefreshResult.Ok).manifest
        assertEquals(listOf("base"), repo.updatesAvailable(newer).map { it.id })
    }

    @Test
    fun missingOrCyclicDependenciesFailCleanly() {
        val fetcher = FakeFetcher().also {
            publish(it, pack("a", dependsOn = listOf("b")), pack("b", dependsOn = listOf("a")), pack("c", dependsOn = listOf("zzz")))
        }
        val repo = repo("cycle", fetcher)
        val manifest = (repo.refresh() as RefreshResult.Ok).manifest
        assertEquals(InstallResult.Failed(InstallFailure.UNSUPPORTED), repo.install(manifest, "a"))
        assertEquals(InstallResult.Failed(InstallFailure.UNSUPPORTED), repo.install(manifest, "c"))
        assertEquals(InstallResult.Failed(InstallFailure.UNSUPPORTED), repo.install(manifest, "unknown"))
        assertTrue(repo.installedVersions().isEmpty())
    }

    @Test
    fun removeDeletesThePack() {
        val fetcher = FakeFetcher().also { publish(it, pack("a")) }
        val repo = repo("remove", fetcher)
        repo.install((repo.refresh() as RefreshResult.Ok).manifest, "a")
        assertNotNull(repo.fileFor("a"))
        repo.remove("a")
        assertNull(repo.fileFor("a"))
    }
}
