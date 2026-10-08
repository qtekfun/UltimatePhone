package com.qtekfun.ultimatephone.core.calllog

import java.time.Instant
import java.time.ZoneId

/** Several consecutive calls with the same number and direction, shown as one row. [entries] are newest first. */
data class CallGroup(val entries: List<CallLogEntry>) {
    init {
        require(entries.isNotEmpty()) { "A group needs at least one call" }
    }

    /** The most recent call, which gives the row its time, type and SIM. */
    val latest: CallLogEntry get() = entries.first()
    val count: Int get() = entries.size
}

/**
 * Collapses consecutive calls into rows, like Google Phone: only neighbours merge, never calls separated by a call
 * with another number or another direction class. Hidden numbers never merge, and a group never crosses midnight so
 * the day sections stay correct.
 *
 * @param entries newest first, as the repository returns them.
 * @param numberKey maps a raw number to a comparison key (see [NumberKeys]).
 */
fun groupCalls(entries: List<CallLogEntry>, zone: ZoneId = ZoneId.systemDefault(), numberKey: (String) -> String): List<CallGroup> {
    val groups = mutableListOf<CallGroup>()
    var current = mutableListOf<CallLogEntry>()
    var currentKey = ""

    fun flush() {
        if (current.isNotEmpty()) groups += CallGroup(current)
        current = mutableListOf()
    }

    for (entry in entries) {
        val key = if (entry.isPrivateNumber) "" else numberKey(entry.number)
        val previous = current.lastOrNull()
        val joins = previous != null && key.isNotEmpty() && key == currentKey &&
            previous.type.directionClass == entry.type.directionClass &&
            dayOf(previous.dateMillis, zone) == dayOf(entry.dateMillis, zone)
        if (!joins) {
            flush()
            currentKey = key
        }
        current += entry
    }
    flush()
    return groups
}

private fun dayOf(millis: Long, zone: ZoneId) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/** The calls to or from [number], newest first, comparing by [numberKey] so formats do not matter. */
fun entriesForNumber(entries: List<CallLogEntry>, number: String, numberKey: (String) -> String): List<CallLogEntry> {
    if (isHiddenNumber(number)) return entries.filter { it.number == number }
    val wanted = numberKey(number)
    return entries.filter { !it.isPrivateNumber && numberKey(it.number) == wanted }
}
