package com.qtekfun.ultimatephone.feature.onboarding

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.core.telecom.RoleController
import com.qtekfun.ultimatephone.data.DataManager
import com.qtekfun.ultimatephone.data.DataSettingsRepository
import com.qtekfun.ultimatephone.data.DownloadPermission
import com.qtekfun.ultimatephone.data.NetworkChecker
import com.qtekfun.ultimatephone.data.NetworkPolicy
import com.qtekfun.ultimatephone.data.NetworkState
import com.qtekfun.ultimatephone.data.PackCatalog
import com.qtekfun.ultimatephone.data.RegionGroup
import com.qtekfun.ultimatephone.data.UpdateScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RolesState(
    val dialerAvailable: Boolean = false,
    val dialerHeld: Boolean = false,
    val screeningAvailable: Boolean = false,
    val screeningHeld: Boolean = false
)

data class OnboardingUiState(
    val flow: OnboardingState = OnboardingState(),
    val roles: RolesState = RolesState(),
    /** Regions of the manifest (packs without a region are left out). */
    val regions: List<RegionGroup> = emptyList(),
    val catalogLoaded: Boolean = false,
    val catalogRefreshing: Boolean = false,
    val hasManifest: Boolean = false,
    val catalogFromCache: Boolean = false,
    val catalogError: String? = null,
    val selectedRegions: Set<String> = emptySet(),
    val network: NetworkState = NetworkState.NONE,
    val wifiOnly: Boolean = true,
    val manufacturer: String = "",
    val guideId: String = "",
    val topics: List<ResolvedTopic> = emptyList()
) {
    val selectedDownloadBytes: Long get() = regions.filter { it.region in selectedRegions }.sumOf { it.pendingDownloadBytes }
    val selectedInstalledBytes: Long get() = regions.filter { it.region in selectedRegions }.sumOf { it.installedBytes }

    /** The download would start at once; otherwise it waits for an unmetered network. */
    val canDownloadNow: Boolean get() = NetworkPolicy.permission(wifiOnly, network) == DownloadPermission.ALLOWED
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val settings: DataSettingsRepository,
    private val manager: DataManager,
    private val roles: RoleController,
    private val regionProvider: RegionProvider,
    private val networkChecker: NetworkChecker,
    private val scheduler: UpdateScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val flow = MutableStateFlow(
        OnboardingFlow.restore(
            saved.get<String>(KEY_STEP),
            saved.get<ArrayList<String>>(KEY_SKIPPED).orEmpty(),
            saved.get<ArrayList<String>>(KEY_COMPLETED).orEmpty(),
            saved.get<Boolean>(KEY_FORM_OPEN) ?: false
        )
    )
    private val roleState = MutableStateFlow(RolesState())
    private val selection = MutableStateFlow<Set<String>?>(null)
    private val network = MutableStateFlow(NetworkState.NONE)
    private val guide = MutableStateFlow(GuideSelection())

    private data class GuideSelection(val manufacturer: String = "", val guideId: String = "", val topics: List<ResolvedTopic> = emptyList())

    private val catalog = combine(manager.catalog, selection, settings.settings) { catalog, chosen, stored ->
        val regions = catalog.manifest?.let { PackCatalog.group(it, catalog.installed).filter { g -> g.region != null } }.orEmpty()
        Triple(catalog, regions, chosen ?: stored.selectedRegions)
    }

    val state: StateFlow<OnboardingUiState> = combine(flow, roleState, catalog, network, guide) { f, r, (cat, regions, chosen), net, g ->
        OnboardingUiState(
            flow = f,
            roles = r,
            regions = regions,
            catalogLoaded = cat.loaded,
            catalogRefreshing = cat.refreshing,
            hasManifest = cat.manifest != null,
            catalogFromCache = cat.fromCache,
            catalogError = cat.refreshError,
            selectedRegions = chosen,
            network = net,
            manufacturer = g.manufacturer,
            guideId = g.guideId,
            topics = g.topics
        )
    }.combine(settings.settings.map { it.wifiOnly }) { ui, wifiOnly -> ui.copy(wifiOnly = wifiOnly) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OnboardingUiState())

    init {
        refreshRoles()
        refreshNetwork()
        viewModelScope.launch { guide.value = loadGuide() }
        viewModelScope.launch {
            manager.load()
            preselectRegion()
            manager.refreshCatalog()
            refreshNetwork()
        }
    }

    // ---- Flow ---------------------------------------------------------------------------------------------------

    fun next() = move(OnboardingFlow::next)

    fun skip() = move(OnboardingFlow::skip)

    fun back() = move(OnboardingFlow::back)

    fun openNextcloudForm() = move(OnboardingFlow::openNextcloudForm)

    fun closeNextcloudForm() = move(OnboardingFlow::closeNextcloudForm)

    private fun move(transition: (OnboardingState) -> OnboardingState) {
        flow.update(transition)
        val now = flow.value
        saved[KEY_STEP] = now.step.name
        saved[KEY_SKIPPED] = ArrayList(now.skipped.map { it.name })
        saved[KEY_COMPLETED] = ArrayList(now.completed.map { it.name })
        saved[KEY_FORM_OPEN] = now.nextcloudFormOpen
        refreshRoles()
        refreshNetwork()
    }

    // ---- Roles --------------------------------------------------------------------------------------------------

    fun refreshRoles() {
        roleState.value = RolesState(
            dialerAvailable = roles.isDialerRoleAvailable(),
            dialerHeld = roles.isDefaultDialer(),
            screeningAvailable = roles.isScreeningRoleAvailable(),
            screeningHeld = roles.isScreeningRoleHeld()
        )
    }

    fun dialerRoleIntent(): Intent? = roles.createDialerRoleIntent()

    fun screeningRoleIntent(): Intent? = roles.createScreeningRoleIntent()

    // ---- Data regions ---------------------------------------------------------------------------------------------

    fun refreshNetwork() {
        network.value = networkChecker.state()
    }

    fun retryCatalog() {
        viewModelScope.launch {
            manager.refreshCatalog()
            refreshNetwork()
        }
    }

    fun toggleRegion(region: String) {
        val current = state.value.selectedRegions
        selection.value = if (region in current) current - region else current + region
    }

    /** The region of the SIM, when the manifest has packs for it, starts selected: the most likely choice. */
    private suspend fun preselectRegion() {
        if (selection.value != null || settings.current().selectedRegions.isNotEmpty()) return
        val region = regionProvider.defaultRegion().uppercase()
        val available = manager.catalog.value.manifest?.packs?.any { it.region.equals(region, ignoreCase = true) } == true
        if (available) selection.value = setOf(region)
    }

    // ---- Battery guide ------------------------------------------------------------------------------------------

    private suspend fun loadGuide(): GuideSelection = withContext(Dispatchers.IO) {
        val file = runCatching { context.assets.open(OemGuides.ASSET).bufferedReader().use { it.readText() } }.getOrNull()
            ?.let(OemGuides::parse) ?: OemGuides.fallback()
        val selected = OemGuides.select(file, Build.MANUFACTURER, Build.BRAND)
        GuideSelection(Build.MANUFACTURER.orEmpty(), selected.id, OemGuides.resolve(file, selected))
    }

    // ---- Finishing ----------------------------------------------------------------------------------------------

    /**
     * Saves the choices and ends the flow. With [download], the selected regions are installed now when the connection
     * allows it, otherwise the daily update installs them as soon as an unmetered network is available.
     */
    fun finish(download: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            val ui = state.value
            val regions = ui.selectedRegions
            settings.update { it.copy(selectedRegions = regions, onboardingCompleted = true) }
            scheduler.apply(settings.current())
            if (download && regions.isNotEmpty()) startDownload(ui, regions)
            flow.update(OnboardingFlow::finish)
            onDone()
        }
    }

    private suspend fun startDownload(ui: OnboardingUiState, regions: Set<String>) {
        if (ui.hasManifest && ui.canDownloadNow) {
            val ids = ui.regions.filter { it.region in regions }.flatMap { g -> g.packs.filter { !it.isInstalled }.map { it.entry.id } }
            manager.install(ids)
        } else {
            scheduler.runSoon(settings.current())
        }
    }

    private companion object {
        const val KEY_STEP = "step"
        const val KEY_SKIPPED = "skipped"
        const val KEY_COMPLETED = "completed"
        const val KEY_FORM_OPEN = "nextcloud_form_open"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
