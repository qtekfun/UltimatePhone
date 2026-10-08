package com.qtekfun.ultimatephone.core.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliciesTest {
    @Test
    fun `number key keeps the last nine digits`() {
        assertEquals("612345678", NumberKey.of("+34 612 34 56 78"))
        assertEquals("612345678", NumberKey.of("0034612345678"))
        assertEquals("123", NumberKey.of("123"))
        assertNull(NumberKey.of(null))
        assertNull(NumberKey.of("   "))
        assertNull(NumberKey.of("private"))
    }

    @Test
    fun `mode off never records automatically`() {
        assertFalse(AutoRecordPolicy.shouldRecord(RecordingMode.OFF, false, "1", setOf("1")))
    }

    @Test
    fun `mode all records every call`() {
        assertTrue(AutoRecordPolicy.shouldRecord(RecordingMode.ALL_CALLS, true, "1", emptySet()))
        assertTrue(AutoRecordPolicy.shouldRecord(RecordingMode.ALL_CALLS, false, null, emptySet()))
    }

    @Test
    fun `mode unknown records only callers that are not contacts`() {
        assertFalse(AutoRecordPolicy.shouldRecord(RecordingMode.UNKNOWN_NUMBERS, true, "1", emptySet()))
        assertTrue(AutoRecordPolicy.shouldRecord(RecordingMode.UNKNOWN_NUMBERS, false, "1", emptySet()))
        assertTrue(AutoRecordPolicy.shouldRecord(RecordingMode.UNKNOWN_NUMBERS, false, null, emptySet()))
    }

    @Test
    fun `mode selected records only chosen numbers`() {
        val selected = setOf("612345678")
        assertTrue(AutoRecordPolicy.shouldRecord(RecordingMode.SELECTED_CONTACTS, true, "612345678", selected))
        assertFalse(AutoRecordPolicy.shouldRecord(RecordingMode.SELECTED_CONTACTS, true, "600000000", selected))
        assertFalse(AutoRecordPolicy.shouldRecord(RecordingMode.SELECTED_CONTACTS, true, null, selected))
        assertFalse(AutoRecordPolicy.shouldRecord(RecordingMode.SELECTED_CONTACTS, true, "612345678", emptySet()))
    }

    private fun readiness(
        enabled: Boolean = true,
        legal: Boolean = true,
        folder: Boolean = true,
        permission: Boolean = true,
        capability: RecordingCapability? = RecordingCapability.MICROPHONE_ONLY
    ) = Readiness.evaluate(enabled, legal, folder, permission, capability)

    @Test
    fun `readiness reports the first problem in order`() {
        assertEquals(Readiness.READY, readiness())
        assertEquals(Readiness.DISABLED, readiness(enabled = false, legal = false, folder = false, permission = false))
        assertEquals(Readiness.LEGAL_NOT_ACCEPTED, readiness(legal = false, folder = false, permission = false))
        assertEquals(Readiness.NO_FOLDER, readiness(folder = false, permission = false))
        assertEquals(Readiness.NO_MICROPHONE, readiness(permission = false, capability = RecordingCapability.UNAVAILABLE))
        assertEquals(Readiness.NO_PERMISSION, readiness(permission = false))
    }

    @Test
    fun `readiness accepts a capability that is not detected yet`() {
        assertEquals(Readiness.READY, readiness(capability = null))
        assertEquals(Readiness.READY, readiness(capability = RecordingCapability.LINE_CAPTURE))
    }

    @Test
    fun `readiness maps to failure reasons`() {
        assertNull(Readiness.READY.toFailure())
        assertEquals(FailureReason.NOT_ENABLED, Readiness.DISABLED.toFailure())
        assertEquals(FailureReason.LEGAL_NOT_ACCEPTED, Readiness.LEGAL_NOT_ACCEPTED.toFailure())
        assertEquals(FailureReason.NO_FOLDER, Readiness.NO_FOLDER.toFailure())
        assertEquals(FailureReason.NO_PERMISSION, Readiness.NO_PERMISSION.toFailure())
        assertEquals(FailureReason.NO_MICROPHONE, Readiness.NO_MICROPHONE.toFailure())
    }

    @Test
    fun `speaker is switched on for microphone only when the user chose it`() {
        val mic = RecordingCapability.MICROPHONE_ONLY
        assertTrue(SpeakerPolicy.shouldEnableSpeaker(mic, autoSpeaker = true, announce = false, onEarpiece = true))
        assertFalse(SpeakerPolicy.shouldEnableSpeaker(mic, autoSpeaker = false, announce = false, onEarpiece = true))
        assertTrue(SpeakerPolicy.shouldEnableSpeaker(null, autoSpeaker = true, announce = false, onEarpiece = true))
    }

    @Test
    fun `speaker is not needed for line capture unless announcing`() {
        val line = RecordingCapability.LINE_CAPTURE
        assertFalse(SpeakerPolicy.shouldEnableSpeaker(line, autoSpeaker = true, announce = false, onEarpiece = true))
        assertTrue(SpeakerPolicy.shouldEnableSpeaker(line, autoSpeaker = false, announce = true, onEarpiece = true))
    }

    @Test
    fun `speaker never overrides a headset or bluetooth`() {
        assertFalse(SpeakerPolicy.shouldEnableSpeaker(RecordingCapability.MICROPHONE_ONLY, autoSpeaker = true, announce = true, onEarpiece = false))
    }

    @Test
    fun `speaker is left alone without a microphone`() {
        assertFalse(SpeakerPolicy.shouldEnableSpeaker(RecordingCapability.UNAVAILABLE, autoSpeaker = true, announce = true, onEarpiece = true))
    }

    @Test
    fun `announce needs the option and a microphone`() {
        assertTrue(AnnouncePolicy.shouldAnnounce(true, RecordingCapability.MICROPHONE_ONLY))
        assertTrue(AnnouncePolicy.shouldAnnounce(true, null))
        assertFalse(AnnouncePolicy.shouldAnnounce(false, RecordingCapability.MICROPHONE_ONLY))
        assertFalse(AnnouncePolicy.shouldAnnounce(true, RecordingCapability.UNAVAILABLE))
    }

    @Test
    fun `microphone warning shows once`() {
        assertTrue(MicWarningPolicy.shouldWarn(RecordingCapability.MICROPHONE_ONLY, alreadyShown = false))
        assertFalse(MicWarningPolicy.shouldWarn(RecordingCapability.MICROPHONE_ONLY, alreadyShown = true))
        assertFalse(MicWarningPolicy.shouldWarn(RecordingCapability.LINE_CAPTURE, alreadyShown = false))
        assertFalse(MicWarningPolicy.shouldWarn(null, alreadyShown = false))
    }

    @Test
    fun `a line source with real audio wins and is final`() {
        val results = listOf(ProbeResult(CaptureSource.VOICE_CALL, started = false, peak = 0), ProbeResult(CaptureSource.VOICE_DOWNLINK, true, 800))
        val decision = CapabilityDetector.decide(results, previousSilentAttempts = 2)
        assertEquals(RecordingCapability.LINE_CAPTURE, decision.capability)
        assertEquals(CaptureSource.VOICE_DOWNLINK, decision.source)
        assertTrue(decision.persist)
        assertEquals(0, decision.silentAttempts)
    }

    @Test
    fun `voice call is preferred when both work`() {
        val results = listOf(ProbeResult(CaptureSource.VOICE_CALL, true, 5), ProbeResult(CaptureSource.VOICE_DOWNLINK, true, 900))
        assertEquals(CaptureSource.VOICE_CALL, CapabilityDetector.decide(results, 0).source)
    }

    @Test
    fun `sources that fail to start mean microphone only, for good`() {
        val results = CapabilityDetector.LINE_SOURCES.map { ProbeResult(it, started = false, peak = 0) }
        val decision = CapabilityDetector.decide(results, 0)
        assertEquals(RecordingCapability.MICROPHONE_ONLY, decision.capability)
        assertEquals(CaptureSource.MICROPHONE, decision.source)
        assertTrue(decision.persist)
    }

    @Test
    fun `an empty probe means microphone only`() {
        assertEquals(RecordingCapability.MICROPHONE_ONLY, CapabilityDetector.decide(emptyList(), 0).capability)
    }

    @Test
    fun `a silent line is only believed after several attempts`() {
        val silent = listOf(ProbeResult(CaptureSource.VOICE_CALL, started = true, peak = 0))
        val first = CapabilityDetector.decide(silent, 0)
        assertEquals(RecordingCapability.MICROPHONE_ONLY, first.capability)
        assertFalse(first.persist)
        assertEquals(1, first.silentAttempts)
        assertFalse(CapabilityDetector.decide(silent, 1).persist)
        val third = CapabilityDetector.decide(silent, CapabilityDetector.MAX_SILENT_ATTEMPTS - 1)
        assertTrue(third.persist)
        assertEquals(CapabilityDetector.MAX_SILENT_ATTEMPTS, third.silentAttempts)
    }

    @Test
    fun `line sources are voice call then downlink`() {
        assertEquals(listOf(CaptureSource.VOICE_CALL, CaptureSource.VOICE_DOWNLINK), CapabilityDetector.LINE_SOURCES)
        assertTrue(CaptureSource.VOICE_CALL.isLine)
        assertFalse(CaptureSource.MICROPHONE.isLine)
    }
}
