package com.qtekfun.ultimatephone.core.spam.decision

import com.google.i18n.phonenumbers.ShortNumberInfo
import com.qtekfun.ultimatephone.core.phonenumber.NormalizedNumber

/** Emergency numbers are never filtered, whatever the lists say. */
fun interface EmergencyNumbers {
    /**
     * @param raw what the system reported, when available. Short codes such as `112` do not normalise, so they are
     * only recognisable here.
     */
    fun isEmergency(raw: String?, number: NormalizedNumber, region: String?): Boolean
}

/** Uses libphonenumber's short number metadata for [region], plus the codes that work nearly everywhere. */
object LibEmergencyNumbers : EmergencyNumbers {
    private val universal = setOf("112", "911", "999")

    override fun isEmergency(raw: String?, number: NormalizedNumber, region: String?): Boolean {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.any { it.isLetter() }) return false
        val digits = text.filter { it.isDigit() || it == '+' }
        if (digits in universal) return true
        val info = ShortNumberInfo.getInstance()
        val own = region?.uppercase()
        val numberRegion = (number as? NormalizedNumber.Valid)?.region
        return (own != null && info.isEmergencyNumber(digits, own)) ||
            (numberRegion != null && numberRegion != own && info.isEmergencyNumber(digits, numberRegion))
    }
}
