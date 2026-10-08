package com.qtekfun.ultimatephone.data

import com.qtekfun.ultimatephone.core.datapacks.InstallFailure
import com.qtekfun.ultimatephone.core.datapacks.InstallResult
import com.qtekfun.ultimatephone.core.datapacks.Manifest
import com.qtekfun.ultimatephone.core.datapacks.PackEntry
import com.qtekfun.ultimatephone.core.datapacks.RefreshResult
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRule
import com.qtekfun.ultimatephone.core.spam.sources.PackDatabase
import com.qtekfun.ultimatephone.core.spam.sources.PackEntry as PackRow
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Test files go under the module's build directory, never the system temp directory (which is RAM here). */
fun testDir(name: String): File = File(System.getProperty("user.dir"), "build/test-work/$name").apply {
    deleteRecursively()
    mkdirs()
}

fun entry(
    id: String,
    type: String = PackTypes.BUSINESSES,
    region: String? = id.substringAfterLast('-').uppercase(),
    version: String = "2026.10.01",
    bytes: Long = 1_000_000,
    uncompressed: Long = 4_000_000
) = PackEntry(
    id = id,
    type = type,
    region = region,
    version = version,
    url = "https://example.org/$id.db.xz",
    sha256 = "00",
    bytes = bytes,
    uncompressedBytes = uncompressed
)

fun manifest(vararg packs: PackEntry) = Manifest(schema = 1, generatedAt = "2026-10-01T00:00:00Z", packs = packs.toList())

class FakeDataSettings(initial: DataSettings = DataSettings()) : DataSettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<DataSettings> = state

    override suspend fun current(): DataSettings = state.value

    override suspend fun update(transform: (DataSettings) -> DataSettings) {
        state.value = transform(state.value)
    }
}

class FakeReloader : PackLookupsReloader {
    var reloads = 0

    override suspend fun reload() {
        reloads++
    }
}

class FakeNetwork(var state: NetworkState = NetworkState.UNMETERED) : NetworkChecker {
    override fun state(): NetworkState = state
}

/** An in-memory pack backend: what the "server" has, what is installed, and scripted failures. */
class FakeBackend(var published: Manifest? = null, var cached: Manifest? = null) : PackBackend {
    val installedPacks = mutableMapOf<String, String>()
    var refreshResult: RefreshResult? = null
    var failInstall: InstallFailure? = null
    val installRequests = mutableListOf<String>()
    val removed = mutableListOf<String>()

    override fun cachedManifest(): Manifest? = cached

    override fun refresh(): RefreshResult {
        refreshResult?.let { return it }
        val m = published ?: return RefreshResult.Offline(cached)
        cached = m
        return RefreshResult.Ok(m, fromCache = false)
    }

    override fun installed(): Map<String, String> = installedPacks.toMap()

    override fun install(manifest: Manifest, id: String, onProgress: (Long) -> Unit): InstallResult {
        installRequests += id
        failInstall?.let { return InstallResult.Failed(it) }
        val entry = manifest.find(id) ?: return InstallResult.Failed(InstallFailure.UNSUPPORTED)
        onProgress(entry.bytes)
        installedPacks[id] = entry.version
        return InstallResult.Installed(id, entry.version)
    }

    override fun remove(id: String) {
        installedPacks.remove(id)
        removed += id
    }

    override fun diskUsage(): Long = installedPacks.size * 1000L
}

/** Scripted answers for custom source downloads, in order; the last one repeats. Records every request. */
class FakeSourceFetcher : SourceFetcher {
    sealed interface Reply {
        class Body(val text: String, val etag: String? = null, val lastModified: String? = null) : Reply

        data object NotModified : Reply

        class Http(val status: Int) : Reply

        data object Network : Reply

        data object TooLarge : Reply
    }

    class Request(val url: String, val etag: String?, val lastModified: String?)

    val replies = ArrayDeque<Reply>()
    val requests = mutableListOf<Request>()
    private var last: Reply = Reply.Network

    fun reply(vararg next: Reply) {
        replies.addAll(next)
    }

    override fun fetch(url: String, etag: String?, lastModified: String?, target: File, maxBytes: Long): SourceResponse {
        requests += Request(url, etag, lastModified)
        val reply = replies.removeFirstOrNull() ?: last
        last = reply
        return when (reply) {
            is Reply.Body -> {
                target.writeText(reply.text)
                SourceResponse.Downloaded(reply.etag, reply.lastModified)
            }
            Reply.NotModified -> SourceResponse.NotModified
            is Reply.Http -> throw SourceHttpException(reply.status)
            Reply.Network -> throw IOException("down")
            Reply.TooLarge -> throw SourceTooLargeException()
        }
    }
}

class FakePackDb(private val rows: Map<String, PackRow> = emptyMap(), private val rules: List<PrefixRule> = emptyList()) : PackDatabase {
    var closed = false

    override fun lookup(e164: String): PackRow? = rows[e164]

    override fun prefixRules(): List<PrefixRule> = rules

    override fun close() {
        closed = true
    }
}
