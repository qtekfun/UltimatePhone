package com.qtekfun.ultimatephone.core.sync

import com.qtekfun.ultimatephone.core.spam.lists.ListEntry
import com.qtekfun.ultimatephone.core.spam.lists.ListType

/**
 * In-memory WebDAV server with the semantics sync relies on: ETags change on every write, `If-Match` and
 * `If-None-Match: *` are enforced, a missing folder makes a PUT fail like Nextcloud's 409.
 */
class FakeDavServer : WebDav {
    private class Stored(var text: String, var etag: String)

    private val files = HashMap<String, Stored>()
    private val folders = HashSet<String>()
    private var version = 0

    /** Called before every PUT; lets a test make another device write in between. */
    var beforePut: (suspend (String) -> Unit)? = null
    var failWith: SyncException? = null
    var omitEtags = false
    val requests = ArrayList<String>()

    fun text(path: String): String? = files[path]?.text

    fun plant(path: String, text: String) {
        folders += path.substringBeforeLast('/')
        files[path] = Stored(text, nextEtag())
    }

    private fun nextEtag() = "\"v${++version}\""

    private fun check() {
        failWith?.let { throw it }
    }

    override suspend fun stat(path: String): DavStat? {
        requests += "PROPFIND $path"
        check()
        return when {
            path in folders -> DavStat(true, null)
            path.isEmpty() -> DavStat(true, null)
            else -> files[path]?.let { DavStat(false, if (omitEtags) null else it.etag) }
        }
    }

    override suspend fun mkcol(path: String) {
        requests += "MKCOL $path"
        check()
        folders += path
    }

    override suspend fun get(path: String, ifNoneMatch: String?): DavGet {
        requests += "GET $path"
        check()
        val f = files[path] ?: return DavGet.NotFound
        if (ifNoneMatch != null && ifNoneMatch == f.etag) return DavGet.NotModified
        return DavGet.Content(f.text, if (omitEtags) null else f.etag)
    }

    override suspend fun put(path: String, text: String, precondition: Precondition): DavPut {
        requests += "PUT $path"
        check()
        beforePut?.invoke(path)
        if (path.substringBeforeLast('/') !in folders) throw SyncException(SyncError.UNKNOWN, "HTTP 409")
        val current = files[path]
        val ok = when (precondition) {
            Precondition.MustNotExist -> current == null
            is Precondition.Etag -> current != null && current.etag == precondition.etag
        }
        if (!ok) return DavPut.PreconditionFailed
        val etag = nextEtag()
        files[path] = Stored(text, etag)
        return DavPut.Stored(etag)
    }
}

/** One device's lists. [apply] keeps a row that changed after the sync read it, like the Room implementation. */
class FakeLocal : LocalLists {
    val rows = HashMap<ListType, MutableMap<String, ListEntry>>()
    var beforeApply: (() -> Unit)? = null

    private fun table(list: ListType) = rows.getOrPut(list) { HashMap() }

    fun put(list: ListType, entry: ListEntry) {
        table(list)[entry.id] = entry
    }

    fun live(list: ListType): Set<String> = table(list).values.filter { !it.deleted }.map { it.value }.toSet()

    override suspend fun entries(list: ListType): List<ListEntry> = table(list).values.toList()

    override suspend fun apply(list: ListType, upserts: List<ListEntry>, deleteTombstones: List<String>) {
        beforeApply?.invoke()
        val t = table(list)
        for (u in upserts) {
            val current = t[u.id]
            if (current == null || ListMerge.winner(current, u) == u) t[u.id] = u
        }
        for (id in deleteTombstones) if (t[id]?.deleted == true) t.remove(id)
    }
}

class FakeState : SyncStateStore {
    private val etags = HashMap<String, String?>()
    private val prints = HashMap<ListType, String>()

    override suspend fun etag(file: String) = etags[file]

    override suspend fun setEtag(file: String, etag: String?) {
        etags[file] = etag
    }

    override suspend fun syncedFingerprint(list: ListType) = prints[list]

    override suspend fun setSyncedFingerprint(list: ListType, fingerprint: String) {
        prints[list] = fingerprint
    }
}

/** A phone: local lists, state, and a syncer pointed at a shared server. */
class Device(val name: String, server: WebDav, var now: Long = 1_000_000L, attempts: Int = ListSyncer.DEFAULT_ATTEMPTS) {
    val local = FakeLocal()
    val state = FakeState()
    val pauses = ArrayList<Long>()
    val syncer = ListSyncer(server, local, state, clock = { now }, maxAttempts = attempts, pause = { pauses += it })
    private var counter = 0

    fun add(list: ListType, value: String, label: String? = null): ListEntry {
        val existing = local.rows[list]?.values?.firstOrNull { it.value == value }
        val e =
            ListEntry(existing?.id ?: "$name-${counter++}", com.qtekfun.ultimatephone.core.spam.lists.EntryKind.NUMBER, value, label, null, ++now, name, false)
        local.put(list, e)
        return e
    }

    fun remove(list: ListType, value: String) {
        val e = local.rows[list]!!.values.first { it.value == value && !it.deleted }
        local.put(list, e.copy(deleted = true, updatedAt = ++now, deviceId = name))
    }

    suspend fun sync() = syncer.syncAll()
}
