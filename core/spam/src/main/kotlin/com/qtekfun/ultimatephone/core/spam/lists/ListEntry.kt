package com.qtekfun.ultimatephone.core.spam.lists

import com.qtekfun.ultimatephone.core.spam.prefix.PrefixCandidates

/** The two user lists. Each one syncs to its own file (`spam-list.jsonl`, `whitelist.jsonl`). */
enum class ListType { OWN, WHITELIST }

enum class EntryKind { NUMBER, PREFIX }

/**
 * One list entry in the F7 sync shape. [value] is an E.164 number for [EntryKind.NUMBER] and an international
 * prefix (`+34900`) for [EntryKind.PREFIX]. A [deleted] entry is a tombstone kept so deletions can sync.
 */
data class ListEntry(
    val id: String,
    val kind: EntryKind,
    val value: String,
    val label: String?,
    val note: String?,
    val updatedAt: Long,
    val deviceId: String,
    val deleted: Boolean = false
)

object EntryValues {
    private val number = Regex("""\+[1-9]\d{4,14}""")
    private val prefix = Regex("""\+[1-9]\d{0,13}""")

    fun isValid(kind: EntryKind, value: String): Boolean = when (kind) {
        EntryKind.NUMBER -> number.matches(value)
        EntryKind.PREFIX -> prefix.matches(value)
    }

    /**
     * Keys to query for [e164]: the number itself and every proper prefix, for one `value IN (...)` query.
     */
    fun lookupKeys(e164: String): List<String> = listOf(e164) + PrefixCandidates.of(e164)

    /**
     * Picks the best live hit among rows returned for [lookupKeys]: an exact number beats any prefix, and a longer
     * prefix beats a shorter one. A NUMBER row whose value is not the number itself is ignored.
     */
    fun best(e164: String, rows: List<ListEntry>): ListEntry? {
        val live = rows.filter { !it.deleted }
        live.firstOrNull { it.kind == EntryKind.NUMBER && it.value == e164 }?.let { return it }
        return live
            .filter { it.kind == EntryKind.PREFIX && e164.startsWith(it.value) && e164.length > it.value.length }
            .maxByOrNull { it.value.length }
    }
}

/** Sync merge rule: the newer `updatedAt` wins, a tie goes to the greater `deviceId`. */
object EntryMerge {
    fun winner(local: ListEntry, remote: ListEntry): ListEntry = when {
        remote.updatedAt > local.updatedAt -> remote
        remote.updatedAt < local.updatedAt -> local
        remote.deviceId > local.deviceId -> remote
        else -> local
    }
}
