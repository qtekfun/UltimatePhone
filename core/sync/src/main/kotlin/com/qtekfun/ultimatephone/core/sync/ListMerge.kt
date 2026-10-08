package com.qtekfun.ultimatephone.core.sync

import com.qtekfun.ultimatephone.core.spam.lists.EntryJsonl
import com.qtekfun.ultimatephone.core.spam.lists.ListEntry
import java.security.MessageDigest

/** Pure merge of two copies of one list. No clock, no I/O: [now] is a parameter so results are reproducible. */
object ListMerge {
    const val DAY_MS = 24L * 60 * 60 * 1000

    /** Default time a tombstone is kept so a deletion can still reach a device that was offline. */
    const val DEFAULT_TOMBSTONE_RETENTION_MS = 180 * DAY_MS

    /**
     * Total order over entries that share an id. The newest `updatedAt` is greatest, then the greater `deviceId`,
     * then a tombstone over a live entry, then the content. Being total it is symmetric, so the result never depends
     * on which side is "local".
     */
    private val order: Comparator<ListEntry> = compareBy<ListEntry> { it.updatedAt }
        .thenBy { it.deviceId }
        .thenBy { it.deleted }
        .thenBy { it.label.orEmpty() }
        .thenBy { it.note.orEmpty() }
        .thenBy { it.kind }
        .thenBy { it.value }

    fun winner(a: ListEntry, b: ListEntry): ListEntry = if (order.compare(a, b) >= 0) a else b

    /**
     * Merges [local] and [remote]:
     * 1. entries are united by `id`, the [winner] keeps each id;
     * 2. when several live ids hold the same kind and value (added independently on two devices) the greatest by
     *    `updatedAt`, `deviceId`, `id` stays and the others become tombstones. A tombstone keeps the stamp of the
     *    entry it replaces and beats it on a tie, so it only ever moves an id up in the per-id order: collapsing
     *    can be repeated in any order on any device and every device ends at the same set;
     * 3. tombstones older than [tombstoneRetentionMs] are dropped.
     *
     * The result is sorted by id: merging is commutative and idempotent, and the same inputs always encode to the
     * same bytes.
     */
    fun merge(
        local: Collection<ListEntry>,
        remote: Collection<ListEntry>,
        now: Long,
        tombstoneRetentionMs: Long = DEFAULT_TOMBSTONE_RETENTION_MS
    ): List<ListEntry> {
        val byId = HashMap<String, ListEntry>(local.size + remote.size)
        for (entry in local) byId.merge(entry.id, entry, ::winner)
        for (entry in remote) byId.merge(entry.id, entry, ::winner)
        collapseDuplicates(byId)
        val cutoff = now - tombstoneRetentionMs
        return byId.values.filter { !(it.deleted && it.updatedAt < cutoff) }.sortedBy { it.id }
    }

    private fun collapseDuplicates(byId: MutableMap<String, ListEntry>) {
        val groups = byId.values.filter { !it.deleted }.groupBy { it.kind to it.value }
        for (group in groups.values) {
            if (group.size < 2) continue
            val keep = group.maxWithOrNull(order.thenBy { it.id })!!
            for (loser in group) {
                if (loser.id != keep.id) {
                    byId[loser.id] = loser.copy(deleted = true)
                }
            }
        }
    }

    /** Canonical text of a list: sorted by id, one JSON line each. Equal lists give equal text. */
    fun canonical(entries: Collection<ListEntry>): String = EntryJsonl.encodeAll(entries.sortedBy { it.id })

    /** Fingerprint of a list, used to tell whether it changed since the last sync without keeping a copy. */
    fun fingerprint(entries: Collection<ListEntry>): String =
        MessageDigest.getInstance("SHA-256").digest(canonical(entries).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
