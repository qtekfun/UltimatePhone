package com.qtekfun.ultimatephone.core.telecom

import kotlinx.coroutines.flow.StateFlow

/** What a caller turned out to be. Filled in by the app, which knows contacts (and, later, businesses and spam). */
data class CallerLabel(val name: String?, val photoUri: String? = null)

fun interface CallerLabelResolver {
    suspend fun resolve(number: String): CallerLabel?
}

/** The UI's view of the calls handled by the in-call service, and the actions it can take. */
interface CallController {
    val calls: StateFlow<List<CallInfo>>
    val audio: StateFlow<AudioState>

    fun answer(id: String)

    fun reject(id: String)

    fun hangup(id: String)

    fun setHold(id: String, hold: Boolean)

    fun setMuted(muted: Boolean)

    fun setRoute(route: AudioRoute)

    fun playDtmf(id: String, digit: Char)

    fun stopDtmf(id: String)

    /** Joins two calls into a conference when the network allows it. */
    fun mergeCalls()

    /** Brings the held call back and puts the active one on hold. */
    fun swapCalls()
}

/** Intent contract between the telecom module (notifications) and the app's in-call screen. */
object InCallIntents {
    const val ACTION_IN_CALL = "com.qtekfun.ultimatephone.action.IN_CALL"

    /** Set to true when the user tapped "Answer" on the notification. */
    const val EXTRA_ANSWER = "com.qtekfun.ultimatephone.extra.ANSWER"
}
