package com.qtekfun.ultimatephone.feature.recents

import com.qtekfun.ultimatephone.core.calllog.isHiddenNumber
import com.qtekfun.ultimatephone.core.contacts.ContactMatch
import com.qtekfun.ultimatephone.core.contacts.ContactsRepository
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.data.BusinessFinder
import com.qtekfun.ultimatephone.data.BusinessInfo
import com.qtekfun.ultimatephone.data.NoBusinesses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Who a number belongs to, for display.
 *
 * @property contact the matching contact, if any.
 * @property business the business identified from the installed packs; only set when there is no contact.
 * @property displayName the contact's name, else the business name, else the name the call log cached, else null.
 * @property formattedNumber the number as the user expects to read it; blank for hidden numbers.
 */
data class CallerInfo(val contact: ContactMatch?, val displayName: String?, val formattedNumber: String, val business: BusinessInfo? = null) {
    /** What a row shows as its title: the name, else the number; null means a hidden number (the UI says "Unknown"). */
    val title: String? get() = displayName ?: formattedNumber.ifBlank { null }
}

/** Looks numbers up in the contacts, remembering answers until [clear] so a long history costs one lookup per number. */
class CallerResolver(
    private val contacts: ContactsRepository,
    private val normalizer: PhoneNormalizer,
    private val regionProvider: RegionProvider,
    private val businesses: BusinessFinder = NoBusinesses
) {
    private val matches = HashMap<String, ContactMatch?>()
    private val businessMatches = HashMap<String, BusinessInfo?>()

    suspend fun resolve(number: String, cachedName: String?): CallerInfo {
        val hidden = isHiddenNumber(number)
        val match = if (hidden) null else lookup(number)
        val formatted = if (hidden) "" else normalizer.formatForDisplay(number, regionProvider.defaultRegion())
        // A contact is never replaced by a business.
        val business = if (hidden || match != null) null else business(number)
        return CallerInfo(match, match?.displayName ?: business?.name ?: cachedName?.takeIf { it.isNotBlank() }, formatted, business)
    }

    private suspend fun business(number: String): BusinessInfo? {
        if (businessMatches.containsKey(number)) return businessMatches[number]
        return withContext(Dispatchers.IO) { businesses.find(number) }.also { businessMatches[number] = it }
    }

    private suspend fun lookup(number: String): ContactMatch? {
        if (matches.containsKey(number)) return matches[number]
        return contacts.lookupByNumber(number).also { matches[number] = it }
    }

    /** Forgets the cached answers; returns false when there was nothing to forget. */
    fun clear(): Boolean {
        val had = matches.isNotEmpty() || businessMatches.isNotEmpty()
        matches.clear()
        businessMatches.clear()
        return had
    }
}
