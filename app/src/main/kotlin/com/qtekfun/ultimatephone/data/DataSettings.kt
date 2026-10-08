package com.qtekfun.ultimatephone.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.sources.SourceFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A spam source added by the user by URL. The numbers it lists live in a local pack file (see [NumberPackFile]);
 * the fields from [etag] on are the bookkeeping of the last update.
 *
 * @property lastError a code from [UpdateErrors], shown to the user as text.
 */
@Serializable
data class CustomSource(
    val id: String,
    val name: String,
    val url: String,
    val format: SourceFormat = SourceFormat.TXT,
    val level: SpamLevel = SpamLevel.COMMUNITY,
    val enabled: Boolean = true,
    val etag: String? = null,
    val lastModified: String? = null,
    val contentSha256: String? = null,
    val lastUpdateMillis: Long? = null,
    val lastError: String? = null,
    val entries: Int = 0
) {
    /** Id of the source in decisions and of its pack file. */
    val packId: String get() = "custom-$id"
}

/** Everything the data feature remembers, kept apart from the general app settings. */
data class DataSettings(
    /** ISO regions (upper case) whose packs are kept installed and updated. */
    val selectedRegions: Set<String> = emptySet(),
    /** Packs the user removed on purpose; automatic updates do not bring them back. */
    val excludedPacks: Set<String> = emptySet(),
    val wifiOnly: Boolean = true,
    val autoUpdate: Boolean = true,
    val customSources: List<CustomSource> = emptyList(),
    val lastManifestRefreshMillis: Long? = null,
    val lastSuccessfulUpdateMillis: Long? = null,
    val lastError: String? = null,
    val onboardingCompleted: Boolean = false
)

/** Pure translation between [DataSettings] and the DataStore preferences, so it can be tested without Android. */
object DataSettingsMapper {
    private val REGIONS = stringSetPreferencesKey("selected_regions")
    private val EXCLUDED = stringSetPreferencesKey("excluded_packs")
    private val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    private val AUTO_UPDATE = booleanPreferencesKey("auto_update")
    private val SOURCES = stringPreferencesKey("custom_sources")
    private val MANIFEST_REFRESH = longPreferencesKey("last_manifest_refresh")
    private val LAST_SUCCESS = longPreferencesKey("last_successful_update")
    private val LAST_ERROR = stringPreferencesKey("last_error")
    private val ONBOARDING = booleanPreferencesKey("onboarding_completed")

    private val json = Json { ignoreUnknownKeys = true }
    private val sourcesSerializer = ListSerializer(CustomSource.serializer())

    fun read(prefs: Preferences) = DataSettings(
        selectedRegions = prefs[REGIONS].orEmpty().map { it.uppercase() }.toSet(),
        excludedPacks = prefs[EXCLUDED].orEmpty(),
        wifiOnly = prefs[WIFI_ONLY] ?: true,
        autoUpdate = prefs[AUTO_UPDATE] ?: true,
        customSources = decodeSources(prefs[SOURCES]),
        lastManifestRefreshMillis = prefs[MANIFEST_REFRESH],
        lastSuccessfulUpdateMillis = prefs[LAST_SUCCESS],
        lastError = prefs[LAST_ERROR],
        onboardingCompleted = prefs[ONBOARDING] ?: false
    )

    fun write(prefs: MutablePreferences, settings: DataSettings) {
        prefs[REGIONS] = settings.selectedRegions.map { it.uppercase() }.toSet()
        prefs[EXCLUDED] = settings.excludedPacks
        prefs[WIFI_ONLY] = settings.wifiOnly
        prefs[AUTO_UPDATE] = settings.autoUpdate
        prefs[SOURCES] = json.encodeToString(sourcesSerializer, settings.customSources)
        prefs.putOrRemove(MANIFEST_REFRESH, settings.lastManifestRefreshMillis)
        prefs.putOrRemove(LAST_SUCCESS, settings.lastSuccessfulUpdateMillis)
        prefs.putOrRemove(LAST_ERROR, settings.lastError)
        prefs[ONBOARDING] = settings.onboardingCompleted
    }

    /** A damaged value must never take the settings screen down: it reads as "no sources". */
    fun decodeSources(text: String?): List<CustomSource> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(sourcesSerializer, text)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun <T : Any> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }
}

/** The data feature's own settings store. Independent from the general settings. */
interface DataSettingsRepository {
    val settings: Flow<DataSettings>

    suspend fun current(): DataSettings

    /** Applies [transform] to the stored settings atomically. */
    suspend fun update(transform: (DataSettings) -> DataSettings)
}

private val Context.dataSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "data_settings")

class DataStoreDataSettingsRepository(context: Context) : DataSettingsRepository {
    private val store = context.applicationContext.dataSettingsStore

    override val settings: Flow<DataSettings> = store.data.map(DataSettingsMapper::read)

    override suspend fun current(): DataSettings = settings.first()

    override suspend fun update(transform: (DataSettings) -> DataSettings) {
        store.edit { prefs -> DataSettingsMapper.write(prefs, transform(DataSettingsMapper.read(prefs))) }
    }
}
