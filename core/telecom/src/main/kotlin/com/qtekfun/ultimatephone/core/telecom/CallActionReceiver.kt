package com.qtekfun.ultimatephone.core.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Notification buttons that need no screen: decline a ringing call or hang up the current one. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val calls = TelecomCalls.calls.value
        when (intent.action) {
            ACTION_DECLINE -> calls.firstOrNull { it.status == CallStatus.RINGING }?.let { TelecomCalls.reject(it.id) }
            ACTION_HANGUP -> calls.firstOrNull { it.status != CallStatus.RINGING }?.let { TelecomCalls.hangup(it.id) }
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.qtekfun.ultimatephone.action.DECLINE"
        const val ACTION_HANGUP = "com.qtekfun.ultimatephone.action.HANGUP"
    }
}
