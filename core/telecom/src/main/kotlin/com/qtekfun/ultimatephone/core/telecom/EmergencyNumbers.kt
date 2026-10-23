package com.qtekfun.ultimatephone.core.telecom

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager

/** Emergency numbers are dialled at once, on whatever SIM the system picks. */
fun interface EmergencyNumbers {
    fun isEmergency(number: String): Boolean
}

class TelephonyEmergencyNumbers(context: Context) : EmergencyNumbers {
    private val telephony = context.getSystemService(TelephonyManager::class.java)

    @Suppress("DEPRECATION") // The framework check needs READ_PHONE_STATE; the old static one works without it.
    override fun isEmergency(number: String): Boolean = try {
        telephony.isEmergencyNumber(number)
    } catch (_: SecurityException) {
        PhoneNumberUtils.isEmergencyNumber(number)
    } catch (_: IllegalStateException) {
        PhoneNumberUtils.isEmergencyNumber(number) // Thrown when the telephony service is not up.
    }
}
