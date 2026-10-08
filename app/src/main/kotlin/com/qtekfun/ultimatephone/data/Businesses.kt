package com.qtekfun.ultimatephone.data

import com.qtekfun.ultimatephone.core.contacts.ContactsRepository
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.phonenumber.e164OrNull
import com.qtekfun.ultimatephone.core.spam.decision.BusinessLookup
import com.qtekfun.ultimatephone.core.telecom.CallerLabel
import com.qtekfun.ultimatephone.core.telecom.CallerLabelResolver
import com.qtekfun.ultimatephone.core.telecom.RegionProvider

/**
 * The icon of a business category. The ids and the icon names are those of the data repository
 * (`pipeline/categories.py`); the names are Material Symbols names that the UI maps to its own vectors.
 */
object BusinessCategories {
    const val DEFAULT_CATEGORY = "other"
    const val DEFAULT_ICON = "storefront"

    private val ICONS = mapOf(
        "food" to "restaurant",
        "grocery" to "shopping_basket",
        "shopping" to "shopping_bag",
        "health" to "local_hospital",
        "lodging" to "hotel",
        "automotive" to "directions_car",
        "finance" to "account_balance",
        "beauty" to "content_cut",
        "services" to "build",
        "office" to "business_center",
        "education" to "school",
        "government" to "account_balance_wallet",
        "transport" to "local_taxi",
        "leisure" to "sports_tennis",
        "culture" to "theater_comedy",
        DEFAULT_CATEGORY to DEFAULT_ICON
    )

    /** Every category id the data repository defines. */
    val ids: Set<String> get() = ICONS.keys

    /** The icon name of [category]; a generic shop for a missing or unknown category, never null. */
    fun iconName(category: String?): String = category?.trim()?.lowercase()?.let(ICONS::get) ?: DEFAULT_ICON

    /** The category id to show a label for: the known id, else [DEFAULT_CATEGORY]; null when the pack gave none. */
    fun normalized(category: String?): String? = category?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { if (it in ICONS) it else DEFAULT_CATEGORY }
}

/** A business identified from the installed packs. */
data class BusinessInfo(val name: String, val category: String?, val iconName: String)

/** Identifies a phone number as a business, if the installed packs know it. */
fun interface BusinessFinder {
    fun find(number: String): BusinessInfo?
}

/** For callers that work without business data (and for tests). */
object NoBusinesses : BusinessFinder {
    override fun find(number: String): BusinessInfo? = null
}

/** Looks a phone number up in the business packs. Business names never leave the device. */
class BusinessResolver(private val lookups: () -> BusinessLookup, private val normalizer: PhoneNormalizer, private val regionProvider: RegionProvider) :
    BusinessFinder {
    override fun find(number: String): BusinessInfo? {
        val e164 = normalizer.normalize(number, regionProvider.defaultRegion()).e164OrNull() ?: return null
        val match = lookups().find(e164) ?: return null
        val category = BusinessCategories.normalized(match.category)
        return BusinessInfo(match.name, category, BusinessCategories.iconName(category))
    }
}

/** Names callers for the call screen: a contact first, a business only when there is no contact. */
class CallerLabelProvider(private val contacts: ContactsRepository, private val businesses: BusinessFinder) : CallerLabelResolver {
    override suspend fun resolve(number: String): CallerLabel? {
        contacts.lookupByNumber(number)?.let { return CallerLabel(it.displayName, it.photoThumbUri) }
        return businesses.find(number)?.let {
            CallerLabel(name = it.name, businessCategory = it.category, businessIcon = it.iconName, isBusiness = true)
        }
    }
}
