package com.qtekfun.ultimatephone.data

import com.qtekfun.ultimatephone.core.contacts.ContactMatch
import com.qtekfun.ultimatephone.core.spam.decision.BusinessLookup
import com.qtekfun.ultimatephone.core.spam.decision.BusinessMatch
import com.qtekfun.ultimatephone.core.telecom.CallerLabel
import com.qtekfun.ultimatephone.feature.recents.CallerResolver
import com.qtekfun.ultimatephone.feature.recents.FakeContacts
import com.qtekfun.ultimatephone.feature.recents.FakeNormalizer
import com.qtekfun.ultimatephone.feature.recents.FakeRegion
import com.qtekfun.ultimatephone.feature.recents.match
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessCategoriesTest {
    /** The category ids and icon names of `pipeline/categories.py` in the data repository. */
    private val dataRepoCategories = mapOf(
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
        "other" to "storefront"
    )

    @Test
    fun everyCategoryOfTheDataRepositoryMapsToItsIcon() {
        dataRepoCategories.forEach { (category, icon) -> assertEquals(category, icon, BusinessCategories.iconName(category)) }
        assertEquals(dataRepoCategories.keys, BusinessCategories.ids)
    }

    @Test
    fun missingOrUnknownCategoriesGetTheSafeDefault() {
        assertEquals("storefront", BusinessCategories.iconName(null))
        assertEquals("storefront", BusinessCategories.iconName(""))
        assertEquals("storefront", BusinessCategories.iconName("spaceship"))
    }

    @Test
    fun matchingIgnoresCaseAndSpaces() {
        assertEquals("restaurant", BusinessCategories.iconName(" Food "))
        assertEquals("food", BusinessCategories.normalized(" FOOD"))
    }

    @Test
    fun normalizedKeepsKnownIdsMapsUnknownToOtherAndLeavesMissingAlone() {
        assertEquals("health", BusinessCategories.normalized("health"))
        assertEquals("other", BusinessCategories.normalized("spaceship"))
        assertNull(BusinessCategories.normalized(null))
        assertNull(BusinessCategories.normalized("  "))
    }
}

class BusinessResolutionTest {
    private val pharmacy = BusinessMatch("Farmacia Sol", "health", "businesses-es")
    private val lookup = BusinessLookup { e164 -> pharmacy.takeIf { e164 == "+34912345678" } }
    private val resolver = BusinessResolver({ lookup }, FakeNormalizer(), FakeRegion())

    @Test
    fun aKnownNumberResolvesToNameCategoryAndIcon() {
        assertEquals(BusinessInfo("Farmacia Sol", "health", "local_hospital"), resolver.find("912 34 56 78"))
    }

    @Test
    fun unknownHiddenAndInvalidNumbersResolveToNothing() {
        assertNull(resolver.find("933333333"))
        assertNull(resolver.find(""))
        assertNull(resolver.find("12"))
    }

    @Test
    fun aBusinessWithoutACategoryGetsTheDefaultIcon() {
        val noCategory = BusinessResolver({ BusinessLookup { BusinessMatch("Bar Pepe", null) } }, FakeNormalizer(), FakeRegion())
        assertEquals(BusinessInfo("Bar Pepe", null, "storefront"), noCategory.find("912345678"))
    }

    @Test
    fun callLabelsNameContactsFirstAndNeverOverwriteThemWithABusiness() = runTest {
        val contacts = FakeContacts(mapOf("912345678" to match("Ana")))
        val label = CallerLabelProvider(contacts, resolver).resolve("912345678")
        assertEquals(CallerLabel("Ana", null), label)
        assertFalse(label!!.isBusiness)
    }

    @Test
    fun callLabelsFallBackToTheBusinessWithItsCategoryAndIcon() = runTest {
        val label = CallerLabelProvider(FakeContacts(), resolver).resolve("912345678")!!
        assertEquals("Farmacia Sol", label.name)
        assertEquals("health", label.businessCategory)
        assertEquals("local_hospital", label.businessIcon)
        assertTrue(label.isBusiness)
    }

    @Test
    fun callLabelsAreNullWhenNobodyKnowsTheNumber() = runTest {
        assertNull(CallerLabelProvider(FakeContacts(), resolver).resolve("955555555"))
    }

    @Test
    fun historyShowsABusinessNameWhenThereIsNoContact() = runTest {
        val caller = CallerResolver(FakeContacts(), FakeNormalizer(), FakeRegion(), resolver).resolve("912345678", cachedName = "Cached")
        assertEquals("Farmacia Sol", caller.displayName)
        assertEquals("health", caller.business?.category)
        assertNull(caller.contact)
    }

    @Test
    fun historyKeepsTheContactNameOverTheBusinessAndTheCachedName() = runTest {
        val contacts = FakeContacts(mapOf("912345678" to ContactMatch("k", "Ana", null)))
        val caller = CallerResolver(contacts, FakeNormalizer(), FakeRegion(), resolver).resolve("912345678", cachedName = "Cached")
        assertEquals("Ana", caller.displayName)
        assertNull(caller.business)
    }

    @Test
    fun historyFallsBackToTheCachedNameThenToNothing() = runTest {
        val caller = CallerResolver(FakeContacts(), FakeNormalizer(), FakeRegion(), resolver)
        assertEquals("Cached", caller.resolve("933333333", cachedName = "Cached").displayName)
        assertNull(caller.resolve("933333333", cachedName = null).displayName)
    }

    @Test
    fun hiddenNumbersAreNeverLookedUpAsBusinesses() = runTest {
        var asked = 0
        val counting = BusinessFinder {
            asked++
            null
        }
        CallerResolver(FakeContacts(), FakeNormalizer(), FakeRegion(), counting).resolve("-2", cachedName = null)
        assertEquals(0, asked)
    }

    @Test
    fun businessAnswersAreCachedUntilCleared() = runTest {
        var asked = 0
        val counting = BusinessFinder {
            asked++
            null
        }
        val caller = CallerResolver(FakeContacts(), FakeNormalizer(), FakeRegion(), counting)
        caller.resolve("933333333", null)
        caller.resolve("933333333", null)
        assertEquals(1, asked)
        assertTrue(caller.clear())
        caller.resolve("933333333", null)
        assertEquals(2, asked)
    }
}

class BusinessSearchTest {
    @Test
    fun threeKeysExpandToEveryLetterCombinationAsPrefixTerms() {
        val query = BusinessQuery.matchForDigits("327")!!
        val terms = query.split(" OR ")
        assertEquals(3 * 3 * 4, terms.size)
        assertTrue("\"far\"*" in terms)
        assertTrue("\"ebs\"*" in terms)
    }

    @Test
    fun expansionStopsAtFourKeysSoTheQueryStaysSmall() {
        assertEquals(3 * 3 * 4 * 3, BusinessQuery.matchForDigits("3274")!!.split(" OR ").size)
        assertEquals(BusinessQuery.matchForDigits("3274"), BusinessQuery.matchForDigits("32745678"))
    }

    @Test
    fun shortOrNonDigitQueriesAreNotSearched() {
        assertNull(BusinessQuery.matchForDigits("32"))
        assertNull(BusinessQuery.matchForDigits(""))
        assertNull(BusinessQuery.matchForDigits("32*"))
        assertNull(BusinessQuery.matchForDigits("3a7"))
    }

    @Test
    fun keysZeroAndOneStayAsDigits() {
        assertEquals("\"101\"*", BusinessQuery.matchForDigits("101"))
    }

    @Test
    fun t9MatchingUsesTheStartOfAnyWordAndIgnoresAccents() {
        assertTrue(BusinessQuery.matchesT9("Farmacia Sol", "327"))
        assertTrue(BusinessQuery.matchesT9("Farmacia Sol", "765"))
        assertTrue(BusinessQuery.matchesT9("Café Ñandú", "2233"))
        assertFalse(BusinessQuery.matchesT9("Farmacia Sol", "329"))
        assertFalse("must be the start of a word", BusinessQuery.matchesT9("Farmacia", "622"))
    }

    @Test
    fun resultsFilteredByTheFullCodeAndRankedFirstWordFirstThenShorter() {
        fun hit(name: String) = BusinessHit("+3491${name.length}", name, null, "p")
        val ranked = BusinessQuery.rank(
            listOf(hit("Gran Farmacia Central"), hit("Farmacia Sol"), hit("Farsa"), hit("Bar Pepe"), hit("Farmacia Doctor Martinez")),
            "327"
        )
        assertEquals(listOf("Farsa", "Farmacia Sol", "Farmacia Doctor Martinez", "Gran Farmacia Central"), ranked.map { it.name })
    }

    @Test
    fun businessesNeverReplaceAContactWithTheSameNumber() {
        val hits = listOf(BusinessHit("+34912345678", "Farmacia", "health", "p"), BusinessHit("+34933333333", "Farmacia Dos", "health", "p"))
        val kept = BusinessSuggestions.withoutContacts(hits, listOf("912 34 56 78", "+34600000000"))
        assertEquals(listOf("Farmacia Dos"), kept.map { it.name })
    }

    @Test
    fun withoutContactsEverythingIsKept() {
        val hits = listOf(BusinessHit("+34912345678", "Farmacia", null, "p"))
        assertEquals(hits, BusinessSuggestions.withoutContacts(hits, emptyList()))
    }
}
