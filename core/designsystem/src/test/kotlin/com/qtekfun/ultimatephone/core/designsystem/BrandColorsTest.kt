package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandColorsTest {
    private val text = 4.5f
    private val graphic = 3f

    private fun pairs(s: ColorScheme): Map<String, Pair<Color, Color>> = mapOf(
        "primary" to (s.primary to s.onPrimary),
        "primaryContainer" to (s.primaryContainer to s.onPrimaryContainer),
        "secondary" to (s.secondary to s.onSecondary),
        "secondaryContainer" to (s.secondaryContainer to s.onSecondaryContainer),
        "tertiary" to (s.tertiary to s.onTertiary),
        "tertiaryContainer" to (s.tertiaryContainer to s.onTertiaryContainer),
        "error" to (s.error to s.onError),
        "errorContainer" to (s.errorContainer to s.onErrorContainer),
        "surface" to (s.surface to s.onSurface),
        "surfaceVariant" to (s.surfaceVariant to s.onSurfaceVariant),
        "inverseSurface" to (s.inverseSurface to s.inverseOnSurface),
        "surfaceContainer" to (s.surfaceContainer to s.onSurface),
        "surfaceContainerHigh" to (s.surfaceContainerHigh to s.onSurface),
        "surfaceContainerHighest" to (s.surfaceContainerHighest to s.onSurface),
        "surfaceContainerHighestVariant" to (s.surfaceContainerHighest to s.onSurfaceVariant),
        "surfaceContainerVariant" to (s.surfaceContainer to s.onSurfaceVariant),
        "primaryOnSurface" to (s.surface to s.primary),
        "primaryOnContainer" to (s.surfaceContainer to s.primary)
    )

    @Test
    fun everyTextPairIsAtLeastAA() {
        listOf(false, true).forEach { dark ->
            pairs(brandColorScheme(dark)).forEach { (name, pair) ->
                val ratio = contrastRatio(pair.first, pair.second)
                assertTrue("$name dark=$dark has contrast $ratio", ratio >= text)
            }
        }
    }

    @Test
    fun outlineIsVisibleOnSurfaces() {
        listOf(false, true).forEach { dark ->
            val s = brandColorScheme(dark)
            assertTrue("outline on surface dark=$dark", contrastRatio(s.outline, s.surface) >= graphic)
            assertTrue("outline on container dark=$dark", contrastRatio(s.outline, s.surfaceContainer) >= graphic)
        }
    }

    @Test
    fun callButtonsStandOutFromTheSurfaces() {
        listOf(false, true).forEach { dark ->
            val s = brandColorScheme(dark)
            val call = callColorsFor(dark)
            assertTrue("answer vs surface dark=$dark", contrastRatio(call.answer, s.surface) >= graphic)
            assertTrue("decline vs surface dark=$dark", contrastRatio(call.decline, s.surface) >= graphic)
        }
    }

    @Test
    fun statusColorsKeepReadableContrast() {
        listOf(false, true).forEach { dark ->
            val c = statusColorsFor(dark)
            assertTrue("warning text dark=$dark", contrastRatio(c.warningContainer, c.onWarningContainer) >= text)
            assertTrue("warning icon dark=$dark", contrastRatio(c.warningContainer, c.warningAccent) >= graphic)
        }
    }

    @Test
    fun bannerAccentsAreVisibleOnTheirContainers() {
        listOf(false, true).forEach { dark ->
            val s = brandColorScheme(dark)
            assertTrue("info icon dark=$dark", contrastRatio(s.secondaryContainer, s.primary) >= graphic)
        }
    }

    @Test
    fun lightAndDarkSchemesDiffer() {
        assertTrue(brandColorScheme(true).surface != brandColorScheme(false).surface)
    }

    @Test
    fun initialsFallBackForUnknownCallers() {
        assertEquals("AL", initialsOf("Ada Lovelace"))
        assertTrue(hasInitials("Ada"))
        assertTrue(!hasInitials("5551234567"))
        assertTrue(!hasInitials(null))
        assertTrue(!hasInitials(""))
    }

    @Test
    fun tabularFiguresDoNotChangeTheDigitsOnly() {
        val style = androidx.compose.ui.text.TextStyle().tabularFigures()
        assertEquals("tnum", style.fontFeatureSettings)
    }
}
