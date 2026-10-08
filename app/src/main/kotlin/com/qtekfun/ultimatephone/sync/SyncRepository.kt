package com.qtekfun.ultimatephone.sync

import com.qtekfun.ultimatephone.core.sync.Credentials
import com.qtekfun.ultimatephone.core.sync.ListMerge
import com.qtekfun.ultimatephone.core.sync.ListSyncer
import com.qtekfun.ultimatephone.core.sync.LocalLists
import com.qtekfun.ultimatephone.core.sync.NextcloudEndpoint
import com.qtekfun.ultimatephone.core.sync.SecretVault
import com.qtekfun.ultimatephone.core.sync.SyncError
import com.qtekfun.ultimatephone.core.sync.SyncException
import com.qtekfun.ultimatephone.core.sync.SyncResult
import com.qtekfun.ultimatephone.core.sync.WebDav
import com.qtekfun.ultimatephone.core.sync.hasPendingChanges
import com.qtekfun.ultimatephone.core.sync.testConnection as pingServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl

enum class SyncPhase { IDLE, SYNCING }

/** What the Sync screen shows. Never holds the password, not even sealed. */
data class SyncUiState(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val username: String = "",
    val hasPassword: Boolean = false,
    val phase: SyncPhase = SyncPhase.IDLE,
    val lastSyncAt: Long? = null,
    val lastError: SyncError? = null,
    /** Local changes not uploaded yet (made offline, or waiting for the debounce). */
    val pendingChanges: Boolean = false
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && username.isNotBlank() && hasPassword
}

interface SyncRepository {
    val state: Flow<SyncUiState>

    /** Saves the account. A null [password] keeps the saved one. Fails with a typed error when the address is not https. */
    suspend fun saveAccount(serverUrl: String, username: String, password: String?): SyncResult

    /** Tries the typed values (or the saved password when [password] is null) without saving anything. */
    suspend fun testConnection(serverUrl: String, username: String, password: String?): SyncResult

    suspend fun setEnabled(enabled: Boolean)

    suspend fun isEnabled(): Boolean

    /** Syncs now, whether or not background sync is on. */
    suspend fun syncNow(): SyncResult

    /** Background entry point: does nothing unless sync is on and set up. */
    suspend fun syncIfEnabled(): SyncResult?

    /** Recomputes [SyncUiState.pendingChanges]. */
    suspend fun refreshPending(): Boolean

    /** Forgets the account and the password. */
    suspend fun forget()

    /** Credentials for the backup section; null when none are saved or the password cannot be read. */
    suspend fun credentials(): Credentials?
}

fun interface WebDavFactory {
    fun create(root: HttpUrl, credentials: Credentials): WebDav
}

class DefaultSyncRepository(
    private val settings: SyncSettings,
    private val vault: SecretVault,
    private val lists: LocalLists,
    private val davFactory: WebDavFactory,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retentionMs: Long = ListMerge.DEFAULT_TOMBSTONE_RETENTION_MS
) : SyncRepository {
    private val mutex = Mutex()
    private val syncing = MutableStateFlow(false)
    private val pending = MutableStateFlow(false)

    override val state: Flow<SyncUiState> = combine(settings.stored, syncing, pending) { s, busy, dirty ->
        SyncUiState(
            enabled = s.enabled,
            serverUrl = s.serverUrl,
            username = s.username,
            hasPassword = s.sealedPassword != null,
            phase = if (busy) SyncPhase.SYNCING else SyncPhase.IDLE,
            lastSyncAt = s.lastSyncAt,
            lastError = s.lastError,
            pendingChanges = dirty
        )
    }

    override suspend fun saveAccount(serverUrl: String, username: String, password: String?): SyncResult {
        val url = serverUrl.trim()
        val user = username.trim()
        return try {
            NextcloudEndpoint.davRoot(url, user)
            val sealed = password?.takeIf { it.isNotEmpty() }?.let(vault::seal)
            settings.saveAccount(url, user, sealed)
            SyncResult()
        } catch (e: SyncException) {
            SyncResult(error = e.error, detail = e.message)
        }
    }

    override suspend fun testConnection(serverUrl: String, username: String, password: String?): SyncResult = try {
        val typed = password?.takeIf { it.isNotEmpty() } ?: savedPassword()
        val root = NextcloudEndpoint.davRoot(serverUrl.trim(), username.trim())
        pingServer(davFactory.create(root, Credentials(serverUrl.trim(), username.trim(), typed)))
    } catch (e: SyncException) {
        SyncResult(error = e.error, detail = e.message)
    }

    override suspend fun setEnabled(enabled: Boolean) = settings.setEnabled(enabled)

    override suspend fun isEnabled(): Boolean = settings.stored.first().enabled

    override suspend fun syncNow(): SyncResult = mutex.withLock {
        syncing.value = true
        try {
            val result = runSync()
            if (result.isSuccess) settings.recordSuccess(clock()) else settings.recordError(result.error!!, result.detail)
            result
        } finally {
            syncing.value = false
            refreshPending()
        }
    }

    override suspend fun syncIfEnabled(): SyncResult? {
        val s = settings.stored.first()
        return if (s.enabled && s.isConfigured) syncNow() else null
    }

    override suspend fun refreshPending(): Boolean {
        val dirty = hasPendingChanges(lists, settings)
        pending.value = dirty
        return dirty
    }

    override suspend fun forget() {
        mutex.withLock {
            settings.clear()
            pending.value = false
        }
    }

    override suspend fun credentials(): Credentials? {
        val s = settings.stored.first()
        val password = s.sealedPassword?.let(vault::open) ?: return null
        return Credentials(s.serverUrl, s.username, password)
    }

    private suspend fun runSync(): SyncResult {
        val syncer = try {
            syncer() ?: return SyncResult(error = SyncError.NOT_CONFIGURED)
        } catch (e: SyncException) {
            return SyncResult(error = e.error, detail = e.message)
        }
        return syncer.syncAll()
    }

    /** A syncer for the saved account, or null when there is none. Throws [SyncException] for an unusable address or password. */
    private suspend fun syncer(): ListSyncer? {
        val s = settings.stored.first()
        if (!s.isConfigured) return null
        val password = s.sealedPassword?.let(vault::open) ?: throw SyncException(SyncError.CREDENTIALS_UNAVAILABLE)
        val root = NextcloudEndpoint.davRoot(s.serverUrl, s.username)
        val dav = davFactory.create(root, Credentials(s.serverUrl, s.username, password))
        return ListSyncer(dav, lists, settings, clock = clock, tombstoneRetentionMs = retentionMs)
    }

    private suspend fun savedPassword(): String {
        val sealed = settings.stored.first().sealedPassword ?: throw SyncException(SyncError.NOT_CONFIGURED)
        return vault.open(sealed) ?: throw SyncException(SyncError.CREDENTIALS_UNAVAILABLE)
    }
}
