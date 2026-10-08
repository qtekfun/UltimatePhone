package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The two semantic call colours: green to answer or place a call, red to decline or end one. They are fixed hues that do not
 * follow the dynamic wallpaper colours (a call button must always read as green or red), with a light and a dark variant
 * that both keep at least 4.5:1 contrast between the container and its content.
 */
@Immutable
data class CallColors(val answer: Color, val onAnswer: Color, val decline: Color, val onDecline: Color)

private val LightCallColors = CallColors(
    answer = Color(0xFF1B6E2E),
    onAnswer = Color(0xFFFFFFFF),
    decline = Color(0xFFB3261E),
    onDecline = Color(0xFFFFFFFF)
)

private val DarkCallColors = CallColors(
    answer = Color(0xFF5BC470),
    onAnswer = Color(0xFF00210A),
    decline = Color(0xFFF2706B),
    onDecline = Color(0xFF410002)
)

/** The call colours for a light or a dark surface. */
fun callColorsFor(dark: Boolean): CallColors = if (dark) DarkCallColors else LightCallColors

/** WCAG contrast ratio between two opaque colours. */
fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.luminance()
    val l2 = b.luminance()
    return (maxOf(l1, l2) + CONTRAST_OFFSET) / (minOf(l1, l2) + CONTRAST_OFFSET)
}

private const val CONTRAST_OFFSET = 0.05f
private const val DARK_SURFACE_LUMINANCE = 0.5f

/** The call colours matching the current theme (follows the in-app light/dark choice, not only the system one). */
@Composable
fun callColors(): CallColors = callColorsFor(dark = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE)
