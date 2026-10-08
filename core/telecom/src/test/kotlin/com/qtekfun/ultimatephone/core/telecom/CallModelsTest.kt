package com.qtekfun.ultimatephone.core.telecom

import android.telecom.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallModelsTest {
    private fun call(status: CallStatus) = CallInfo(
        id = status.name,
        status = status,
        number = "+34612345678",
        displayNumber = "612 34 56 78",
        incoming = false,
        sim = null,
        connectedAtMillis = 0,
        canHold = true,
        canMerge = false,
        isConference = false,
        participants = 0
    )

    @Test
    fun mapsFrameworkStatesToOurStatuses() {
        assertEquals(CallStatus.RINGING, CallStatusMapper.fromState(Call.STATE_RINGING))
        assertEquals(CallStatus.RINGING, CallStatusMapper.fromState(Call.STATE_SIMULATED_RINGING))
        assertEquals(CallStatus.DIALING, CallStatusMapper.fromState(Call.STATE_DIALING))
        assertEquals(CallStatus.DIALING, CallStatusMapper.fromState(Call.STATE_PULLING_CALL))
        assertEquals(CallStatus.ACTIVE, CallStatusMapper.fromState(Call.STATE_ACTIVE))
        assertEquals(CallStatus.HOLDING, CallStatusMapper.fromState(Call.STATE_HOLDING))
        assertEquals(CallStatus.DISCONNECTED, CallStatusMapper.fromState(Call.STATE_DISCONNECTED))
        assertEquals(CallStatus.OTHER, CallStatusMapper.fromState(Call.STATE_NEW))
    }

    @Test
    fun audioRoutePrefersTheMostSpecificActiveRoute() {
        assertEquals(AudioRoute.SPEAKER, AudioRouteMapper.route(0x8))
        assertEquals(AudioRoute.BLUETOOTH, AudioRouteMapper.route(0x2))
        assertEquals(AudioRoute.WIRED_HEADSET, AudioRouteMapper.route(0x4))
        assertEquals(AudioRoute.EARPIECE, AudioRouteMapper.route(0x1))
        assertEquals(AudioRoute.EARPIECE, AudioRouteMapper.route(0))
    }

    @Test
    fun availableRoutesFollowTheSupportedMask() {
        assertEquals(setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER), AudioRouteMapper.available(0x9))
        assertEquals(setOf(AudioRoute.BLUETOOTH, AudioRoute.WIRED_HEADSET, AudioRoute.SPEAKER), AudioRouteMapper.available(0xE))
        assertEquals(emptySet<AudioRoute>(), AudioRouteMapper.available(0))
    }

    @Test
    fun routeMasksRoundTrip() {
        AudioRoute.entries.forEach { assertEquals(it, AudioRouteMapper.route(AudioRouteMapper.toMask(it))) }
    }

    @Test
    fun proximityIsOnOnlyForLiveCallsOnTheEarpiece() {
        val active = listOf(call(CallStatus.ACTIVE))
        assertTrue(ProximityPolicy.shouldBeOn(active, AudioRoute.EARPIECE))
        assertTrue(ProximityPolicy.shouldBeOn(active, AudioRoute.WIRED_HEADSET))
        assertTrue(ProximityPolicy.shouldBeOn(listOf(call(CallStatus.DIALING)), AudioRoute.EARPIECE))
        assertFalse(ProximityPolicy.shouldBeOn(active, AudioRoute.SPEAKER))
        assertFalse(ProximityPolicy.shouldBeOn(active, AudioRoute.BLUETOOTH))
        assertFalse(ProximityPolicy.shouldBeOn(listOf(call(CallStatus.RINGING)), AudioRoute.EARPIECE))
        assertFalse(ProximityPolicy.shouldBeOn(listOf(call(CallStatus.HOLDING)), AudioRoute.EARPIECE))
        assertFalse(ProximityPolicy.shouldBeOn(emptyList(), AudioRoute.EARPIECE))
    }

    @Test
    fun titleIsTheContactNameOrTheFormattedNumber() {
        assertEquals("612 34 56 78", call(CallStatus.ACTIVE).title)
        assertEquals("Ana", call(CallStatus.ACTIVE).copy(contactName = "Ana").title)
    }

    @Test
    fun durationIsFormattedAsClock() {
        assertEquals("00:00", CallDurationFormatter.format(0))
        assertEquals("00:09", CallDurationFormatter.format(9))
        assertEquals("01:05", CallDurationFormatter.format(65))
        assertEquals("59:59", CallDurationFormatter.format(3599))
        assertEquals("1:00:00", CallDurationFormatter.format(3600))
        assertEquals("2:03:04", CallDurationFormatter.format(7384))
        assertEquals("00:00", CallDurationFormatter.format(-5))
    }
}
