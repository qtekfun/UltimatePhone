package com.qtekfun.ultimatephone.core.telecom

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager

/** Starts an outgoing call. The in-call screen is opened by the in-call service once Telecom connects it. */
interface CallPlacer {
    /**
     * @param sim the SIM to use; null lets the SIM preferences decide, and then the system (which may ask the user).
     * @param lastUsed the SIM of an earlier call with this number: preferred over the default SIM when nothing else is known.
     */
    fun placeCall(number: String, sim: SimAccount? = null, lastUsed: SimAccount? = null): Boolean
}

/** Places the call through Telecom on [sim], or on whatever Telecom picks; [lastUsed] is a hint for a wrapper that applies preferences. */
class TelecomCallPlacer(private val context: Context) : CallPlacer {
    @SuppressLint("MissingPermission")
    override fun placeCall(number: String, sim: SimAccount?, lastUsed: SimAccount?): Boolean {
        if (number.isBlank()) return false
        val extras = Bundle()
        if (sim != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, sim.handle)
        return try {
            context.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number.trim(), null), extras)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
