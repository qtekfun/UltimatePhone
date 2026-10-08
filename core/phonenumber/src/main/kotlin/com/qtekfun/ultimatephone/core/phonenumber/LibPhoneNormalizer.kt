package com.qtekfun.ultimatephone.core.phonenumber

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibPhoneNormalizer @Inject constructor() : PhoneNormalizer {
    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

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
        if (text.isEmpty() || text.any { it.isLetter() } || text.filter { it.isDigit() }.length < MIN_DIGITS) {
            null
        } else {
            util.parse(text, defaultRegion?.uppercase())
        }
    } catch (_: NumberParseException) {
        null
    }

    private companion object {
        /** Shorter than this is a service or emergency code, not something to look up. */
        const val MIN_DIGITS = 5
    }
}
