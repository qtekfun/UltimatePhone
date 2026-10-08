package com.qtekfun.ultimatephone.screening

import com.qtekfun.ultimatephone.core.spam.decision.SpamAction

/**
 * What to tell Telecom about a call, as plain flags so the mapping can be tested without the framework. The service
 * copies them into a `CallScreeningService.CallResponse`.
 */
data class ScreeningResponse(
    val disallowCall: Boolean = false,
    val rejectCall: Boolean = false,
    val silenceCall: Boolean = false,
    val skipCallLog: Boolean = false,
    val skipNotification: Boolean = false
) {
    companion object {
        val Allow = ScreeningResponse()
    }
}

/**
 * Maps a decision to the system's answer. A rejected call is still written to the call log (so the user can see what
 * was filtered, and the history can badge it) but raises no missed-call notification. A silenced call rings no sound
 * and is otherwise a normal call. Warning only needs the call to go through: the warning is shown by the in-call UI.
 */
fun responseFor(action: SpamAction): ScreeningResponse = when (action) {
    SpamAction.REJECT -> ScreeningResponse(disallowCall = true, rejectCall = true, skipCallLog = false, skipNotification = true)
    SpamAction.SILENCE -> ScreeningResponse(silenceCall = true)
    SpamAction.WARN, SpamAction.NONE -> ScreeningResponse.Allow
}
