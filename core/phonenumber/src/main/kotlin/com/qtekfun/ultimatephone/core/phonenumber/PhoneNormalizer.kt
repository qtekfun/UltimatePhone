package com.qtekfun.ultimatephone.core.phonenumber

/** The result of normalising a raw number. Anything that is not a valid number is [Unknown], never spam. */
sealed interface NormalizedNumber {
    /** A valid number in E.164 form (`+34600123456`) and the ISO region it belongs to, when known. */
    data class Valid(val e164: String, val region: String?) : NormalizedNumber

    /** Hidden caller id, empty, malformed, a short code or otherwise not a dialable subscriber number. */
    data object Unknown : NormalizedNumber
}

fun NormalizedNumber.e164OrNull(): String? = (this as? NormalizedNumber.Valid)?.e164

interface PhoneNormalizer {
    /**
     * @param raw what the system or the user gave us, in any format.
     * @param defaultRegion ISO 3166 region used for numbers without a country code (SIM country or user setting).
     */
    fun normalize(raw: String?, defaultRegion: String?): NormalizedNumber

    /** Human readable form for the screen: national for the default region, international otherwise. */
    fun formatForDisplay(raw: String?, defaultRegion: String?): String
}
