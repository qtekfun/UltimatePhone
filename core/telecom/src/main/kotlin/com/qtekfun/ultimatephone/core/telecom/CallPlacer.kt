package com.qtekfun.ultimatephone.core.telecom

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.TelecomManager

/** Starts an outgoing call. The in-call screen is opened by the in-call service once Telecom connects it. */
interface CallPlacer {
    /**
     * @param sim the SIM to use; null lets the SIM preferences decide, and then the system (which may ask the user).
     * @param lastUsed the SIM of an earlier call with this number: preferred over the default SIM when nothing else is known.
     */
    fun placeCall(number: String, sim: SimAccount? = null, lastUsed: SimAccount? = null): Boolean

    /** Calls the voicemail of [sim] (null: the SIM preferences, then the system, decide). */
    fun callVoicemail(sim: SimAccount? = null): Boolean
}

/** Places the call through Telecom on [sim], or on whatever Telecom picks; [lastUsed] is a hint for a wrapper that applies preferences. */
class TelecomCallPlacer(private val context: Context) : CallPlacer {
    @SuppressLint("MissingPermission")
    override fun placeCall(number: String, sim: SimAccount?, lastUsed: SimAccount?): Boolean {
        if (number.isBlank()) return false
        return place(Uri.fromParts(PhoneAccount.SCHEME_TEL, number.trim(), null), sim)
    }

    override fun callVoicemail(sim: SimAccount?): Boolean = place(Uri.fromParts(PhoneAccount.SCHEME_VOICEMAIL, "", null), sim)

    @SuppressLint("MissingPermission")
    private fun place(uri: Uri, sim: SimAccount?): Boolean {
        val extras = Bundle()
        if (sim != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, sim.handle)
        return try {
            context.getSystemService(TelecomManager::class.java).placeCall(uri, extras)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
