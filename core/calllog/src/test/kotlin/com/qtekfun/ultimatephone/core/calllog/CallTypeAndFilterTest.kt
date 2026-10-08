package com.qtekfun.ultimatephone.core.calllog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CallTypeAndFilterTest {
    @Test
    fun mapsEveryCallLogTypeCode() {
        assertEquals(CallType.INCOMING, CallType.fromCode(1))
        assertEquals(CallType.OUTGOING, CallType.fromCode(2))
        assertEquals(CallType.MISSED, CallType.fromCode(3))
        assertEquals(CallType.VOICEMAIL, CallType.fromCode(4))
        assertEquals(CallType.REJECTED, CallType.fromCode(5))
        assertEquals(CallType.BLOCKED, CallType.fromCode(6))
        assertEquals(CallType.ANSWERED_EXTERNALLY, CallType.fromCode(7))
    }

    @Test
    fun unknownCodesMapToUnknown() {
        listOf(0, 8, 99, -1, Int.MAX_VALUE).forEach { assertEquals(CallType.UNKNOWN, CallType.fromCode(it)) }
    }

    @Test
    fun answeredExternallyIsIncomingDirection() {
        assertEquals(DirectionClass.INCOMING, CallType.ANSWERED_EXTERNALLY.directionClass)
        assertEquals(DirectionClass.INCOMING, CallType.INCOMING.directionClass)
    }

    @Test
    fun everyOtherTypeHasItsOwnDirectionClass() {
        val others = listOf(CallType.OUTGOING, CallType.MISSED, CallType.REJECTED, CallType.BLOCKED, CallType.VOICEMAIL, CallType.UNKNOWN)
        assertEquals(others.size, others.map { it.directionClass }.toSet().size)
    }

    private fun matching(filter: CallLogFilter) = CallType.entries.filter { filter.matches(entry(1, type = it)) }

    @Test
    fun allMatchesEverything() {
        assertEquals(CallType.entries, matching(CallLogFilter.All))
    }

    @Test
    fun missedMatchesOnlyMissed() {
        assertEquals(listOf(CallType.MISSED), matching(CallLogFilter.Missed))
    }

    @Test
    fun incomingIncludesAnsweredExternallyButNotMissed() {
        assertEquals(listOf(CallType.INCOMING, CallType.ANSWERED_EXTERNALLY), matching(CallLogFilter.Incoming))
    }

    @Test
    fun outgoingMatchesOnlyOutgoing() {
        assertEquals(listOf(CallType.OUTGOING), matching(CallLogFilter.Outgoing))
    }

    @Test
    fun bySimMatchesComponentAndIdTogether() {
        val filter = CallLogFilter.BySim("com.android.phone/.Service", "sim1")
        assertTrue(filter.matches(entry(1, simComponent = "com.android.phone/.Service", simId = "sim1")))
        assertFalse(filter.matches(entry(2, simComponent = "com.android.phone/.Service", simId = "sim2")))
        assertFalse(filter.matches(entry(3, simComponent = "other/.Service", simId = "sim1")))
        assertFalse(filter.matches(entry(4)))
    }

    @Test
    fun filteredKeepsOrderAndShortCircuitsAll() {
        val all = listOf(entry(3, type = CallType.MISSED), entry(2, type = CallType.OUTGOING), entry(1, type = CallType.MISSED))
        assertEquals(listOf(3L, 1L), all.filtered(CallLogFilter.Missed).map { it.id })
        assertSame(all, all.filtered(CallLogFilter.All))
    }

    @Test
    fun hiddenNumbersAreDetected() {
        listOf("", "-1", "-2", "  ").forEach { assertTrue(entry(1, number = it).isPrivateNumber) }
        assertFalse(entry(1, number = "112").isPrivateNumber)
    }
}
