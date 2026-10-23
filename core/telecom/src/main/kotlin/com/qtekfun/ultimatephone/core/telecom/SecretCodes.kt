package com.qtekfun.ultimatephone.core.telecom

import android.content.Context
import android.telephony.TelephonyManager

/**
 * Android secret codes (`*#*#4636#*#*` and the like). While the app is the phone app, the system leaves them to it: they
 * only reach the apps that listen for them if the keypad passes them on.
 */
fun interface SecretCodes {
    /** @return false when the system refused (the app is not the phone app). */
    fun send(code: String): Boolean
}

class TelephonySecretCodes(private val context: Context) : SecretCodes {
    override fun send(code: String): Boolean = try {
        context.getSystemService(TelephonyManager::class.java).sendDialerSpecialCode(code)
        true
    } catch (_: SecurityException) {
        false
    }
}
