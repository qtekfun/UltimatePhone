package com.qtekfun.ultimatephone.spike

import android.telecom.Call

object CallNames {
    fun state(state: Int): String = when (state) {
        Call.STATE_NEW -> "NEW"
        Call.STATE_DIALING -> "DIALING"
        Call.STATE_RINGING -> "RINGING"
        Call.STATE_HOLDING -> "HOLDING"
        Call.STATE_ACTIVE -> "ACTIVE"
        Call.STATE_DISCONNECTED -> "DISCONNECTED"
        Call.STATE_CONNECTING -> "CONNECTING"
        Call.STATE_DISCONNECTING -> "DISCONNECTING"
        Call.STATE_SELECT_PHONE_ACCOUNT -> "SELECT_PHONE_ACCOUNT"
        Call.STATE_SIMULATED_RINGING -> "SIMULATED_RINGING"
        Call.STATE_AUDIO_PROCESSING -> "AUDIO_PROCESSING"
        else -> "STATE_$state"
    }

    fun direction(direction: Int): String = when (direction) {
        Call.Details.DIRECTION_INCOMING -> "incoming"
        Call.Details.DIRECTION_OUTGOING -> "outgoing"
        else -> "direction?"
    }

    fun callLogType(type: Int): String = when (type) {
        1 -> "INCOMING"
        2 -> "OUTGOING"
        3 -> "MISSED"
        4 -> "VOICEMAIL"
        5 -> "REJECTED"
        6 -> "BLOCKED"
        7 -> "ANSWERED_EXTERNALLY"
        else -> "TYPE_$type"
    }
}
