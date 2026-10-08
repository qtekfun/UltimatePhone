package com.qtekfun.ultimatephone.screening

import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.util.Log
import com.qtekfun.ultimatephone.core.phonenumber.PhoneMask
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Answers the system's question "what should happen to this incoming call" while the phone rings. Only used when the
 * user gave the app the call screening role; without it the in-call screen shows the same decision as a warning.
 *
 * It must answer fast and never wait for the network: the decision reads contacts, two indexed list queries and the
 * in-memory pack lookups. If anything goes wrong or takes longer than [BUDGET_MS] the call is allowed, because a
 * call that rings is better than a call that is lost. Hidden numbers, outgoing calls and non-`tel:` handles are never
 * actioned.
 */
@AndroidEntryPoint
class SpamScreeningService : CallScreeningService() {
    @Inject
    lateinit var engine: DecisionEngine

    @Inject
    lateinit var stats: ScreeningStats

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onScreenCall(callDetails: Call.Details) {
        val number = actionableNumber(callDetails)
        if (number == null) {
            respondToCall(callDetails, ScreeningResponse.Allow.toCallResponse())
            return
        }
        scope.launch {
            val started = SystemClock.elapsedRealtime()
            val verdict = runCatching { withTimeoutOrNull(BUDGET_MS) { engine.decide(number, record = false) } }
                .onFailure {
                    if (it is CancellationException) throw it
                    Log.w(TAG, "Screening failed, allowing the call: ${it.javaClass.simpleName}")
                }
                .getOrNull()
            // Visible to the in-call screen before Telecom shows the call, then answer, and only then touch the disk.
            verdict?.let(engine::remember)
            val action = verdict?.decision?.action ?: SpamAction.NONE
            respondToCall(callDetails, responseFor(action).toCallResponse())
            val elapsed = SystemClock.elapsedRealtime() - started
            stats.record(elapsed)
            Log.d(TAG, "Screened ${PhoneMask.mask(number)} in $elapsed ms: $action (slowest recent ${stats.slowestRecentMillis()} ms)")
            verdict?.let { runCatching { engine.record(it) } }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** The number to decide on, or null for calls that must pass untouched. */
    private fun actionableNumber(details: Call.Details): String? {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return null
        if (details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED) return null
        val handle = details.handle ?: return null
        if (handle.scheme != PhoneAccount.SCHEME_TEL) return null
        return handle.schemeSpecificPart?.takeIf { it.isNotBlank() }
    }

    private fun ScreeningResponse.toCallResponse(): CallResponse = CallResponse.Builder()
        .setDisallowCall(disallowCall)
        .setRejectCall(rejectCall)
        .setSilenceCall(silenceCall)
        .setSkipCallLog(skipCallLog)
        .setSkipNotification(skipNotification)
        .build()

    private companion object {
        const val TAG = "SpamScreening"

        /** The system gives a screening service about five seconds; stay well inside it. */
        const val BUDGET_MS = 2_000L
    }
}
