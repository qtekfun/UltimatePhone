package com.qtekfun.ultimatephone.core.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMachineTest {
    private fun reduce(state: SessionState, vararg events: SessionEvent): SessionState = events.fold(state) { s, e -> SessionMachine.reduce(s, e) }

    private val recording = SessionState.Recording("c1", 1000L, CaptureSource.MICROPHONE)

    @Test
    fun `happy path goes idle starting recording stopping idle`() {
        val end = reduce(
            SessionState.Idle,
            SessionEvent.RequestStart("c1"),
            SessionEvent.Started(CaptureSource.MICROPHONE, 1000L),
            SessionEvent.RequestStop,
            SessionEvent.Stopped(valid = true)
        )
        assertEquals(SessionState.Idle, end)
    }

    @Test
    fun `start creates a starting session for the call`() {
        assertEquals(SessionState.Starting("c1"), reduce(SessionState.Idle, SessionEvent.RequestStart("c1")))
    }

    @Test
    fun `start is ignored while busy`() {
        listOf(SessionState.Starting("a"), recording, SessionState.Stopping("a")).forEach {
            assertEquals(it, reduce(it, SessionEvent.RequestStart("other")))
        }
    }

    @Test
    fun `a failed session can start again`() {
        val failed = SessionState.Failed(FailureReason.STORAGE_FAILED)
        assertEquals(SessionState.Starting("c2"), reduce(failed, SessionEvent.RequestStart("c2")))
    }

    @Test
    fun `started moves to recording with the source and time`() {
        assertEquals(recording, reduce(SessionState.Starting("c1"), SessionEvent.Started(CaptureSource.MICROPHONE, 1000L)))
    }

    @Test
    fun `started is ignored when not starting`() {
        assertEquals(SessionState.Idle, reduce(SessionState.Idle, SessionEvent.Started(CaptureSource.MICROPHONE, 5L)))
        assertEquals(SessionState.Stopping("c1"), reduce(SessionState.Stopping("c1"), SessionEvent.Started(CaptureSource.MICROPHONE, 5L)))
    }

    @Test
    fun `start failure carries the reason`() {
        val state = reduce(SessionState.Starting("c1"), SessionEvent.StartFailed(FailureReason.FOREGROUND_NOT_ALLOWED))
        assertEquals(SessionState.Failed(FailureReason.FOREGROUND_NOT_ALLOWED), state)
    }

    @Test
    fun `giving up while starting ends quietly`() {
        val end = reduce(SessionState.Starting("c1"), SessionEvent.RequestStop, SessionEvent.StartFailed(FailureReason.AUDIO_SOURCE_FAILED))
        assertEquals(SessionState.Idle, end)
    }

    @Test
    fun `stop request while recording moves to stopping`() {
        assertEquals(SessionState.Stopping("c1"), reduce(recording, SessionEvent.RequestStop))
    }

    @Test
    fun `stop request is ignored when idle or failed`() {
        assertEquals(SessionState.Idle, reduce(SessionState.Idle, SessionEvent.RequestStop))
        val failed = SessionState.Failed(FailureReason.NO_AUDIO)
        assertEquals(failed, reduce(failed, SessionEvent.RequestStop))
    }

    @Test
    fun `an empty recording is reported as no audio`() {
        assertEquals(SessionState.Failed(FailureReason.NO_AUDIO), reduce(SessionState.Stopping("c1"), SessionEvent.Stopped(valid = false)))
    }

    @Test
    fun `stopped is ignored outside stopping`() {
        assertEquals(recording, reduce(recording, SessionEvent.Stopped(valid = true)))
    }

    @Test
    fun `errors fail an active session in any active state`() {
        listOf(SessionState.Starting("a"), recording, SessionState.Stopping("a")).forEach {
            assertEquals(SessionState.Failed(FailureReason.CAPTURE_ERROR), reduce(it, SessionEvent.Error(FailureReason.CAPTURE_ERROR)))
        }
    }

    @Test
    fun `errors are ignored when nothing is active`() {
        assertEquals(SessionState.Idle, reduce(SessionState.Idle, SessionEvent.Error(FailureReason.CAPTURE_ERROR)))
    }

    @Test
    fun `dismiss clears only a failure`() {
        assertEquals(SessionState.Idle, reduce(SessionState.Failed(FailureReason.NO_AUDIO), SessionEvent.Dismiss))
        assertEquals(recording, reduce(recording, SessionEvent.Dismiss))
    }

    @Test
    fun `call id and busy follow the active states`() {
        assertEquals("c1", SessionMachine.callIdOf(recording))
        assertEquals("a", SessionMachine.callIdOf(SessionState.Starting("a")))
        assertEquals("a", SessionMachine.callIdOf(SessionState.Stopping("a")))
        assertNull(SessionMachine.callIdOf(SessionState.Idle))
        assertNull(SessionMachine.callIdOf(SessionState.Failed(FailureReason.NO_AUDIO)))
        assertTrue(SessionMachine.isBusy(recording))
        assertFalse(SessionMachine.isBusy(SessionState.Idle))
    }
}
