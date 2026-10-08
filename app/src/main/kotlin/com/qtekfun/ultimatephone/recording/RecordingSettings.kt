package com.qtekfun.ultimatephone.recording

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qtekfun.ultimatephone.core.recording.CaptureSource
import com.qtekfun.ultimatephone.core.recording.RecordingCapability
import com.qtekfun.ultimatephone.core.recording.RecordingFormat
import com.qtekfun.ultimatephone.core.recording.RecordingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Everything the user chose for call recording, plus what the app learned about this phone.
 *
 * @property capability what recording can do on this phone; null until the first recording detected it (or after a system
 * update, which can change it).
 * @property lineSource the call-line source that worked, when [capability] is line capture.
 * @property autoStartBlocked an automatic start from the background was refused by the system.
 */
data class RecordingConfig(
    val enabled: Boolean = false,
    val legalAccepted: Boolean = false,
    val mode: RecordingMode = RecordingMode.OFF,
    val folderUri: String? = null,
    val format: RecordingFormat = RecordingFormat.M4A_AAC,
    val announce: Boolean = false,
    val autoSpeaker: Boolean = true,
    val includeContactName: Boolean = false,
    val selectedNumbers: Set<String> = emptySet(),
    val capability: RecordingCapability? = null,
    val lineSource: CaptureSource? = null,
    val silentAttempts: Int = 0,
    val micWarningShown: Boolean = false,
    val autoStartBlocked: Boolean = false
)

@Suppress("TooManyFunctions") // One setter per user setting; splitting the interface would only scatter them.
interface RecordingSettings {
    val config: Flow<RecordingConfig>

    suspend fun current(): RecordingConfig = config.first()

    suspend fun setEnabled(enabled: Boolean)

    suspend fun acceptLegal()

    suspend fun setMode(mode: RecordingMode)

    suspend fun setFolder(uri: String?)

    suspend fun setFormat(format: RecordingFormat)

    suspend fun setAnnounce(value: Boolean)

    suspend fun setAutoSpeaker(value: Boolean)

    suspend fun setIncludeContactName(value: Boolean)

    suspend fun setSelectedNumbers(keys: Set<String>)

    suspend fun saveCapability(capability: RecordingCapability, lineSource: CaptureSource?)

    suspend fun saveSilentAttempts(attempts: Int)

    /** Forgets what was detected, so the next recording checks again. */
    suspend fun resetCapability()

    suspend fun setMicWarningShown()

    suspend fun setAutoStartBlocked(blocked: Boolean)
}

private val Context.recordingStore: DataStore<Preferences> by preferencesDataStore(name = "recording_settings")

class DataStoreRecordingSettings(context: Context, private val build: String = Build.FINGERPRINT) : RecordingSettings {
    private val store = context.applicationContext.recordingStore

    override val config: Flow<RecordingConfig> = store.data.map { p ->
        // The detected capability belongs to one system build: an update can change what the phone allows.
        val sameBuild = p[CAPABILITY_BUILD] == build
        RecordingConfig(
            enabled = p[ENABLED] ?: false,
            legalAccepted = p[LEGAL] ?: false,
            mode = p[MODE]?.let { name -> RecordingMode.entries.firstOrNull { it.name == name } } ?: RecordingMode.OFF,
            folderUri = p[FOLDER],
            format = p[FORMAT]?.let { name -> RecordingFormat.entries.firstOrNull { it.name == name } } ?: RecordingFormat.M4A_AAC,
            announce = p[ANNOUNCE] ?: false,
            autoSpeaker = p[AUTO_SPEAKER] ?: true,
            includeContactName = p[CONTACT_NAME] ?: false,
            selectedNumbers = p[SELECTED].orEmpty(),
            capability = if (sameBuild) p[CAPABILITY]?.let { name -> RecordingCapability.entries.firstOrNull { it.name == name } } else null,
            lineSource = if (sameBuild) p[LINE_SOURCE]?.let { name -> CaptureSource.entries.firstOrNull { it.name == name } } else null,
            silentAttempts = if (sameBuild) p[SILENT] ?: 0 else 0,
            micWarningShown = p[MIC_WARNING] ?: false,
            autoStartBlocked = p[AUTO_BLOCKED] ?: false
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
    }

    override suspend fun acceptLegal() {
        store.edit { it[LEGAL] = true }
    }

    override suspend fun setMode(mode: RecordingMode) {
        store.edit { it[MODE] = mode.name }
    }

    override suspend fun setFolder(uri: String?) {
        store.edit { if (uri == null) it.remove(FOLDER) else it[FOLDER] = uri }
    }

    override suspend fun setFormat(format: RecordingFormat) {
        store.edit { it[FORMAT] = format.name }
    }

    override suspend fun setAnnounce(value: Boolean) {
        store.edit { it[ANNOUNCE] = value }
    }

    override suspend fun setAutoSpeaker(value: Boolean) {
        store.edit { it[AUTO_SPEAKER] = value }
    }

    override suspend fun setIncludeContactName(value: Boolean) {
        store.edit { it[CONTACT_NAME] = value }
    }

    override suspend fun setSelectedNumbers(keys: Set<String>) {
        store.edit { it[SELECTED] = keys }
    }

    override suspend fun saveCapability(capability: RecordingCapability, lineSource: CaptureSource?) {
        store.edit {
            it[CAPABILITY] = capability.name
            it[CAPABILITY_BUILD] = build
            if (lineSource == null) it.remove(LINE_SOURCE) else it[LINE_SOURCE] = lineSource.name
            it[SILENT] = 0
        }
    }

    override suspend fun saveSilentAttempts(attempts: Int) {
        store.edit {
            if (it[CAPABILITY_BUILD] != build) {
                // A capability saved by another system build is stale; do not let a new stamp make it valid again.
                it.remove(CAPABILITY)
                it.remove(LINE_SOURCE)
            }
            it[SILENT] = attempts
            it[CAPABILITY_BUILD] = build
        }
    }

    override suspend fun resetCapability() {
        store.edit {
            it.remove(CAPABILITY)
            it.remove(LINE_SOURCE)
            it.remove(CAPABILITY_BUILD)
            it.remove(SILENT)
        }
    }

    override suspend fun setMicWarningShown() {
        store.edit { it[MIC_WARNING] = true }
    }

    override suspend fun setAutoStartBlocked(blocked: Boolean) {
        store.edit { it[AUTO_BLOCKED] = blocked }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val LEGAL = booleanPreferencesKey("legal_accepted")
        val MODE = stringPreferencesKey("mode")
        val FOLDER = stringPreferencesKey("folder_uri")
        val FORMAT = stringPreferencesKey("format")
        val ANNOUNCE = booleanPreferencesKey("announce")
        val AUTO_SPEAKER = booleanPreferencesKey("auto_speaker")
        val CONTACT_NAME = booleanPreferencesKey("include_contact_name")
        val SELECTED = stringSetPreferencesKey("selected_numbers")
        val CAPABILITY = stringPreferencesKey("capability")
        val CAPABILITY_BUILD = stringPreferencesKey("capability_build")
        val LINE_SOURCE = stringPreferencesKey("line_source")
        val SILENT = intPreferencesKey("silent_attempts")
        val MIC_WARNING = booleanPreferencesKey("mic_warning_shown")
        val AUTO_BLOCKED = booleanPreferencesKey("auto_start_blocked")
    }
}
