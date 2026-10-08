package com.qtekfun.ultimatephone.core.datapacks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackInstallerTest {
    private val database = ByteArray(5000) { (it * 7).toByte() }
    private val compressed = xz(database)
    private val url = "https://example.org/businesses-es.db.xz"

    private fun entry(
        sha: String = Hashing.sha256Hex(compressed),
        bytes: Long = compressed.size.toLong(),
        uncompressedSha: String? = Hashing.sha256Hex(database),
        uncompressedBytes: Long = database.size.toLong(),
        version: String = "1",
        packUrl: String = url,
        compression: String = "xz"
    ) = PackEntry(
        id = "businesses-es", type = "businesses", version = version, url = packUrl, sha256 = sha, bytes = bytes,
        compression = compression, uncompressedSha256 = uncompressedSha, uncompressedBytes = uncompressedBytes
    )

    private fun setup(name: String): Triple<FakeFetcher, PackStore, PackInstaller> {
        val fetcher = FakeFetcher().apply { files[url] = compressed }
        val store = PackStore(testDir(name))
        return Triple(fetcher, store, PackInstaller(fetcher, store))
    }

    @Test
    fun installsAGoodPackAndRecordsItsVersion() {
        val (_, store, installer) = setup("good")
        val result = installer.install(entry())
        assertEquals(InstallResult.Installed("businesses-es", "1"), result)
        assertTrue(store.fileFor("businesses-es").readBytes().contentEquals(database))
        assertEquals("1", store.versionOf("businesses-es"))
    }

    @Test
    fun aBadChecksumInstallsNothing() {
        val (_, store, installer) = setup("badsum")
        val result = installer.install(entry(sha = "00".repeat(32)))
        assertEquals(InstallResult.Failed(InstallFailure.CHECKSUM_MISMATCH), result)
        assertFalse(store.fileFor("businesses-es").exists())
        assertEquals(null, store.versionOf("businesses-es"))
    }

    @Test
    fun aWrongSizeIsRejected() {
        val (_, _, installer) = setup("badsize")
        assertEquals(InstallResult.Failed(InstallFailure.SIZE_MISMATCH), installer.install(entry(bytes = 1)))
    }

    @Test
    fun aCorruptArchiveWithAValidChecksumIsRejected() {
        val garbage = ByteArray(300) { it.toByte() }
        val fetcher = FakeFetcher().apply { files[url] = garbage }
        val store = PackStore(testDir("garbage"))
        val result = PackInstaller(fetcher, store).install(entry(sha = Hashing.sha256Hex(garbage), bytes = garbage.size.toLong()))
        assertEquals(InstallResult.Failed(InstallFailure.BAD_ARCHIVE), result)
        assertFalse(store.fileFor("businesses-es").exists())
    }

    @Test
    fun theUnpackedChecksumIsVerifiedToo() {
        val (_, store, installer) = setup("badunpacked")
        val result = installer.install(entry(uncompressedSha = "11".repeat(32)))
        assertEquals(InstallResult.Failed(InstallFailure.CHECKSUM_MISMATCH), result)
        assertFalse(store.fileFor("businesses-es").exists())
    }

    @Test
    fun anArchiveThatUnpacksBeyondTheAnnouncedSizeIsStopped() {
        val (_, store, installer) = setup("bomb")
        val result = installer.install(entry(uncompressedBytes = 100, uncompressedSha = null))
        assertEquals(InstallResult.Failed(InstallFailure.TOO_LARGE), result)
        assertFalse(store.fileFor("businesses-es").exists())
    }

    @Test
    fun aFailedUpdateKeepsTheOldVersionWorking() {
        val (fetcher, store, installer) = setup("keepold")
        installer.install(entry(version = "1"))
        fetcher.offline = true
        assertEquals(InstallResult.Failed(InstallFailure.NETWORK), installer.install(entry(version = "2")))
        assertEquals("1", store.versionOf("businesses-es"))
        assertTrue(store.fileFor("businesses-es").readBytes().contentEquals(database))
        fetcher.offline = false
        fetcher.files[url] = xz(database + 1)
        val newer =
            entry(
                version = "2",
                sha = Hashing.sha256Hex(fetcher.files.getValue(url)),
                bytes = fetcher.files.getValue(url).size.toLong(),
                uncompressedSha = null,
                uncompressedBytes = 0
            )
        assertEquals(InstallResult.Installed("businesses-es", "2"), installer.install(newer))
        assertEquals(database.size + 1, store.fileFor("businesses-es").length().toInt())
    }

    @Test
    fun refusesPlainHttpAndUnknownCompression() {
        val (fetcher, _, installer) = setup("insecure")
        assertEquals(InstallResult.Failed(InstallFailure.INSECURE_URL), installer.install(entry(packUrl = "http://example.org/a")))
        assertEquals(InstallResult.Failed(InstallFailure.UNSUPPORTED), installer.install(entry(compression = "rar")))
        assertTrue("nothing may be requested", fetcher.requests.isEmpty())
    }

    @Test
    fun uncompressedPacksAreSupported() {
        val fetcher = FakeFetcher().apply { files[url] = database }
        val store = PackStore(testDir("plain"))
        val e = entry(sha = Hashing.sha256Hex(database), bytes = database.size.toLong(), compression = "none")
        assertEquals(InstallResult.Installed("businesses-es", "1"), PackInstaller(fetcher, store).install(e))
    }

    @Test
    fun removingAPackDeletesTheFileAndTheRecord() {
        val (_, store, installer) = setup("remove")
        installer.install(entry())
        store.remove("businesses-es")
        assertFalse(store.fileFor("businesses-es").exists())
        assertEquals(emptyMap<String, String>(), store.installed())
    }

    @Test
    fun leftoverTemporaryFilesAreNotLeftBehind() {
        val dir = testDir("clean")
        val fetcher = FakeFetcher().apply { files[url] = compressed }
        PackInstaller(fetcher, PackStore(dir)).install(entry(sha = "00".repeat(32)))
        assertEquals(emptyList<String>(), dir.list().orEmpty().filter { it.endsWith(".part") })
    }
}
