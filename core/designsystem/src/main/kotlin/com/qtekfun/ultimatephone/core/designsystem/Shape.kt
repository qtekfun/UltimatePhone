package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Generous corner radii. Cards and groups use `large` (24 dp) or `extraLarge` (28 dp, keypad keys); chips and small tiles use
 * `medium` and below; buttons that must read as a pill use `CircleShape`.
 */
internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
