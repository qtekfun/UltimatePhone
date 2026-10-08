package com.qtekfun.ultimatephone.core.calllog

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DaySectionsAndStatsTest {
    private val now = clockAt("2026-10-08T12:00:00Z")

    @Test
    fun todayYesterdayAndOlderDates() {
        assertEquals(DaySection.Today, daySectionOf(millis("2026-10-08T00:00:00Z"), now))
        assertEquals(DaySection.Today, daySectionOf(millis("2026-10-08T11:59:59Z"), now))
        assertEquals(DaySection.Yesterday, daySectionOf(millis("2026-10-07T23:59:59Z"), now))
        assertEquals(DaySection.Yesterday, daySectionOf(millis("2026-10-07T00:00:00Z"), now))
        assertEquals(DaySection.Date(LocalDate.of(2026, 10, 6)), daySectionOf(millis("2026-10-06T23:59:59Z"), now))
        assertEquals(DaySection.Date(LocalDate.of(2025, 1, 1)), daySectionOf(millis("2025-01-01T10:00:00Z"), now))
    }

    @Test
    fun yesterdayAcrossAMonthBoundary() {
        val firstOfMonth = clockAt("2026-11-01T08:00:00Z")
        assertEquals(DaySection.Yesterday, daySectionOf(millis("2026-10-31T20:00:00Z"), firstOfMonth))
    }

    @Test
    fun theClockZoneDecidesWhereMidnightIs() {
        // 23:30 UTC on the 7th is already the 8th in Madrid (UTC+2 in October).
        val madrid = Clock.fixed(now.instant(), ZoneId.of("Europe/Madrid"))
        assertEquals(DaySection.Today, daySectionOf(millis("2026-10-07T23:30:00Z"), madrid))
        assertEquals(DaySection.Yesterday, daySectionOf(millis("2026-10-07T23:30:00Z"), now))
    }

    @Test
    fun futureDatesAreJustDates() {
        assertEquals(DaySection.Date(LocalDate.of(2026, 10, 9)), daySectionOf(millis("2026-10-09T09:00:00Z"), now))
    }

    private fun groupOf(id: Long, iso: String) = CallGroup(listOf(entry(id, date = millis(iso))))

    @Test
    fun sectionsSplitConsecutiveDaysAndKeepOrder() {
        val groups = listOf(
            groupOf(5, "2026-10-08T10:00:00Z"),
            groupOf(4, "2026-10-08T08:00:00Z"),
            groupOf(3, "2026-10-07T20:00:00Z"),
            groupOf(2, "2026-10-03T20:00:00Z"),
            groupOf(1, "2026-10-03T08:00:00Z")
        )
        val sections = sectionByDay(groups, now)
        assertEquals(listOf(DaySection.Today, DaySection.Yesterday, DaySection.Date(LocalDate.of(2026, 10, 3))), sections.map { it.section })
        assertEquals(listOf(listOf(5L, 4L), listOf(3L), listOf(2L, 1L)), sections.map { s -> s.groups.map { it.latest.id } })
    }

    @Test
    fun noGroupsNoSections() {
        assertTrue(sectionByDay(emptyList(), now).isEmpty())
    }

    @Test
    fun statsCountEachDirection() {
        val entries = listOf(
            entry(1, type = CallType.INCOMING, duration = 60),
            entry(2, type = CallType.ANSWERED_EXTERNALLY, duration = 30),
            entry(3, type = CallType.OUTGOING, duration = 125),
            entry(4, type = CallType.MISSED),
            entry(5, type = CallType.REJECTED),
            entry(6, type = CallType.BLOCKED)
        )
        assertEquals(CallStats(total = 6, missed = 1, incoming = 2, outgoing = 1, totalDurationSeconds = 215), statsOf(entries))
    }

    @Test
    fun statsOfNothingAreZero() {
        assertEquals(CallStats(0, 0, 0, 0, 0), statsOf(emptyList()))
    }

    @Test
    fun negativeDurationsDoNotSubtract() {
        assertEquals(10, statsOf(listOf(entry(1, duration = -50), entry(2, duration = 10))).totalDurationSeconds)
    }

    @Test
    fun durationClockFormats() {
        assertEquals("0:00", formatDurationClock(0))
        assertEquals("0:09", formatDurationClock(9))
        assertEquals("1:05", formatDurationClock(65))
        assertEquals("59:59", formatDurationClock(3599))
        assertEquals("1:00:00", formatDurationClock(3600))
        assertEquals("27:46:40", formatDurationClock(100_000))
        assertEquals("0:00", formatDurationClock(-5))
    }
}
