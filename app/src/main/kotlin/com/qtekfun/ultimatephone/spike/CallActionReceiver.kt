package com.qtekfun.ultimatephone.spike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Notification actions that need no UI: decline an incoming call or hang up the current one. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EventLog.init(context)
        val call = CallStore.primary()
        EventLog.add("incall", "notification action ${intent.action} call=${if (call == null) "none" else "present"}")
        when (intent.action) {
            ACTION_DECLINE -> call?.reject(false, null)
            ACTION_HANGUP -> call?.disconnect()
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.qtekfun.ultimatephone.spike.DECLINE"
        const val ACTION_HANGUP = "com.qtekfun.ultimatephone.spike.HANGUP"
    }
}
