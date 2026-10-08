package com.qtekfun.ultimatephone.feature.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.recording.NumberKey
import com.qtekfun.ultimatephone.core.recording.Readiness
import com.qtekfun.ultimatephone.core.recording.RecordingCapability
import com.qtekfun.ultimatephone.core.recording.RecordingFormat
import com.qtekfun.ultimatephone.core.recording.RecordingMode
import com.qtekfun.ultimatephone.core.recording.RecordingStorage
import com.qtekfun.ultimatephone.recording.MicTestResult
import com.qtekfun.ultimatephone.recording.RecordingConfig
import com.qtekfun.ultimatephone.recording.RecordingEngine
import com.qtekfun.ultimatephone.recording.RecordingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MicTestState {
    data object None : MicTestState

    data object Running : MicTestState

    data class Finished(val result: MicTestResult) : MicTestState
}

data class RecordingSettingsState(
    val config: RecordingConfig = RecordingConfig(),
    val readiness: Readiness = Readiness.DISABLED,
    val folderName: String? = null,
    /** A folder was chosen but the system no longer lets the app use it. */
    val folderLost: Boolean = false,
    /** What this phone can record; null until the first recording checked. */
    val capability: RecordingCapability? = null,
    val hasPermission: Boolean = false,
    val test: MicTestState = MicTestState.None
)

@HiltViewModel
class RecordingSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: RecordingSettings,
    private val storage: RecordingStorage,
    private val engine: RecordingEngine
) : ViewModel() {
    private val refreshTick = MutableStateFlow(0)
    private val testState = MutableStateFlow<MicTestState>(MicTestState.None)

    val state: StateFlow<RecordingSettingsState> = combine(settings.config, refreshTick, testState) { config, _, test ->
        val tree = config.folderUri?.let(Uri::parse)
        // Null: no folder, or one the system no longer lets us use. Empty: usable, but the provider gave no name.
        val folder = withContext(Dispatchers.IO) { if (tree != null && storage.isUsable(tree)) storage.folderName(tree).orEmpty() else null }
        val usable = folder != null
        val permission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val capability = engine.effectiveCapability(config.capability)
        RecordingSettingsState(
            config = config,
            readiness = Readiness.evaluate(config.enabled, config.legalAccepted, usable, permission, capability),
            folderName = folder?.ifEmpty { null },
            folderLost = tree != null && !usable,
            capability = capability,
            hasPermission = permission,
            test = test
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecordingSettingsState())

    /** Re-reads the permission and the folder, which can change outside the app. */
    fun refresh() = refreshTick.update { it + 1 }

    /** Turns recording on after the legal notice was accepted. */
    fun enable() {
        viewModelScope.launch {
            settings.acceptLegal()
            settings.setEnabled(true)
        }
    }

    fun disable() {
        viewModelScope.launch { settings.setEnabled(false) }
    }

    fun folderChosen(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { storage.persist(uri) }
            if (saved) settings.setFolder(uri.toString())
            refresh()
        }
    }

    fun setMode(mode: RecordingMode) = launch { settings.setMode(mode) }

    fun setFormat(format: RecordingFormat) = launch { settings.setFormat(format) }

    fun setAnnounce(value: Boolean) = launch { settings.setAnnounce(value) }

    fun setAutoSpeaker(value: Boolean) = launch { settings.setAutoSpeaker(value) }

    fun setIncludeContactName(value: Boolean) = launch { settings.setIncludeContactName(value) }

    fun recheckCapability() = launch {
        settings.resetCapability()
        settings.setAutoStartBlocked(false)
    }

    /** Adds a number typed by the user to the "selected numbers" list. False when it has no digits. */
    fun addNumber(typed: String): Boolean {
        val key = NumberKey.of(typed) ?: return false
        launch { settings.setSelectedNumbers(state.value.config.selectedNumbers + key) }
        return true
    }

    fun removeNumber(key: String) = launch { settings.setSelectedNumbers(state.value.config.selectedNumbers - key) }

    fun runTest() {
        if (testState.value == MicTestState.Running) return
        testState.value = MicTestState.Running
        viewModelScope.launch { testState.value = MicTestState.Finished(engine.runMicTest()) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
