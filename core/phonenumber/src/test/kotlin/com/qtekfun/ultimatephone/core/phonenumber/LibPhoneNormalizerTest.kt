package com.qtekfun.ultimatephone.core.phonenumber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibPhoneNormalizerTest {
    private val normalizer = LibPhoneNormalizer()

    private fun e164(raw: String?, region: String?) = normalizer.normalize(raw, region).e164OrNull()

    @Test
    fun spanishMobileInAnyFormatBecomesTheSameE164() {
        val expected = "+34612345678"
        assertEquals(expected, e164("612345678", "ES"))
        assertEquals(expected, e164("612 34 56 78", "ES"))
        assertEquals(expected, e164("+34 612 34 56 78", "ES"))
        assertEquals(expected, e164("0034612345678", "ES"))
        assertEquals(expected, e164("+34612345678", null))
        assertEquals(expected, e164("(+34) 612-345-678", "DE"))
    }

    @Test
    fun usesTheDefaultRegionForNationalNumbers() {
        assertEquals("+4915112345678", e164("0151 12345678", "DE"))
        assertEquals("+14155552671", e164("(415) 555-2671", "US"))
        assertEquals("+436641234567", e164("0664 1234567", "AT"))
    }

    @Test
    fun regionIsReportedForValidNumbers() {
        val result = normalizer.normalize("+34 612 34 56 78", "DE")
        assertEquals(NormalizedNumber.Valid("+34612345678", "ES"), result)
    }

    @Test
    fun hiddenOrInvalidNumbersAreUnknownNotSpam() {
        listOf(null, "", "   ", "-1", "-2", "private", "unknown", "Unknown caller", "12345abc", "+", "00").forEach {
            assertEquals("expected Unknown for '$it'", NormalizedNumber.Unknown, normalizer.normalize(it, "ES"))
        }
    }

    @Test
    fun shortAndEmergencyCodesAreNotLookedUp() {
        listOf("112", "091", "1004", "*123#").forEach {
            assertEquals(NormalizedNumber.Unknown, normalizer.normalize(it, "ES"))
        }
    }

    @Test
    fun numbersWithoutRegionAndWithoutCountryCodeAreUnknown() {
        assertEquals(NormalizedNumber.Unknown, normalizer.normalize("612345678", null))
    }

    @Test
    fun lowercaseRegionIsAccepted() {
        assertEquals("+34612345678", e164("612345678", "es"))
    }

    @Test
    fun displayIsNationalForTheDefaultRegionAndInternationalOtherwise() {
        assertEquals("612 34 56 78", normalizer.formatForDisplay("+34612345678", "ES"))
        assertEquals("+34 612 34 56 78", normalizer.formatForDisplay("+34612345678", "DE"))
    }

    @Test
    fun displayKeepsWhatWeCannotParse() {
        assertEquals("112", normalizer.formatForDisplay("112", "ES"))
        assertEquals("", normalizer.formatForDisplay(null, "ES"))
        assertNull(normalizer.normalize("abc", "ES").e164OrNull())
    }
}
