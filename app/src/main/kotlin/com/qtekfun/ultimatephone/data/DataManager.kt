package com.qtekfun.ultimatephone.data

import com.qtekfun.ultimatephone.core.datapacks.InstallResult
import com.qtekfun.ultimatephone.core.datapacks.Manifest
import com.qtekfun.ultimatephone.core.datapacks.PackEntry
import com.qtekfun.ultimatephone.core.datapacks.RefreshResult
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.sources.SourceFormat
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The verified manifest and what is installed, as the screens show them. */
data class CatalogState(
    val manifest: Manifest? = null,
    val installed: Map<String, String> = emptyMap(),
    /** True while the manifest on screen is the cached one, not one fetched in this session. */
    val fromCache: Boolean = true,
    val refreshing: Boolean = false,
    /** A code from [UpdateErrors] when the last refresh failed. */
    val refreshError: String? = null,
    /** False until the cached manifest and the installed packs have been read. */
    val loaded: Boolean = false
)

/** Progress of one pack that is being installed. */
sealed interface PackProgress {
    data object Queued : PackProgress

    data class Downloading(val doneBytes: Long, val totalBytes: Long) : PackProgress {
        val fraction: Float get() = if (totalBytes <= 0) 0f else (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    data class Failed(val error: String) : PackProgress
}

sealed interface UpdateOutcome {
    /** Everything is current. [changed] is true when a pack or a source actually changed. */
    data class Success(val changed: Boolean) : UpdateOutcome

    /** Something failed in a way that may go away: the scheduler should try again later. */
    data class Retry(val error: String) : UpdateOutcome

    /** Something failed for good (bad signature, damaged pack): trying again will not help until the data changes. */
    data class Failed(val error: String) : UpdateOutcome

    /** The connection does not allow a download right now. */
    data class Blocked(val permission: DownloadPermission) : UpdateOutcome
}

/**
 * Everything the data feature does: shows the catalog, installs and removes packs, runs updates (scheduled or on demand)
 * and manages custom sources. One update or install runs at a time. Work lives in [scope], so it carries on when the screen
 * that started it goes away, and it can be cancelled per pack.
 */
@Suppress("TooManyFunctions") // One facade for the whole feature; every function is a small step of one workflow.
class DataManager(
    private val backend: PackBackend,
    private val settings: DataSettingsRepository,
    private val reloader: PackLookupsReloader,
    private val sources: CustomSourceUpdater,
    private val network: NetworkChecker,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val lock = Mutex()
    private val jobs = HashMap<String, Job>()
    private val catalogState = MutableStateFlow(CatalogState())
    private val progressState = MutableStateFlow<Map<String, PackProgress>>(emptyMap())
    private val updatingState = MutableStateFlow(false)

    val catalog: StateFlow<CatalogState> = catalogState.asStateFlow()
    val progress: StateFlow<Map<String, PackProgress>> = progressState.asStateFlow()
    val updating: StateFlow<Boolean> = updatingState.asStateFlow()

    /** Reads the cached manifest and the installed packs. Cheap; safe to call from every screen. */
    suspend fun load() {
        if (catalogState.value.loaded) return
        val (manifest, installed) = withContext(io) { backend.cachedManifest() to backend.installed() }
        catalogState.update { it.copy(manifest = it.manifest ?: manifest, installed = installed, loaded = true) }
    }

    /**
     * Fetches the manifest and verifies its signature. Allowed on any connection: it is a few kilobytes.
     * @return null on success, otherwise an [UpdateErrors] code. The previous manifest stays on screen on failure.
     */
    suspend fun refreshCatalog(): String? {
        load()
        catalogState.update { it.copy(refreshing = true) }
        val result = withContext(io) { backend.refresh() }
        val error = when (result) {
            is RefreshResult.Ok -> {
                catalogState.update { it.copy(manifest = result.manifest, fromCache = false, refreshError = null) }
                settings.update { it.copy(lastManifestRefreshMillis = clock()) }
                null
            }
            is RefreshResult.Offline -> UpdateErrors.NETWORK
            RefreshResult.InvalidSignature -> UpdateErrors.SIGNATURE
            is RefreshResult.Malformed -> UpdateErrors.MANIFEST
        }
        catalogState.update { it.copy(refreshing = false, refreshError = error) }
        return error
    }

    // ---- Installing and removing -------------------------------------------------------------------------------

    /** Starts installing [ids] (and what they depend on). Already running ids are left alone. */
    fun install(ids: Collection<String>) {
        for (id in ids) {
            if (jobs.containsKey(id)) continue
            progressState.update { it + (id to PackProgress.Queued) }
            jobs[id] = scope.launch {
                try {
                    installOne(id)
                } finally {
                    jobs.remove(id)
                }
            }
        }
    }

    /** Stops a queued or running install; the previous version of the pack, if any, stays in place. */
    fun cancel(id: String) {
        jobs[id]?.cancel()
        progressState.update { it - id }
    }

    fun cancelAll() = jobs.keys.toList().forEach(::cancel)

    fun dismissFailure(id: String) {
        progressState.update { if (it[id] is PackProgress.Failed) it - id else it }
    }

    fun remove(ids: Collection<String>) {
        ids.forEach { cancel(it) }
        scope.launch {
            lock.withLock {
                withContext(io) { ids.forEach(backend::remove) }
                val installed = withContext(io) { backend.installed() }
                catalogState.update { it.copy(installed = installed) }
                settings.update { s -> s.copy(excludedPacks = s.excludedPacks + ids, selectedRegions = regionsWithPacks(s.selectedRegions, installed)) }
            }
            reloader.reload()
        }
    }

    /** Removes every pack and every custom source file: the "delete downloaded data" action. */
    fun removeAll() {
        val ids = catalogState.value.installed.keys
        remove(ids)
    }

    private fun regionsWithPacks(selected: Set<String>, installed: Map<String, String>): Set<String> {
        val manifest = catalogState.value.manifest ?: return selected
        val live = manifest.packs.filter { it.id in installed }.mapNotNull { it.region?.uppercase() }.toSet()
        return selected.filter { it in live }.toSet()
    }

    private suspend fun installOne(id: String) {
        try {
            lock.withLock {
                val manifest = catalogState.value.manifest
                val entry = manifest?.find(id)
                if (manifest == null || entry == null) {
                    progressState.update { it + (id to PackProgress.Failed(UpdateErrors.install("UNSUPPORTED"))) }
                    return
                }
                val result = download(manifest, entry)
                finishInstall(id, entry, result)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            progressState.update { it - id }
            throw e
        }
    }

    private suspend fun finishInstall(id: String, entry: PackEntry, result: InstallResult) {
        when (result) {
            is InstallResult.Installed -> {
                progressState.update { it - id }
                val installed = withContext(io) { backend.installed() }
                catalogState.update { it.copy(installed = installed) }
                settings.update { s ->
                    s.copy(
                        selectedRegions = entry.region?.let { s.selectedRegions + it.uppercase() } ?: s.selectedRegions,
                        excludedPacks = s.excludedPacks - id
                    )
                }
                reloader.reload()
            }
            is InstallResult.Failed -> progressState.update { it + (id to PackProgress.Failed(UpdateErrors.install(result.reason.name))) }
        }
    }

    /** Runs the blocking install on the IO dispatcher; cancelling the coroutine stops the download at the next chunk. */
    private suspend fun download(manifest: Manifest, entry: PackEntry): InstallResult = withContext(io) {
        val job = coroutineContext.job
        backend.install(manifest, entry.id) { bytes ->
            job.ensureActive()
            progressState.update { it + (entry.id to PackProgress.Downloading(bytes, entry.bytes)) }
        }
    }

    // ---- Updating ---------------------------------------------------------------------------------------------

    /**
     * Refreshes the manifest, installs what is new in the selected regions, updates installed packs whose version changed
     * and refreshes the custom sources. Used by the daily worker and by "update now".
     */
    suspend fun updateAll(): UpdateOutcome {
        val permission = NetworkPolicy.permission(settings.current().wifiOnly, network.state())
        if (permission != DownloadPermission.ALLOWED) return UpdateOutcome.Blocked(permission)
        updatingState.value = true
        try {
            return lock.withLock { runUpdate() }
        } finally {
            updatingState.value = false
        }
    }

    /** What an update run found out so far. */
    private class Report {
        var changed = false
        var retry = false
        val errors = ArrayList<String>()

        fun fail(code: String) {
            errors += code
            retry = retry || UpdateErrors.isTransient(code)
        }

        fun outcome(): UpdateOutcome = when {
            errors.isEmpty() -> UpdateOutcome.Success(changed)
            retry -> UpdateOutcome.Retry(errors.first())
            else -> UpdateOutcome.Failed(errors.first())
        }
    }

    private suspend fun runUpdate(): UpdateOutcome {
        load()
        val report = Report()
        refreshManifestStep(report)
        // Without a trustworthy, reachable manifest nothing is installed; custom sources do not depend on it.
        if (report.errors.isEmpty()) installStep(report)
        sourcesStep(report)
        if (report.changed) reloader.reload()

        val now = clock()
        settings.update {
            it.copy(
                lastError = report.errors.firstOrNull(),
                lastSuccessfulUpdateMillis = if (report.errors.isEmpty()) now else it.lastSuccessfulUpdateMillis
            )
        }
        return report.outcome()
    }

    private suspend fun refreshManifestStep(report: Report) {
        when (val refreshed = withContext(io) { backend.refresh() }) {
            is RefreshResult.Ok -> {
                catalogState.update { it.copy(manifest = refreshed.manifest, fromCache = false, refreshError = null) }
                settings.update { it.copy(lastManifestRefreshMillis = clock()) }
            }
            is RefreshResult.Offline -> report.fail(UpdateErrors.NETWORK)
            RefreshResult.InvalidSignature -> report.fail(UpdateErrors.SIGNATURE)
            is RefreshResult.Malformed -> report.fail(UpdateErrors.MANIFEST)
        }
    }

    private suspend fun installStep(report: Report) {
        val manifest = catalogState.value.manifest ?: return
        val current = settings.current()
        val installed = withContext(io) { backend.installed() }
        for (entry in UpdatePlanner.plan(manifest, installed, current.selectedRegions, current.excludedPacks).all) {
            when (val result = download(manifest, entry)) {
                is InstallResult.Installed -> {
                    progressState.update { it - entry.id }
                    report.changed = true
                }
                is InstallResult.Failed -> report.fail(UpdateErrors.install(result.reason.name))
            }
        }
        val after = withContext(io) { backend.installed() }
        catalogState.update { it.copy(installed = after) }
    }

    private suspend fun sourcesStep(report: Report) {
        for (source in UpdatePlanner.sourcesToUpdate(settings.current())) {
            val updated = sources.update(source)
            if (updated.contentSha256 != source.contentSha256 || updated.entries != source.entries) report.changed = true
            updated.lastError?.let(report::fail)
            settings.update { s -> s.copy(customSources = s.customSources.map { if (it.id == updated.id) mergeUpdate(it, updated) else it }) }
        }
    }

    /** Takes the bookkeeping of an update but keeps the user's edits (name, level, enabled) made while it ran. */
    private fun mergeUpdate(current: CustomSource, updated: CustomSource) = current.copy(
        etag = updated.etag,
        lastModified = updated.lastModified,
        contentSha256 = updated.contentSha256,
        lastUpdateMillis = updated.lastUpdateMillis,
        lastError = updated.lastError,
        entries = updated.entries
    )

    // ---- Custom sources ---------------------------------------------------------------------------------------

    /** Adds a source and downloads it once. @return the stored source, or null when [url] is not an https URL. */
    suspend fun addSource(name: String, url: String, format: SourceFormat, level: SpamLevel): CustomSource? {
        val cleaned = url.trim()
        if (!SourceUrls.isValid(cleaned)) return null
        val source = CustomSource(
            id = UUID.randomUUID().toString().take(ID_LENGTH),
            name = name.trim().ifEmpty { cleaned.substringAfter("://").substringBefore('/') },
            url = cleaned,
            format = format,
            level = level
        )
        settings.update { it.copy(customSources = it.customSources + source) }
        return refreshSource(source.id)
    }

    /** Downloads one source now, whatever the schedule says. */
    suspend fun refreshSource(id: String): CustomSource? {
        val source = settings.current().customSources.firstOrNull { it.id == id } ?: return null
        val updated = lock.withLock { sources.update(source) }
        settings.update { s -> s.copy(customSources = s.customSources.map { if (it.id == id) mergeUpdate(it, updated) else it }) }
        reloader.reload()
        return updated
    }

    suspend fun setSourceEnabled(id: String, enabled: Boolean) {
        settings.update { s -> s.copy(customSources = s.customSources.map { if (it.id == id) it.copy(enabled = enabled) else it }) }
        reloader.reload()
    }

    suspend fun removeSource(id: String) {
        val source = settings.current().customSources.firstOrNull { it.id == id } ?: return
        settings.update { s -> s.copy(customSources = s.customSources.filterNot { it.id == id }) }
        withContext(io) { sources.deletePack(source) }
        reloader.reload()
    }

    suspend fun testSource(url: String, format: SourceFormat): SourceTestResult = sources.test(url.trim(), format)

    suspend fun diskUsage(): Long = withContext(io) { backend.diskUsage() }

    private companion object {
        const val ID_LENGTH = 8
    }
}
