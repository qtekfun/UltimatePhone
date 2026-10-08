package com.qtekfun.ultimatephone.core.calllog

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** The header above a day of calls. The UI turns it into text ("Today", "Yesterday", a formatted date). */
sealed interface DaySection {
    data object Today : DaySection

    data object Yesterday : DaySection

    data class Date(val date: LocalDate) : DaySection
}

/** Which header a call at [millis] falls under, according to [clock] (injected so tests control "now" and the zone). */
fun daySectionOf(millis: Long, clock: Clock): DaySection {
    val today = LocalDate.now(clock)
    val day = Instant.ofEpochMilli(millis).atZone(clock.zone).toLocalDate()
    return when (day) {
        today -> DaySection.Today
        today.minusDays(1) -> DaySection.Yesterday
        else -> DaySection.Date(day)
    }
}

data class DayGroup(val section: DaySection, val groups: List<CallGroup>)

/** Splits rows (newest first) into consecutive day sections. */
fun sectionByDay(groups: List<CallGroup>, clock: Clock): List<DayGroup> {
    val result = mutableListOf<DayGroup>()
    for (group in groups) {
        val section = daySectionOf(group.latest.dateMillis, clock)
        val last = result.lastOrNull()
        if (last != null && last.section == section) {
            result[result.lastIndex] = last.copy(groups = last.groups + group)
        } else {
            result += DayGroup(section, listOf(group))
        }
    }
    return result
}
