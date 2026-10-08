package com.qtekfun.ultimatephone.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.core.sync.SyncError
import com.qtekfun.ultimatephone.core.sync.SyncStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * What is stored for the Nextcloud account. [sealedPassword] is the app password encrypted with the Android Keystore
 * (see `SecretVault`); the plain password is never kept here.
 */
data class StoredSync(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val username: String = "",
    val sealedPassword: String? = null,
    val lastSyncAt: Long? = null,
    val lastError: SyncError? = null,
    val lastDetail: String? = null
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && username.isNotBlank() && sealedPassword != null
}

/** Sync settings and the engine's bookkeeping, in their own DataStore file (`sync_settings`). */
interface SyncSettings : SyncStateStore {
    val stored: Flow<StoredSync>

    /**
     * Saves the account. A null [sealedPassword] keeps the one already saved. Changing server or user forgets the
     * ETags and fingerprints of the previous account.
     */
    suspend fun saveAccount(serverUrl: String, username: String, sealedPassword: String?)

    suspend fun setEnabled(enabled: Boolean)

    suspend fun recordSuccess(at: Long)

    suspend fun recordError(error: SyncError, detail: String?)

    /** Forgets the account, the password and all sync bookkeeping. */
    suspend fun clear()
}

private val Context.syncStore: DataStore<Preferences> by preferencesDataStore(name = "sync_settings")

class DataStoreSyncSettings(context: Context) : SyncSettings {
    private val store = context.applicationContext.syncStore

    override val stored: Flow<StoredSync> = store.data.map { p ->
        StoredSync(
            enabled = p[ENABLED] ?: false,
            serverUrl = p[SERVER].orEmpty(),
            username = p[USER].orEmpty(),
            sealedPassword = p[SEALED],
            lastSyncAt = p[LAST_SYNC],
            lastError = p[LAST_ERROR]?.let { name -> SyncError.entries.firstOrNull { it.name == name } },
            lastDetail = p[LAST_DETAIL]
        )
    }

    override suspend fun saveAccount(serverUrl: String, username: String, sealedPassword: String?) {
        store.edit { p ->
            if (p[SERVER] != serverUrl || p[USER] != username) clearBookkeeping(p)
            p[SERVER] = serverUrl
            p[USER] = username
            if (sealedPassword != null) p[SEALED] = sealedPassword
        }
    }

    override suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
    }

    override suspend fun recordSuccess(at: Long) {
        store.edit {
            it[LAST_SYNC] = at
            it.remove(LAST_ERROR)
            it.remove(LAST_DETAIL)
        }
    }

    override suspend fun recordError(error: SyncError, detail: String?) {
        store.edit {
            it[LAST_ERROR] = error.name
            if (detail == null) it.remove(LAST_DETAIL) else it[LAST_DETAIL] = detail
        }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    override suspend fun etag(file: String): String? = store.data.first()[stringPreferencesKey("etag_$file")]

    override suspend fun setEtag(file: String, etag: String?) {
        store.edit { if (etag == null) it.remove(stringPreferencesKey("etag_$file")) else it[stringPreferencesKey("etag_$file")] = etag }
    }

    override suspend fun syncedFingerprint(list: ListType): String? = store.data.first()[fingerprintKey(list)]

    override suspend fun setSyncedFingerprint(list: ListType, fingerprint: String) {
        store.edit { it[fingerprintKey(list)] = fingerprint }
    }

    private fun clearBookkeeping(p: androidx.datastore.preferences.core.MutablePreferences) {
        p.asMap().keys.filter { it.name.startsWith("etag_") || it.name.startsWith("fp_") }.forEach { p.remove(it) }
        p.remove(LAST_SYNC)
        p.remove(LAST_ERROR)
        p.remove(LAST_DETAIL)
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val SERVER = stringPreferencesKey("server_url")
        val USER = stringPreferencesKey("username")
        val SEALED = stringPreferencesKey("sealed_password")
        val LAST_SYNC = longPreferencesKey("last_sync_at")
        val LAST_ERROR = stringPreferencesKey("last_error")
        val LAST_DETAIL = stringPreferencesKey("last_detail")

        fun fingerprintKey(list: ListType) = stringPreferencesKey("fp_${list.name.lowercase()}")
    }
}
