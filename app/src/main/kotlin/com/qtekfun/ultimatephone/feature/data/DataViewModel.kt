package com.qtekfun.ultimatephone.feature.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.sources.SourceFormat
import com.qtekfun.ultimatephone.data.DataManager
import com.qtekfun.ultimatephone.data.DataSettings
import com.qtekfun.ultimatephone.data.DataSettingsRepository
import com.qtekfun.ultimatephone.data.DownloadPermission
import com.qtekfun.ultimatephone.data.NetworkChecker
import com.qtekfun.ultimatephone.data.NetworkPolicy
import com.qtekfun.ultimatephone.data.PackCatalog
import com.qtekfun.ultimatephone.data.PackProgress
import com.qtekfun.ultimatephone.data.RegionGroup
import com.qtekfun.ultimatephone.data.SourceTestResult
import com.qtekfun.ultimatephone.data.UpdateOutcome
import com.qtekfun.ultimatephone.data.UpdateScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DataUiState(
    val loaded: Boolean = false,
    val groups: List<RegionGroup> = emptyList(),
    val hasManifest: Boolean = false,
    /** The packs on screen come from the saved copy of the manifest. */
    val fromCache: Boolean = false,
    val refreshing: Boolean = false,
    val refreshError: String? = null,
    val progress: Map<String, PackProgress> = emptyMap(),
    val settings: DataSettings = DataSettings(),
    val installedCount: Int = 0,
    val spaceUsedBytes: Long = 0,
    val updating: Boolean = false,
    /** Ids waiting for the user's answer to "use mobile data?". */
    val confirmMetered: List<String>? = null,
    val confirmMeteredBytes: Long = 0
)

/** One-off messages for a snackbar. */
sealed interface DataMessage {
    data object UpToDate : DataMessage

    data class UpdateFailed(val error: String) : DataMessage

    data object NoNetwork : DataMessage

    data object WifiOnlyBlocked : DataMessage
}

/** State of the "add source" dialog's test button. */
sealed interface SourceTestState {
    data object Idle : SourceTestState

    data object Running : SourceTestState

    data class Done(val result: SourceTestResult) : SourceTestState
}

@HiltViewModel
class DataViewModel @Inject constructor(
    private val manager: DataManager,
    private val settingsRepository: DataSettingsRepository,
    private val scheduler: UpdateScheduler,
    private val network: NetworkChecker
) : ViewModel() {
    private val metered = MutableStateFlow<List<String>?>(null)
    private val space = MutableStateFlow(0L)
    private val messageChannel = Channel<DataMessage>(Channel.BUFFERED)
    private val testState = MutableStateFlow<SourceTestState>(SourceTestState.Idle)

    val messages: Flow<DataMessage> = messageChannel.receiveAsFlow()
    val sourceTest: StateFlow<SourceTestState> = testState

    private val base = combine(manager.catalog, manager.progress, settingsRepository.settings, manager.updating) { catalog, progress, settings, updating ->
        val groups = catalog.manifest?.let { PackCatalog.group(it, catalog.installed) }.orEmpty()
        DataUiState(
            loaded = catalog.loaded,
            groups = groups,
            hasManifest = catalog.manifest != null,
            fromCache = catalog.fromCache,
            refreshing = catalog.refreshing,
            refreshError = catalog.refreshError,
            progress = progress,
            settings = settings,
            installedCount = catalog.installed.size,
            updating = updating
        )
    }

    val state: StateFlow<DataUiState> = combine(base, metered, space) { ui, ask, used ->
        val bytes = ask?.let { ids -> ui.groups.flatMap { it.packs }.filter { it.entry.id in ids }.sumOf { it.downloadBytes } } ?: 0L
        ui.copy(spaceUsedBytes = used, confirmMetered = ask, confirmMeteredBytes = bytes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DataUiState())

    init {
        viewModelScope.launch {
            manager.load()
            manager.refreshCatalog()
            refreshSpace()
        }
        viewModelScope.launch { manager.catalog.collect { refreshSpace() } }
    }

    private suspend fun refreshSpace() {
        space.value = manager.diskUsage()
    }

    fun retryCatalog() {
        viewModelScope.launch { manager.refreshCatalog() }
    }

    // ---- Packs -------------------------------------------------------------------------------------------------

    /** Installs [ids], first asking when the connection is metered and "Wi-Fi only" is on. */
    fun install(ids: List<String>) {
        viewModelScope.launch {
            when (NetworkPolicy.permission(settingsRepository.current().wifiOnly, network.state())) {
                DownloadPermission.ALLOWED -> manager.install(ids)
                DownloadPermission.NO_NETWORK -> messageChannel.trySend(DataMessage.NoNetwork)
                DownloadPermission.BLOCKED_BY_WIFI_ONLY -> metered.value = ids
            }
        }
    }

    fun confirmMetered() {
        metered.value?.let { manager.install(it) }
        metered.value = null
    }

    fun dismissMetered() {
        metered.value = null
    }

    fun cancel(id: String) = manager.cancel(id)

    fun dismissFailure(id: String) = manager.dismissFailure(id)

    fun remove(ids: List<String>) = manager.remove(ids)

    fun deleteAll() = manager.removeAll()

    fun updateNow() {
        viewModelScope.launch {
            val message = when (val outcome = manager.updateAll()) {
                is UpdateOutcome.Success -> DataMessage.UpToDate
                is UpdateOutcome.Retry -> DataMessage.UpdateFailed(outcome.error)
                is UpdateOutcome.Failed -> DataMessage.UpdateFailed(outcome.error)
                is UpdateOutcome.Blocked ->
                    if (outcome.permission == DownloadPermission.NO_NETWORK) DataMessage.NoNetwork else DataMessage.WifiOnlyBlocked
            }
            messageChannel.trySend(message)
            refreshSpace()
        }
    }

    // ---- Settings ----------------------------------------------------------------------------------------------

    fun setWifiOnly(value: Boolean) = changeSettings { it.copy(wifiOnly = value) }

    fun setAutoUpdate(value: Boolean) = changeSettings { it.copy(autoUpdate = value) }

    private fun changeSettings(transform: (DataSettings) -> DataSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            scheduler.apply(settingsRepository.current())
        }
    }

    // ---- Custom sources ----------------------------------------------------------------------------------------

    fun addSource(name: String, url: String, format: SourceFormat, level: SpamLevel) {
        viewModelScope.launch {
            testState.value = SourceTestState.Idle
            manager.addSource(name, url, format, level)
            refreshSpace()
        }
    }

    fun testSource(url: String, format: SourceFormat) {
        viewModelScope.launch {
            testState.value = SourceTestState.Running
            testState.value = SourceTestState.Done(manager.testSource(url, format))
        }
    }

    fun resetSourceTest() {
        testState.update { SourceTestState.Idle }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { manager.setSourceEnabled(id, enabled) }
    }

    fun refreshSource(id: String) {
        viewModelScope.launch {
            manager.refreshSource(id)
            refreshSpace()
        }
    }

    fun removeSource(id: String) {
        viewModelScope.launch {
            manager.removeSource(id)
            refreshSpace()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
