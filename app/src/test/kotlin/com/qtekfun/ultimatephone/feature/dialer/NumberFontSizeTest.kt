package com.qtekfun.ultimatephone.feature.dialer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberFontSizeTest {
    @Test
    fun shortNumbersUseTheLargestSize() {
        assertEquals(32, numberFontSizeSp(0))
        assertEquals(32, numberFontSizeSp(10))
    }

    @Test
    fun sizeNeverGrowsWithLength() {
        val sizes = (0..40).map(::numberFontSizeSp)
        assertTrue(sizes.zipWithNext().all { (a, b) -> b <= a })
        assertEquals(18, numberFontSizeSp(40))
    }
}
