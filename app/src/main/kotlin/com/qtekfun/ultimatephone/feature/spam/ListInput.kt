package com.qtekfun.ultimatephone.feature.spam

import com.qtekfun.ultimatephone.core.phonenumber.NormalizedNumber
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.spam.lists.EntryKind
import com.qtekfun.ultimatephone.core.spam.lists.EntryValues

/** Turns what the user typed into the value stored in a list, or null when it is not acceptable. */
object ListInput {
    /** A prefix shorter than this (digits after the `+`) would cover far too many numbers to be a typo-safe choice. */
    const val MIN_PREFIX_DIGITS = 4

    fun validate(kind: EntryKind, raw: String, normalizer: PhoneNormalizer, region: String?): String? = when (kind) {
        EntryKind.NUMBER -> (normalizer.normalize(raw.trim(), region) as? NormalizedNumber.Valid)?.e164
        EntryKind.PREFIX -> validatePrefix(raw)
    }

    private fun validatePrefix(raw: String): String? {
        val cleaned = raw.trim().filter { it.isDigit() || it == '+' }
        if (!cleaned.startsWith("+") || cleaned.count { it == '+' } != 1) return null
        if (cleaned.length - 1 < MIN_PREFIX_DIGITS) return null
        return cleaned.takeIf { EntryValues.isValid(EntryKind.PREFIX, it) }
    }
}
