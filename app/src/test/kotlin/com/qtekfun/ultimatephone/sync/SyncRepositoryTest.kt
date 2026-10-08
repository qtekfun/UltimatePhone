package com.qtekfun.ultimatephone.sync

import com.qtekfun.ultimatephone.core.spam.lists.EntryKind
import com.qtekfun.ultimatephone.core.spam.lists.ListEntry
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.core.sync.DavGet
import com.qtekfun.ultimatephone.core.sync.DavPut
import com.qtekfun.ultimatephone.core.sync.DavStat
import com.qtekfun.ultimatephone.core.sync.LocalLists
import com.qtekfun.ultimatephone.core.sync.Precondition
import com.qtekfun.ultimatephone.core.sync.SecretCipher
import com.qtekfun.ultimatephone.core.sync.SecretVault
import com.qtekfun.ultimatephone.core.sync.SyncError
import com.qtekfun.ultimatephone.core.sync.SyncException
import com.qtekfun.ultimatephone.core.sync.WebDav
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class XorCipher : SecretCipher {
    override fun encrypt(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor 0x5a).toByte() }

    override fun decrypt(blob: ByteArray): ByteArray = ByteArray(blob.size) { (blob[it].toInt() xor 0x5a).toByte() }
}

internal class MemorySyncSettings : SyncSettings {
    val data = MutableStateFlow(StoredSync())
    private val etags = HashMap<String, String?>()
    private val prints = HashMap<ListType, String>()

    override val stored = data

    override suspend fun saveAccount(serverUrl: String, username: String, sealedPassword: String?) {
        data.value = data.value.copy(serverUrl = serverUrl, username = username, sealedPassword = sealedPassword ?: data.value.sealedPassword)
    }

    override suspend fun setEnabled(enabled: Boolean) {
        data.value = data.value.copy(enabled = enabled)
    }

    override suspend fun recordSuccess(at: Long) {
        data.value = data.value.copy(lastSyncAt = at, lastError = null)
    }

    override suspend fun recordError(error: SyncError, detail: String?) {
        data.value = data.value.copy(lastError = error, lastDetail = detail)
    }

    override suspend fun clear() {
        data.value = StoredSync()
        etags.clear()
        prints.clear()
    }

    override suspend fun etag(file: String) = etags[file]

    override suspend fun setEtag(file: String, etag: String?) {
        etags[file] = etag
    }

    override suspend fun syncedFingerprint(list: ListType) = prints[list]

    override suspend fun setSyncedFingerprint(list: ListType, fingerprint: String) {
        prints[list] = fingerprint
    }
}

internal class MemoryLists : LocalLists {
    val rows = HashMap<ListType, MutableMap<String, ListEntry>>()

    fun add(list: ListType, id: String, value: String, at: Long = 10) {
        rows.getOrPut(list) { HashMap() }[id] = ListEntry(id, EntryKind.NUMBER, value, null, null, at, "dev", false)
    }

    override suspend fun entries(list: ListType) = rows[list]?.values?.toList().orEmpty()

    override suspend fun apply(list: ListType, upserts: List<ListEntry>, deleteTombstones: List<String>) {
        val t = rows.getOrPut(list) { HashMap() }
        upserts.forEach { t[it.id] = it }
        deleteTombstones.forEach { t.remove(it) }
    }
}

/** Tiny ETag server: enough for one device. */
internal class OneFileDav(val requestedAuth: MutableList<String> = ArrayList()) : WebDav {
    val files = HashMap<String, Pair<String, String>>()
    var failWith: SyncException? = null
    private var n = 0

    override suspend fun stat(path: String): DavStat? {
        failWith?.let { throw it }
        return DavStat(true, null)
    }

    override suspend fun mkcol(path: String) = Unit

    override suspend fun get(path: String, ifNoneMatch: String?): DavGet {
        failWith?.let { throw it }
        val f = files[path] ?: return DavGet.NotFound
        return if (ifNoneMatch == f.second) DavGet.NotModified else DavGet.Content(f.first, f.second)
    }

    override suspend fun put(path: String, text: String, precondition: Precondition): DavPut {
        failWith?.let { throw it }
        val etag = "\"e${n++}\""
        files[path] = text to etag
        return DavPut.Stored(etag)
    }
}

class SyncRepositoryTest {
    private val settings = MemorySyncSettings()
    private val lists = MemoryLists()
    private val dav = OneFileDav()
    private val seen = ArrayList<String>()
    private var now = 5_000L
    private val repo = DefaultSyncRepository(
        settings = settings,
        vault = SecretVault(XorCipher()),
        lists = lists,
        davFactory = WebDavFactory { root, credentials ->
            seen += root.toString()
            seen += credentials.username
            dav
        },
        clock = { now }
    )
    private val password = "fake-app-password-123"

    @Test
    fun theAddressMustBeHttpsAndNothingIsSavedOtherwise() = runTest {
        val result = repo.saveAccount("http://cloud.example.com", "alice", password)
        assertEquals(SyncError.INSECURE_URL, result.error)
        assertEquals("", settings.data.value.serverUrl)
        assertNull(settings.data.value.sealedPassword)
    }

    @Test
    fun thePasswordIsStoredSealedAndNeverInTheUiState() = runTest {
        assertTrue(repo.saveAccount("https://cloud.example.com", "alice", password).isSuccess)
        val stored = settings.data.value
        assertNotNull(stored.sealedPassword)
        assertFalse(stored.sealedPassword!!.contains(password))
        val ui = repo.state.first()
        assertTrue(ui.hasPassword)
        assertFalse(ui.toString().contains(password))
        assertEquals(password, repo.credentials()?.password)
    }

    @Test
    fun savingWithoutAPasswordKeepsTheSavedOne() = runTest {
        repo.saveAccount("https://cloud.example.com", "alice", password)
        repo.saveAccount("https://cloud.example.com", "alice2", null)
        assertEquals(password, repo.credentials()?.password)
        assertEquals("alice2", repo.credentials()?.username)
    }

    @Test
    fun syncNowWithoutAnAccountSaysSo() = runTest {
        assertEquals(SyncError.NOT_CONFIGURED, repo.syncNow().error)
        assertNull(repo.syncIfEnabled())
    }

    @Test
    fun aSuccessfulSyncRecordsTheTimeAndUploadsTheLists() = runTest {
        repo.saveAccount("https://cloud.example.com", "alice", password)
        lists.add(ListType.OWN, "1", "+34600000001")
        assertTrue(repo.refreshPending())
        assertTrue(repo.syncNow().isSuccess)
        assertEquals(5_000L, settings.data.value.lastSyncAt)
        assertFalse(repo.refreshPending())
        assertTrue(dav.files.getValue("UltimatePhone/spam-list.jsonl").first.contains("+34600000001"))
        assertEquals("https://cloud.example.com/remote.php/dav/files/alice", seen[0])
    }

    @Test
    fun aFailedSyncKeepsTheErrorForTheScreenAndTheNextSuccessClearsIt() = runTest {
        repo.saveAccount("https://cloud.example.com", "alice", password)
        dav.failWith = SyncException(SyncError.AUTH, "HTTP 401")
        assertEquals(SyncError.AUTH, repo.syncNow().error)
        assertEquals(SyncError.AUTH, repo.state.first().lastError)
        dav.failWith = null
        repo.syncNow()
        assertNull(repo.state.first().lastError)
    }

    @Test
    fun backgroundSyncOnlyRunsWhenEnabled() = runTest {
        repo.saveAccount("https://cloud.example.com", "alice", password)
        assertNull(repo.syncIfEnabled())
        repo.setEnabled(true)
        assertNotNull(repo.syncIfEnabled())
    }

    @Test
    fun forgettingRemovesTheAccountThePasswordAndTheBookkeeping() = runTest {
        repo.saveAccount("https://cloud.example.com", "alice", password)
        repo.setEnabled(true)
        repo.syncNow()
        repo.forget()
        assertEquals(StoredSync(), settings.data.value)
        assertNull(repo.credentials())
        assertNull(settings.etag("spam-list.jsonl"))
        assertNull(settings.syncedFingerprint(ListType.OWN))
    }

    @Test
    fun aPasswordThatCannotBeReadAnymoreIsATypedError() = runTest {
        val broken = DefaultSyncRepository(
            settings = settings.also { it.data.value = StoredSync(serverUrl = "https://c.example", username = "a", sealedPassword = "AAAA") },
            vault = SecretVault(
                object : SecretCipher {
                    override fun encrypt(plain: ByteArray) = plain

                    override fun decrypt(blob: ByteArray): ByteArray? = null
                }
            ),
            lists = lists,
            davFactory = WebDavFactory { _, _ -> dav }
        )
        assertEquals(SyncError.CREDENTIALS_UNAVAILABLE, broken.syncNow().error)
        assertNull(broken.credentials())
    }

    @Test
    fun uiStateCarriesNoSecretEvenWhenPrinted() = runTest {
        repo.saveAccount("https://cloud.example.com", "alice", password)
        val text = repo.state.map { it.toString() }.first()
        assertFalse(text.contains(password))
    }
}
