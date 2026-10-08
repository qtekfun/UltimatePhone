package com.qtekfun.ultimatephone.core.datapacks

import java.io.File
import java.io.IOException

sealed interface RefreshResult {
    /** A fresh, verified manifest (or the unchanged cached one). */
    data class Ok(val manifest: Manifest, val fromCache: Boolean) : RefreshResult

    /** The network failed; the last verified manifest is still usable. */
    data class Offline(val manifest: Manifest?) : RefreshResult

    /** The signature did not match: nothing from this manifest may be used. */
    data object InvalidSignature : RefreshResult

    data class Malformed(val reason: String) : RefreshResult
}

/** Where manifests come from. The default is the latest release of UltimatePhone-data. */
data class DataPackSource(
    val manifestUrl: String = "https://github.com/qtekfun/UltimatePhone-data/releases/latest/download/manifest.json",
    val signatureUrl: String = "$manifestUrl.sig"
)

/**
 * What the app knows about data packs: the verified manifest, what is installed and what can be updated. Every manifest
 * is verified before it is parsed or cached; a cached copy is verified again when read.
 */
class DataPackRepository(
    private val fetcher: HttpFetcher,
    private val verifier: ManifestVerifier,
    private val store: PackStore,
    private val cacheDir: File,
    private val source: DataPackSource = DataPackSource()
) {
    private val installer = PackInstaller(fetcher, store)
    private val manifestFile = File(cacheDir, "manifest.json")
    private val signatureFile = File(cacheDir, "manifest.json.sig")
    private val etagFile = File(cacheDir, "manifest.etag")

    init {
        cacheDir.mkdirs()
    }

    /** The last manifest that passed verification, or null when none has been downloaded yet. */
    fun cachedManifest(): Manifest? {
        if (!manifestFile.exists() || !signatureFile.exists()) return null
        val bytes = manifestFile.readBytes()
        if (!verifier.verify(bytes, signatureFile.readText())) return null
        return try {
            ManifestParser.parse(bytes)
        } catch (_: ManifestException) {
            null
        }
    }

    fun refresh(): RefreshResult {
        val cached = cachedManifest()
        val etag = etagFile.takeIf { it.exists() && cached != null }?.readText()
        val body: ByteArray
        val signature: String
        val newEtag: String?
        try {
            when (val manifestResponse = fetcher.fetch(source.manifestUrl, etag)) {
                Fetched.NotModified -> return cached?.let { RefreshResult.Ok(it, fromCache = true) } ?: RefreshResult.Offline(null)
                is Fetched.Body -> {
                    body = manifestResponse.bytes
                    newEtag = manifestResponse.etag
                }
            }
            val signatureResponse = fetcher.fetch(source.signatureUrl)
            if (signatureResponse !is Fetched.Body) return RefreshResult.Offline(cached)
            signature = signatureResponse.bytes.decodeToString()
        } catch (_: IOException) {
            return RefreshResult.Offline(cached)
        }
        if (!verifier.verify(body, signature)) return RefreshResult.InvalidSignature
        val manifest = try {
            ManifestParser.parse(body)
        } catch (e: ManifestException) {
            return RefreshResult.Malformed(e.message.orEmpty())
        }
        manifestFile.writeBytes(body)
        signatureFile.writeText(signature)
        if (newEtag != null) etagFile.writeText(newEtag) else etagFile.delete()
        return RefreshResult.Ok(manifest, fromCache = false)
    }

    fun installedVersions(): Map<String, String> = store.installed()

    /** Installed packs whose published version differs from the installed one. */
    fun updatesAvailable(manifest: Manifest): List<PackEntry> {
        val installed = store.installed()
        return manifest.packs.filter { entry -> installed[entry.id]?.let { it != entry.version } == true }
    }

    /** Installs [id] and, first, the packs it depends on. Stops at the first failure. */
    fun install(manifest: Manifest, id: String, onProgress: (Long) -> Unit = {}): InstallResult {
        val order = resolveOrder(manifest, id) ?: return InstallResult.Failed(InstallFailure.UNSUPPORTED)
        var last: InstallResult = InstallResult.Failed(InstallFailure.UNSUPPORTED)
        for (entry in order) {
            if (store.versionOf(entry.id) == entry.version && entry.id != id) continue
            last = installer.install(entry, onProgress)
            if (last is InstallResult.Failed) return last
        }
        return last
    }

    fun remove(id: String) = store.remove(id)

    fun fileFor(id: String): File? = store.fileFor(id).takeIf { it.exists() }

    /** Dependencies first, the requested pack last; null if something is missing or the graph has a cycle. */
    internal fun resolveOrder(manifest: Manifest, id: String): List<PackEntry>? {
        val ordered = LinkedHashMap<String, PackEntry>()
        val visiting = mutableSetOf<String>()

        fun visit(current: String): Boolean {
            if (current in ordered) return true
            if (!visiting.add(current)) return false
            val entry = manifest.find(current) ?: return false
            if (!entry.dependsOn.all { visit(it) }) return false
            visiting.remove(current)
            ordered[current] = entry
            return true
        }
        return if (visit(id)) ordered.values.toList() else null
    }
}
