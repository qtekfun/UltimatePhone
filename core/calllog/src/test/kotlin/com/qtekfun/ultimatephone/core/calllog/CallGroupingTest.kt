package com.qtekfun.ultimatephone.core.calllog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallGroupingTest {
    private val keys = NumberKeys(FakeNormalizer(), FakeRegion()).keyFunction()

    private fun group(entries: List<CallLogEntry>) = groupCalls(entries, Utc, keys).map { g -> g.entries.map { it.id } }

    private val noon = millis("2026-10-08T12:00:00Z")

    private fun at(id: Long, minutesAgo: Long, number: String = "600111222", type: CallType = CallType.INCOMING) =
        entry(id, number = number, type = type, date = noon - minutesAgo * 60_000)

    @Test
    fun emptyListGivesNoGroups() {
        assertTrue(group(emptyList()).isEmpty())
    }

    @Test
    fun consecutiveCallsToTheSameNumberFormOneRow() {
        assertEquals(listOf(listOf(3L, 2L, 1L)), group(listOf(at(3, 1), at(2, 2), at(1, 3))))
    }

    @Test
    fun numbersInDifferentFormatsAreTheSameNumber() {
        assertEquals(listOf(listOf(2L, 1L)), group(listOf(at(2, 1, "600111222"), at(1, 2, "+34 600 111 222"))))
    }

    @Test
    fun onlyNeighboursMerge() {
        val entries = listOf(at(4, 1), at(3, 2, "699000111"), at(2, 3), at(1, 4))
        assertEquals(listOf(listOf(4L), listOf(3L), listOf(2L, 1L)), group(entries))
    }

    @Test
    fun differentDirectionClassesDoNotMerge() {
        val entries = listOf(at(3, 1, type = CallType.MISSED), at(2, 2, type = CallType.INCOMING), at(1, 3, type = CallType.OUTGOING))
        assertEquals(listOf(listOf(3L), listOf(2L), listOf(1L)), group(entries))
    }

    @Test
    fun incomingAndAnsweredExternallyMerge() {
        val entries = listOf(at(2, 1, type = CallType.ANSWERED_EXTERNALLY), at(1, 2, type = CallType.INCOMING))
        assertEquals(listOf(listOf(2L, 1L)), group(entries))
    }

    @Test
    fun missedAndRejectedStaySeparate() {
        assertEquals(2, group(listOf(at(2, 1, type = CallType.MISSED), at(1, 2, type = CallType.REJECTED))).size)
    }

    @Test
    fun hiddenNumbersNeverMerge() {
        val entries = listOf(at(3, 1, ""), at(2, 2, ""), at(1, 3, "-1"))
        assertEquals(3, group(entries).size)
    }

    @Test
    fun aGroupNeverCrossesMidnight() {
        val late = entry(2, date = millis("2026-10-08T00:10:00Z"))
        val early = entry(1, date = millis("2026-10-07T23:50:00Z"))
        assertEquals(listOf(listOf(2L), listOf(1L)), group(listOf(late, early)))
    }

    @Test
    fun theLatestEntryIsTheNewestAndCountIsSize() {
        val g = groupCalls(listOf(at(3, 1), at(2, 2), at(1, 3)), Utc, keys).single()
        assertEquals(3L, g.latest.id)
        assertEquals(3, g.count)
    }

    @Test
    fun shortCodesOnlyMatchThemselves() {
        assertEquals(listOf(listOf(3L, 2L), listOf(1L)), group(listOf(at(3, 1, "112"), at(2, 2, "112"), at(1, 3, "091"))))
    }

    @Test
    fun entriesForNumberIgnoresFormatAndKeepsOrder() {
        val all = listOf(at(4, 1, "+34600111222"), at(3, 2, "699000111"), at(2, 3, "600 111 222"), at(1, 4, ""))
        assertEquals(listOf(4L, 2L), entriesForNumber(all, "600111222", keys).map { it.id })
    }

    @Test
    fun entriesForHiddenNumberMatchesExactStringOnly() {
        val all = listOf(at(3, 1, ""), at(2, 2, "-1"), at(1, 3, ""))
        assertEquals(listOf(3L, 1L), entriesForNumber(all, "", keys).map { it.id })
    }
}
