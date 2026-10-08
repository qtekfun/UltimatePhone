package com.qtekfun.ultimatephone.core.phonenumber

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.ShortNumberInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PhoneNumberUtil] based normaliser. Loading libphonenumber's metadata is the heaviest one-off cost of the phone
 * number code (tens to hundreds of milliseconds on a phone), so it happens on first use only, never at construction,
 * and [warmUp] lets the app pay it on a background thread. A caller that arrives before the warm-up has finished simply
 * waits for the same load (the library initialises under a lock); nothing is loaded twice.
 *
 * Use [shared] where Hilt is not available so the process holds a single instance.
 */
@Singleton
class LibPhoneNormalizer @Inject constructor() : PhoneNormalizer {
    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    /**
     * Loads the metadata of the library, of the short number (emergency) data and of [region], by exercising them once.
     * Safe on any thread, idempotent, and cheap once done. Call it from a background thread at start-up.
     */
    fun warmUp(region: String?) {
        val code = region?.uppercase()?.takeIf { it.length == 2 }
        val sample = code?.let { util.getExampleNumberForType(it, PhoneNumberType.MOBILE) }
        if (sample != null) {
            util.isValidNumber(sample)
            util.format(sample, PhoneNumberFormat.E164)
            util.format(sample, PhoneNumberFormat.NATIONAL)
        }
        ShortNumberInfo.getInstance().isEmergencyNumber("112", code ?: "ES")
    }

    override fun normalize(raw: String?, defaultRegion: String?): NormalizedNumber {
        val parsed = parse(raw, defaultRegion) ?: return NormalizedNumber.Unknown
        if (!util.isValidNumber(parsed)) return NormalizedNumber.Unknown
        return NormalizedNumber.Valid(util.format(parsed, PhoneNumberFormat.E164), util.getRegionCodeForNumber(parsed))
    }

    override fun formatForDisplay(raw: String?, defaultRegion: String?): String {
        val text = raw?.trim().orEmpty()
        val parsed = parse(text, defaultRegion)
        if (parsed == null || !util.isValidNumber(parsed)) return text
        val region = util.getRegionCodeForNumber(parsed)
        val format = if (region != null && region.equals(defaultRegion, ignoreCase = true)) {
            PhoneNumberFormat.NATIONAL
        } else {
            PhoneNumberFormat.INTERNATIONAL
        }
        return util.format(parsed, format)
    }

    private fun parse(raw: String?, defaultRegion: String?) = try {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.any { it.isLetter() } || text.count { it.isDigit() } < MIN_DIGITS) {
            null
        } else {
            util.parse(text, defaultRegion?.uppercase())
        }
    } catch (_: NumberParseException) {
        null
    }

    companion object {
        /** The one instance of the process, for code that is not injected (the in-call service). */
        val shared: LibPhoneNormalizer by lazy { LibPhoneNormalizer() }

        /** Shorter than this is a service or emergency code, not something to look up. */
        private const val MIN_DIGITS = 5
    }
}
