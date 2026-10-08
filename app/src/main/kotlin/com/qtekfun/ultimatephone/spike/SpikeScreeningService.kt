package com.qtekfun.ultimatephone.spike

import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * Test 4 and 5: decides before the call rings from a fixed list and logs how long the decision took.
 * Real decisions must stay in memory or in one indexed read; there is no network here by design.
 */
class SpikeScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val started = SystemClock.elapsedRealtime()
        EventLog.init(this)
        val number = callDetails.handle?.schemeSpecificPart
        val rule = TestRules.match(RuleStore.load(this), number)
        val response = CallResponse.Builder()
        when (rule?.action) {
            ScreenAction.REJECT -> response.setDisallowCall(true).setRejectCall(true).setSkipCallLog(false).setSkipNotification(false)
            ScreenAction.SILENCE -> response.setSilenceCall(true)
            ScreenAction.WARN, null -> Unit
        }
        respondToCall(callDetails, response.build())
        val tookMs = SystemClock.elapsedRealtime() - started
        val sinceCreationMs = System.currentTimeMillis() - callDetails.creationTimeMillis
        EventLog.add(
            "screen",
            "${CallNames.direction(callDetails.callDirection)} ${PhoneMask.mask(number)} verdict=${rule?.action ?: "NONE"} " +
                "decided_in=${tookMs}ms since_call_created=${sinceCreationMs}ms verstat=${callDetails.callerNumberVerificationStatus}"
        )
    }
}
