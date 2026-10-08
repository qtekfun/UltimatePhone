package com.qtekfun.ultimatephone.core.spam.lists

import com.qtekfun.ultimatephone.core.spam.decision.OwnList
import com.qtekfun.ultimatephone.core.spam.decision.OwnMatch
import com.qtekfun.ultimatephone.core.spam.decision.Whitelist
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/** What an import changed. */
data class ImportResult(val added: Int, val updated: Int, val unchanged: Int, val badLines: Int)

interface SpamListsRepository {
    /** Live entries (no tombstones), newest first. */
    fun observe(list: ListType): Flow<List<ListEntry>>

    /** Adds an entry, or revives and updates the existing one for the same kind and value. */
    suspend fun add(list: ListType, kind: EntryKind, value: String, label: String? = null, note: String? = null): ListEntry

    /** Soft delete: the entry stays as a tombstone so the deletion syncs. */
    suspend fun remove(list: ListType, id: String): Boolean

    suspend fun isWhitelisted(e164: String): Boolean

    /** Best own-list hit for the number: an exact number first, then the longest prefix. */
    suspend fun matchOwn(e164: String): ListEntry?

    /** Every entry including tombstones, for sync. */
    suspend fun entries(list: ListType): List<ListEntry>

    suspend fun exportJsonLines(list: ListType): String

    /** Merges entries from JSON Lines by id with [EntryMerge]; bad lines are counted and skipped. */
    suspend fun importJsonLines(list: ListType, lines: Sequence<String>): ImportResult
}

class RoomSpamListsRepository(
    private val dao: ListEntryDao,
    private val deviceId: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : SpamListsRepository {
    override fun observe(list: ListType): Flow<List<ListEntry>> = dao.observeLive(list.name).map { rows -> rows.map { it.toEntry() } }

    override suspend fun add(list: ListType, kind: EntryKind, value: String, label: String?, note: String?): ListEntry {
        require(EntryValues.isValid(kind, value)) { "Not a valid ${kind.name.lowercase()}" }
        val existing = dao.find(list.name, kind.name, value)
        val entry = ListEntry(existing?.id ?: newId(), kind, value, label, note, clock(), deviceId, deleted = false)
        dao.upsert(entry.toEntity(list))
        return entry
    }

    override suspend fun remove(list: ListType, id: String): Boolean {
        val row = dao.byIds(listOf(id)).firstOrNull { it.list == list.name && !it.deleted } ?: return false
        dao.upsert(row.copy(deleted = true, updatedAt = clock(), deviceId = deviceId))
        return true
    }

    override suspend fun isWhitelisted(e164: String): Boolean = bestMatch(ListType.WHITELIST, e164) != null

    override suspend fun matchOwn(e164: String): ListEntry? = bestMatch(ListType.OWN, e164)

    override suspend fun entries(list: ListType): List<ListEntry> = dao.all(list.name).map { it.toEntry() }

    override suspend fun exportJsonLines(list: ListType): String = EntryJsonl.encodeAll(entries(list))

    override suspend fun importJsonLines(list: ListType, lines: Sequence<String>): ImportResult {
        val parsed = EntryJsonl.decodeAll(lines)
        // Several lines for one id collapse to the winner first, then against the stored row.
        val incoming = parsed.entries.groupBy { it.id }.mapValues { (_, group) -> group.reduce(EntryMerge::winner) }
        val local = dao.byIds(incoming.keys.toList()).filter { it.list == list.name }.associateBy { it.id }
        var added = 0
        var updated = 0
        var unchanged = 0
        val toWrite = ArrayList<ListEntryEntity>()
        for ((id, remote) in incoming) {
            val mine = local[id]?.toEntry()
            when {
                mine == null -> added++.also { toWrite += remote.toEntity(list) }
                EntryMerge.winner(mine, remote) == remote && remote != mine -> updated++.also { toWrite += remote.toEntity(list) }
                else -> unchanged++
            }
        }
        if (toWrite.isNotEmpty()) dao.upsertAll(toWrite)
        return ImportResult(added, updated, unchanged, parsed.badLines)
    }

    private suspend fun bestMatch(list: ListType, e164: String): ListEntry? {
        val rows = dao.liveMatching(list.name, EntryValues.lookupKeys(e164)).map { it.toEntry() }
        return EntryValues.best(e164, rows)
    }
}

internal fun ListEntryEntity.toEntry() = ListEntry(
    id = id,
    kind = EntryKind.valueOf(kind),
    value = value,
    label = label,
    note = note,
    updatedAt = updatedAt,
    deviceId = deviceId,
    deleted = deleted
)

internal fun ListEntry.toEntity(list: ListType) = ListEntryEntity(
    id = id,
    list = list.name,
    kind = kind.name,
    value = value,
    label = label,
    note = note,
    updatedAt = updatedAt,
    deviceId = deviceId,
    deleted = deleted
)

/**
 * Adapters from the repository to the decision engine's non-suspending lookups. The call screening service runs
 * on a binder thread with a short deadline, so blocking there is intended; callers must not use these on the main
 * thread.
 */
class RepositoryWhitelist(private val repository: SpamListsRepository) : Whitelist {
    override fun isWhitelisted(e164: String): Boolean = runBlocking { repository.isWhitelisted(e164) }
}

class RepositoryOwnList(private val repository: SpamListsRepository) : OwnList {
    override fun match(e164: String): OwnMatch? = runBlocking { repository.matchOwn(e164) }?.let { OwnMatch(it.label, it.id) }
}
