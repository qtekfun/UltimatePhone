package com.qtekfun.ultimatephone.spike

import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

/** Calls currently known to [SpikeInCallService], shared with the in-call screen. */
object CallStore {
    private val calls = CopyOnWriteArrayList<Call>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var audioState: CallAudioState? = null

    fun add(call: Call) {
        calls.add(call)
        changed()
    }

    fun remove(call: Call) {
        calls.remove(call)
        changed()
    }

    fun isEmpty(): Boolean = calls.isEmpty()

    /** The ringing call if there is one, otherwise the first call. */
    fun primary(): Call? = calls.firstOrNull { it.details.state == Call.STATE_RINGING } ?: calls.firstOrNull()

    fun changed() {
        main.post { listeners.forEach { it() } }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
}
