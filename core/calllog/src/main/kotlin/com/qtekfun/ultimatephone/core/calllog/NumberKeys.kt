package com.qtekfun.ultimatephone.core.calllog

import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.phonenumber.e164OrNull
import com.qtekfun.ultimatephone.core.telecom.RegionProvider

/**
 * Comparison keys for phone numbers: the E.164 form when the number is valid, else its digits, so "600 123 456" and
 * "+34600123456" are the same number but a short code is only equal to itself.
 */
class NumberKeys(private val normalizer: PhoneNormalizer, private val regionProvider: RegionProvider) {
    /** A key function bound to the current region; the region is read once, not per number. */
    fun keyFunction(): (String) -> String {
        val region = regionProvider.defaultRegion()
        return { raw -> keyOf(raw, region) }
    }

    fun keyOf(raw: String, region: String = regionProvider.defaultRegion()): String =
        normalizer.normalize(raw, region).e164OrNull() ?: raw.filter { it.isDigit() }
}
