package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing grid: 4, 8, 16 and 24 dp, plus larger steps and the two target heights. Use these names instead of literals for
 * padding and gaps, so every screen has the same rhythm. Text sizes are `sp` (typography), never from here.
 */
object Spacing {
    /** 4 dp: between a title and its summary, icon and text inside a chip. */
    val XSmall: Dp = 4.dp

    /** 8 dp: between related controls, vertical padding of dense rows. */
    val Small: Dp = 8.dp

    /** 16 dp: screen side gutter, padding inside cards and rows, between groups of controls. */
    val Medium: Dp = 16.dp

    /** 24 dp: between sections, top and bottom padding of headers and empty states. */
    val Large: Dp = 24.dp

    /** 32 dp: breathing room around a hero (avatar, empty-state icon). */
    val XLarge: Dp = 32.dp

    /** 48 dp: the smallest touch target of anything tappable. */
    val MinTarget: Dp = 48.dp

    /** 56 dp: the height of a standard list row. */
    val RowHeight: Dp = 56.dp
}
