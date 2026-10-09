package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The brand palette: deep teal (the app icon background) with a mint accent. The tonal values were generated from a teal
 * seed in CIE L*C*h space (tone = L*), the same idea as Material's HCT tones, and checked against WCAG AA by
 * `BrandColorsTest`. The brand scheme is the default; "Use system colours" switches to Material You instead.
 */
object BrandColors {
    /** Background of the launcher icon. */
    val DeepTeal = Color(0xFF0B3D3A)

    /** Accent of the launcher icon. Decorative glows and the ringing pulse; text never sits on it without a check. */
    val Mint = Color(0xFF69F0AE)
}

/** Light brand scheme. Teal primary, grey-teal secondary, mint-green tertiary, near-white teal-tinted surfaces. */
internal val LightBrandColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF006C6C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB1EEEB),
    onPrimaryContainer = Color(0xFF002222),
    inversePrimary = Color(0xFF66D8D6),
    secondary = Color(0xFF446465),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC6E9EA),
    onSecondaryContainer = Color(0xFF002021),
    tertiary = Color(0xFF196B47),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB7EFCF),
    onTertiaryContainer = Color(0xFF00230B),
    background = Color(0xFFF2FBFB),
    onBackground = Color(0xFF171D1C),
    surface = Color(0xFFF2FBFB),
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFD2E6E6),
    onSurfaceVariant = Color(0xFF394A49),
    surfaceTint = Color(0xFF006C6C),
    inverseSurface = Color(0xFF2B3231),
    inverseOnSurface = Color(0xFFEAF2F2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF58696A),
    outlineVariant = Color(0xFFB7CAC9),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF2FBFB),
    surfaceDim = Color(0xFFD3DCDB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEDF5F5),
    surfaceContainer = Color(0xFFE7F0EF),
    surfaceContainerHigh = Color(0xFFE1EAE9),
    surfaceContainerHighest = Color(0xFFDCE4E4)
)

/** Dark brand scheme. The primary container is the icon's deep teal; the primary is a light teal. */
internal val DarkBrandColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF66D8D6),
    onPrimary = Color(0xFF003C3C),
    primaryContainer = BrandColors.DeepTeal,
    onPrimaryContainer = Color(0xFFB1EEEB),
    inversePrimary = Color(0xFF006C6C),
    secondary = Color(0xFFAACDCE),
    onSecondary = Color(0xFF163536),
    secondaryContainer = Color(0xFF2D4C4D),
    onSecondaryContainer = Color(0xFFC6E9EA),
    tertiary = Color(0xFF88D7AC),
    onTertiary = Color(0xFF003B1B),
    tertiaryContainer = Color(0xFF005331),
    onTertiaryContainer = Color(0xFFA4F3C7),
    background = Color(0xFF0D1514),
    onBackground = Color(0xFFDCE4E4),
    surface = Color(0xFF0D1514),
    onSurface = Color(0xFFDCE4E4),
    surfaceVariant = Color(0xFF394A49),
    onSurfaceVariant = Color(0xFFB7CAC9),
    surfaceTint = Color(0xFF66D8D6),
    inverseSurface = Color(0xFFDCE4E4),
    inverseOnSurface = Color(0xFF2B3231),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF829493),
    outlineVariant = Color(0xFF394A49),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343A3A),
    surfaceDim = Color(0xFF0D1514),
    surfaceContainerLowest = Color(0xFF06100F),
    surfaceContainerLow = Color(0xFF171D1C),
    surfaceContainer = Color(0xFF1B2120),
    surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF2F3636)
)

/** The brand colour scheme for a light or a dark theme. */
fun brandColorScheme(dark: Boolean): ColorScheme = if (dark) DarkBrandColorScheme else LightBrandColorScheme
