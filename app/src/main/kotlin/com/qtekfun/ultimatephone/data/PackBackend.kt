package com.qtekfun.ultimatephone.data

import com.qtekfun.ultimatephone.core.datapacks.DataPackRepository
import com.qtekfun.ultimatephone.core.datapacks.InstallResult
import com.qtekfun.ultimatephone.core.datapacks.Manifest
import com.qtekfun.ultimatephone.core.datapacks.PackStore
import com.qtekfun.ultimatephone.core.datapacks.RefreshResult
import java.io.File

/** What [DataManager] needs from the data pack layer. All calls block; the manager runs them off the main thread. */
interface PackBackend {
    fun cachedManifest(): Manifest?

    fun refresh(): RefreshResult

    fun installed(): Map<String, String>

    fun install(manifest: Manifest, id: String, onProgress: (Long) -> Unit): InstallResult

    fun remove(id: String)

    /** Bytes taken by installed packs and custom source packs. */
    fun diskUsage(): Long
}

class RepositoryPackBackend(private val repository: DataPackRepository, private val store: PackStore, private val packsDir: File) : PackBackend {
    override fun cachedManifest(): Manifest? = repository.cachedManifest()

    override fun refresh(): RefreshResult = repository.refresh()

    override fun installed(): Map<String, String> = repository.installedVersions()

    override fun install(manifest: Manifest, id: String, onProgress: (Long) -> Unit): InstallResult = repository.install(manifest, id, onProgress)

    override fun remove(id: String) = repository.remove(id)

    override fun diskUsage(): Long =
        store.sizeOnDisk() + packsDir.listFiles { f -> f.name.endsWith(CustomSourceUpdater.EXTENSION) }.orEmpty().sumOf { it.length() }
}
