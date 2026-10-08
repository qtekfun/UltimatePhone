package com.qtekfun.ultimatephone.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qtekfun.ultimatephone.core.designsystem.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** User choices that need no database. */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Key of the SIM used by default; null means "ask the system". */
    val defaultSimKey: String? = null,
    /** SIM remembered per number (E.164), set when the user explicitly picks a SIM for it. */
    val simByNumber: Map<String, String> = emptyMap(),
    /** ISO region that overrides the SIM country for numbers without a country code. */
    val regionOverride: String? = null
)

interface SettingsRepository {
    val settings: StateFlow<UserSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDefaultSim(key: String?)

    suspend fun rememberSim(numberE164: String, simKey: String)

    suspend fun setRegionOverride(region: String?)
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(context: Context, scope: CoroutineScope) : SettingsRepository {
    private val store = context.applicationContext.settingsStore

    override val settings: StateFlow<UserSettings> = store.data.map { prefs ->
        UserSettings(
            themeMode = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM,
            defaultSimKey = prefs[DEFAULT_SIM],
            simByNumber = SimMemoryCodec.decode(prefs[SIM_BY_NUMBER].orEmpty()),
            regionOverride = prefs[REGION]
        )
    }.stateIn(scope, SharingStarted.Eagerly, UserSettings())

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    override suspend fun setDefaultSim(key: String?) {
        store.edit { if (key == null) it.remove(DEFAULT_SIM) else it[DEFAULT_SIM] = key }
    }

    override suspend fun rememberSim(numberE164: String, simKey: String) {
        store.edit { prefs ->
            val current = SimMemoryCodec.decode(prefs[SIM_BY_NUMBER].orEmpty())
            prefs[SIM_BY_NUMBER] = SimMemoryCodec.encode(current + (numberE164 to simKey))
        }
    }

    override suspend fun setRegionOverride(region: String?) {
        store.edit { if (region == null) it.remove(REGION) else it[REGION] = region.uppercase() }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val DEFAULT_SIM = stringPreferencesKey("default_sim")
        val SIM_BY_NUMBER = stringPreferencesKey("sim_by_number")
        val REGION = stringPreferencesKey("region_override")
    }
}
