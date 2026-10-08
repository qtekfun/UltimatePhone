package com.qtekfun.ultimatephone.core.telecom

import android.content.Context
import android.os.PowerManager

/** Holds the proximity wake lock (screen off near the face) according to [ProximityPolicy]. */
internal class ProximityController(context: Context) {
    private val lock: PowerManager.WakeLock? = context.getSystemService(PowerManager::class.java).let { power ->
        if (power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "UltimatePhone:proximity").apply { setReferenceCounted(false) }
        } else {
            null
        }
    }

    fun update(calls: List<CallInfo>, route: AudioRoute) {
        val wanted = ProximityPolicy.shouldBeOn(calls, route)
        val held = lock?.isHeld == true
        if (wanted && !held) {
            lock?.acquire()
        } else if (!wanted && held) {
            lock?.release()
        }
    }

    fun release() {
        if (lock?.isHeld == true) lock.release()
    }
}
