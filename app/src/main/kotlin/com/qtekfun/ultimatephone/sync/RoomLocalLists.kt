package com.qtekfun.ultimatephone.sync

import com.qtekfun.ultimatephone.core.spam.lists.EntryKind
import com.qtekfun.ultimatephone.core.spam.lists.ListEntry
import com.qtekfun.ultimatephone.core.spam.lists.ListEntryDao
import com.qtekfun.ultimatephone.core.spam.lists.ListEntryEntity
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.core.sync.ListMerge
import com.qtekfun.ultimatephone.core.sync.LocalLists

/**
 * The sync engine's view of the Room lists. Writes go straight to the DAO (sync must store the exact ids and stamps it
 * merged), but never over a row that has become newer since the sync read it: that edit uploads on the next sync.
 */
class RoomLocalLists(private val dao: ListEntryDao) : LocalLists {
    override suspend fun entries(list: ListType): List<ListEntry> = dao.all(list.name).map { it.toListEntry() }

    override suspend fun apply(list: ListType, upserts: List<ListEntry>, deleteTombstones: List<String>) {
        if (upserts.isNotEmpty()) {
            val current = dao.byIds(upserts.map { it.id }).associateBy { it.id }
            val toWrite = upserts.filter { incoming ->
                val mine = current[incoming.id]?.toListEntry()
                mine == null || ListMerge.winner(mine, incoming) == incoming
            }
            if (toWrite.isNotEmpty()) dao.upsertAll(toWrite.map { it.toEntity(list) })
        }
        // The query only removes rows that are still tombstones.
        if (deleteTombstones.isNotEmpty()) dao.deleteTombstones(deleteTombstones)
    }
}

private fun ListEntryEntity.toListEntry() = ListEntry(
    id = id,
    kind = EntryKind.valueOf(kind),
    value = value,
    label = label,
    note = note,
    updatedAt = updatedAt,
    deviceId = deviceId,
    deleted = deleted
)

private fun ListEntry.toEntity(list: ListType) = ListEntryEntity(
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
