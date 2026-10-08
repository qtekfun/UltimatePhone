package com.qtekfun.ultimatephone.core.recording

/** State of the one recording session the app can have at a time. */
sealed interface SessionState {
    data object Idle : SessionState

    data class Starting(val callId: String) : SessionState

    data class Recording(val callId: String, val startedAtMillis: Long, val source: CaptureSource) : SessionState

    data class Stopping(val callId: String) : SessionState

    data class Failed(val reason: FailureReason) : SessionState
}

sealed interface SessionEvent {
    data class RequestStart(val callId: String) : SessionEvent

    data class Started(val source: CaptureSource, val atMillis: Long) : SessionEvent

    data class StartFailed(val reason: FailureReason) : SessionEvent

    data object RequestStop : SessionEvent

    /** The recorder was stopped; [valid] is false when it had captured nothing usable. */
    data class Stopped(val valid: Boolean) : SessionEvent

    /** Something went wrong while recording or stopping. */
    data class Error(val reason: FailureReason) : SessionEvent

    /** The user dismissed a failure. */
    data object Dismiss : SessionEvent
}

/** Pure transition function of a recording session. Events that make no sense in a state leave it unchanged. */
object SessionMachine {
    fun reduce(state: SessionState, event: SessionEvent): SessionState = when (event) {
        is SessionEvent.RequestStart -> requestStart(state, event)
        is SessionEvent.Started -> if (state is SessionState.Starting) SessionState.Recording(state.callId, event.atMillis, event.source) else state
        is SessionEvent.StartFailed -> startFailed(state, event)
        SessionEvent.RequestStop -> requestStop(state)
        is SessionEvent.Stopped -> stopped(state, event)
        is SessionEvent.Error -> if (isBusy(state)) SessionState.Failed(event.reason) else state
        SessionEvent.Dismiss -> if (state is SessionState.Failed) SessionState.Idle else state
    }

    private fun requestStart(state: SessionState, event: SessionEvent.RequestStart): SessionState =
        if (state == SessionState.Idle || state is SessionState.Failed) SessionState.Starting(event.callId) else state

    private fun startFailed(state: SessionState, event: SessionEvent.StartFailed): SessionState = when (state) {
        is SessionState.Starting -> SessionState.Failed(event.reason)
        // The user gave up while it was starting: nothing to report.
        is SessionState.Stopping -> SessionState.Idle
        else -> state
    }

    private fun requestStop(state: SessionState): SessionState = when (state) {
        is SessionState.Starting -> SessionState.Stopping(state.callId)
        is SessionState.Recording -> SessionState.Stopping(state.callId)
        else -> state
    }

    private fun stopped(state: SessionState, event: SessionEvent.Stopped): SessionState = when {
        state !is SessionState.Stopping -> state
        event.valid -> SessionState.Idle
        else -> SessionState.Failed(FailureReason.NO_AUDIO)
    }

    /** The call a session is tied to, if any. */
    fun callIdOf(state: SessionState): String? = when (state) {
        is SessionState.Starting -> state.callId
        is SessionState.Recording -> state.callId
        is SessionState.Stopping -> state.callId
        else -> null
    }

    /** True while the microphone may be (or is about to be) in use. */
    fun isBusy(state: SessionState): Boolean = callIdOf(state) != null
}
