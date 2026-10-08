package com.qtekfun.ultimatephone.core.phonenumber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PhoneMaskTest {
    @Test
    fun keepsOnlyTheLastThreeDigits() {
        assertEquals("***456", PhoneMask.mask("+34 600 123 456"))
    }

    @Test
    fun neverContainsTheFullNumber() {
        assertFalse(PhoneMask.mask("+34600123456").contains("600123"))
    }

    @Test
    fun hiddenOrEmptyNumbersAreUnknown() {
        assertEquals("unknown", PhoneMask.mask(null))
        assertEquals("unknown", PhoneMask.mask(""))
        assertEquals("unknown", PhoneMask.mask("private"))
    }
}
