package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Up to two initials of a display name; digits and symbols give a neutral glyph. */
fun initialsOf(name: String): String {
    val letters = name.trim().split(Regex("\\s+")).mapNotNull { part -> part.firstOrNull { it.isLetter() } }
    return when {
        letters.isEmpty() -> "#"
        letters.size == 1 -> letters.first().uppercase()
        else -> (letters.first().toString() + letters.last()).uppercase()
    }
}

/** Colour picked from the name, so the same person always gets the same avatar colour. */
@Composable
private fun avatarColor(name: String): Color {
    val palette = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer
    )
    return palette[Math.floorMod(name.hashCode(), palette.size)]
}

/** Circle with initials. A photo, when there is one, is drawn by the caller on top via [content]. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp, content: @Composable () -> Unit = {}) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(avatarColor(name)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = initialsOf(name), style = MaterialTheme.typography.titleMedium)
        content()
    }
}
