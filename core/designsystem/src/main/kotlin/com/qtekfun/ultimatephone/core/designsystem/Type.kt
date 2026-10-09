package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val Base = Typography()

/**
 * The app's type scale: the Material 3 scale on the platform font, with firmer weights so the hierarchy reads at a glance.
 *
 * - display: the dialed number and the caller's name.
 * - headline: screen and step titles.
 * - title: row titles, card titles, banner titles.
 * - body: summaries and paragraphs.
 * - label: captions, chips, button text, section headers.
 */
internal val AppTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Medium),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.Medium),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Medium),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.Medium)
)

/** Tabular (fixed-width) digits: a running timer or a dialed number does not jitter as the digits change. */
fun TextStyle.tabularFigures(): TextStyle = copy(fontFeatureSettings = "tnum")
