package com.qtekfun.ultimatephone.feature.recents

import com.qtekfun.ultimatephone.core.calllog.isHiddenNumber
import com.qtekfun.ultimatephone.core.contacts.ContactMatch
import com.qtekfun.ultimatephone.core.contacts.ContactsRepository
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.RegionProvider

/**
 * Who a number belongs to, for display.
 *
 * @property contact the matching contact, if any.
 * @property displayName the contact's name, else the name the call log cached, else null.
 * @property formattedNumber the number as the user expects to read it; blank for hidden numbers.
 */
data class CallerInfo(val contact: ContactMatch?, val displayName: String?, val formattedNumber: String) {
    /** What a row shows as its title: the name, else the number; null means a hidden number (the UI says "Unknown"). */
    val title: String? get() = displayName ?: formattedNumber.ifBlank { null }
}

/** Looks numbers up in the contacts, remembering answers until [clear] so a long history costs one lookup per number. */
class CallerResolver(private val contacts: ContactsRepository, private val normalizer: PhoneNormalizer, private val regionProvider: RegionProvider) {
    private val matches = HashMap<String, ContactMatch?>()

    suspend fun resolve(number: String, cachedName: String?): CallerInfo {
        val hidden = isHiddenNumber(number)
        val match = if (hidden) null else lookup(number)
        val formatted = if (hidden) "" else normalizer.formatForDisplay(number, regionProvider.defaultRegion())
        return CallerInfo(match, match?.displayName ?: cachedName?.takeIf { it.isNotBlank() }, formatted)
    }

    private suspend fun lookup(number: String): ContactMatch? {
        if (matches.containsKey(number)) return matches[number]
        return contacts.lookupByNumber(number).also { matches[number] = it }
    }

    /** Forgets the cached answers; returns false when there was nothing to forget. */
    fun clear(): Boolean {
        val had = matches.isNotEmpty()
        matches.clear()
        return had
    }
}
