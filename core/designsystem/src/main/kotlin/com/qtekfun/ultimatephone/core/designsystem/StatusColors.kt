package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Colours Material 3 has no role for: the amber "warning" used by [InfoBanner] and [StatusChip]. Like the call colours they are
 * fixed hues that do not follow Material You, with light and dark variants that keep 4.5:1 between container and content.
 *
 * @property warningAccent tint for the leading icon (3:1 on the container, it is not text).
 */
@Immutable
data class StatusColors(val warningContainer: Color, val onWarningContainer: Color, val warningAccent: Color)

private val LightStatusColors = StatusColors(
    warningContainer = Color(0xFFFFE9B8),
    onWarningContainer = Color(0xFF3A2A00),
    warningAccent = Color(0xFF7A5200)
)

private val DarkStatusColors = StatusColors(
    warningContainer = Color(0xFF4D3A00),
    onWarningContainer = Color(0xFFFFE08A),
    warningAccent = Color(0xFFF2C14E)
)

/** The status colours for a light or a dark surface. */
fun statusColorsFor(dark: Boolean): StatusColors = if (dark) DarkStatusColors else LightStatusColors

private const val DARK_SURFACE_LUMINANCE_LIMIT = 0.5f

/** The status colours matching the current theme (follows the in-app light/dark choice). */
@Composable
fun statusColors(): StatusColors = statusColorsFor(dark = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE_LIMIT)
