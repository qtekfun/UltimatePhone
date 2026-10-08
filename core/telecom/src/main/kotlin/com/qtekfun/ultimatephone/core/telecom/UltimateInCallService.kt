package com.qtekfun.ultimatephone.core.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Receives every call once the app holds the phone role. It only forwards to [TelecomCalls] and drives notifications. */
class UltimateInCallService : InCallService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifier: CallNotifier
    private lateinit var proximity: ProximityController

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = TelecomCalls.onCallChanged()

        override fun onDetailsChanged(call: Call, details: Call.Details) = TelecomCalls.onCallChanged()

        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = TelecomCalls.onCallChanged()

        override fun onParentChanged(call: Call, parent: Call?) = TelecomCalls.onCallChanged()
    }

    override fun onCreate() {
        super.onCreate()
        TelecomCalls.attach(this, this)
        notifier = CallNotifier(this).also { it.createChannels() }
        proximity = ProximityController(this)
        scope.launch {
            combine(TelecomCalls.calls, TelecomCalls.audio) { calls, audio -> calls to audio }.collect { (calls, audio) ->
                notifier.update(calls)
                proximity.update(calls, audio.route)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        proximity.release()
        notifier.cancelAll()
        TelecomCalls.detach(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        TelecomCalls.onCallAdded(call)
        // A ringing call is announced by the notification; anything else (we dialled it) opens the screen at once.
        if (call.details.state != Call.STATE_RINGING) notifier.openCallScreen()
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        TelecomCalls.onCallRemoved(call)
    }

    @Deprecated("Replaced by call endpoints on Android 14, still delivered on Android 12 and later")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        TelecomCalls.onAudioChanged(audioState)
    }
}
