package com.qtekfun.ultimatephone.core.telecom

import android.telecom.Call

/** Simplified life cycle of a call, as the UI needs it. */
enum class CallStatus { CONNECTING, DIALING, RINGING, ACTIVE, HOLDING, DISCONNECTING, DISCONNECTED, OTHER }

object CallStatusMapper {
    fun fromState(state: Int): CallStatus = when (state) {
        Call.STATE_CONNECTING -> CallStatus.CONNECTING
        Call.STATE_DIALING, Call.STATE_PULLING_CALL -> CallStatus.DIALING
        Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> CallStatus.RINGING
        Call.STATE_ACTIVE -> CallStatus.ACTIVE
        Call.STATE_HOLDING -> CallStatus.HOLDING
        Call.STATE_DISCONNECTING -> CallStatus.DISCONNECTING
        Call.STATE_DISCONNECTED -> CallStatus.DISCONNECTED
        else -> CallStatus.OTHER
    }
}

/**
 * One call as shown on screen. [number] is the raw number from Telecom (null for a hidden caller id);
 * [displayNumber] is the same formatted for the user. [contactName] and [contactPhotoUri] arrive a moment after the
 * call, once the caller has been looked up.
 */
data class CallInfo(
    val id: String,
    val status: CallStatus,
    val number: String?,
    val displayNumber: String,
    val incoming: Boolean,
    val sim: SimAccount?,
    val connectedAtMillis: Long,
    val canHold: Boolean,
    val canMerge: Boolean,
    val isConference: Boolean,
    val participants: Int,
    val contactName: String? = null,
    val contactPhotoUri: String? = null,
    val disconnectLabel: String? = null,
    /** Set when [contactName] is the name of an identified business; the category id and its icon name. */
    val isBusiness: Boolean = false,
    val businessCategory: String? = null,
    val businessIcon: String? = null
) {
    /** What to show as the title of the call. */
    val title: String get() = contactName ?: displayNumber
}

/** Where the call audio goes. */
enum class AudioRoute { EARPIECE, WIRED_HEADSET, BLUETOOTH, SPEAKER }

data class AudioState(val route: AudioRoute, val available: Set<AudioRoute>, val muted: Boolean) {
    companion object {
        val Default = AudioState(AudioRoute.EARPIECE, setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER), muted = false)
    }
}

/** Translates the bit masks of the framework's CallAudioState. */
object AudioRouteMapper {
    private const val EARPIECE = 0x1
    private const val BLUETOOTH = 0x2
    private const val WIRED_HEADSET = 0x4
    private const val SPEAKER = 0x8

    fun route(mask: Int): AudioRoute = when {
        mask and SPEAKER != 0 -> AudioRoute.SPEAKER
        mask and BLUETOOTH != 0 -> AudioRoute.BLUETOOTH
        mask and WIRED_HEADSET != 0 -> AudioRoute.WIRED_HEADSET
        else -> AudioRoute.EARPIECE
    }

    fun available(mask: Int): Set<AudioRoute> = buildSet {
        if (mask and EARPIECE != 0) add(AudioRoute.EARPIECE)
        if (mask and BLUETOOTH != 0) add(AudioRoute.BLUETOOTH)
        if (mask and WIRED_HEADSET != 0) add(AudioRoute.WIRED_HEADSET)
        if (mask and SPEAKER != 0) add(AudioRoute.SPEAKER)
    }

    fun toMask(route: AudioRoute): Int = when (route) {
        AudioRoute.EARPIECE -> EARPIECE
        AudioRoute.BLUETOOTH -> BLUETOOTH
        AudioRoute.WIRED_HEADSET -> WIRED_HEADSET
        AudioRoute.SPEAKER -> SPEAKER
    }
}

/** The screen turns off near the face only while a call is up and the sound comes out of the earpiece. */
object ProximityPolicy {
    fun shouldBeOn(calls: List<CallInfo>, route: AudioRoute): Boolean {
        val inCall = calls.any { it.status == CallStatus.ACTIVE || it.status == CallStatus.DIALING || it.status == CallStatus.CONNECTING }
        return inCall && (route == AudioRoute.EARPIECE || route == AudioRoute.WIRED_HEADSET)
    }
}

/** Turns call durations into the clock shown on the in-call screen. */
object CallDurationFormatter {
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600

    fun format(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val hours = seconds / SECONDS_PER_HOUR
        val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val rest = seconds % SECONDS_PER_MINUTE
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%02d:%02d".format(minutes, rest)
    }
}
