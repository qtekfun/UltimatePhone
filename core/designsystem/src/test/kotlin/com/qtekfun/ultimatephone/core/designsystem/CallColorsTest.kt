package com.qtekfun.ultimatephone.core.designsystem

import org.junit.Assert.assertTrue
import org.junit.Test

class CallColorsTest {
    private val minContrast = 4.5f

    @Test
    fun lightAndDarkVariantsKeepReadableContrast() {
        listOf(false, true).forEach { dark ->
            val colors = callColorsFor(dark)
            assertTrue("answer dark=$dark", contrastRatio(colors.answer, colors.onAnswer) >= minContrast)
            assertTrue("decline dark=$dark", contrastRatio(colors.decline, colors.onDecline) >= minContrast)
        }
    }

    @Test
    fun variantsDiffer() {
        assertTrue(callColorsFor(true) != callColorsFor(false))
    }
}
