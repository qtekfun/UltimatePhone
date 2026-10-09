package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Up to two initials of a display name; digits and symbols give a neutral glyph ("#"). See [hasInitials]. */
fun initialsOf(name: String): String {
    val letters = name.trim().split(Regex("\\s+")).mapNotNull { part -> part.firstOrNull { it.isLetter() } }
    return when {
        letters.isEmpty() -> "#"
        letters.size == 1 -> letters.first().uppercase()
        else -> (letters.first().toString() + letters.last()).uppercase()
    }
}

/** True when [name] has at least one letter, so initials make sense; a bare number or no name does not. */
fun hasInitials(name: String?): Boolean = name != null && name.any { it.isLetter() }

private class AvatarColors(val container: Color, val content: Color)

/** Colour picked from the name, so the same person always gets the same avatar colour. */
@Composable
private fun avatarColors(name: String): AvatarColors {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        AvatarColors(scheme.primaryContainer, scheme.onPrimaryContainer),
        AvatarColors(scheme.secondaryContainer, scheme.onSecondaryContainer),
        AvatarColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
    )
    return palette[Math.floorMod(name.hashCode(), palette.size)]
}

@Composable
private fun initialsStyle(size: Dp): TextStyle = when {
    size < SMALL_AVATAR -> MaterialTheme.typography.titleMedium
    size < LARGE_AVATAR -> MaterialTheme.typography.headlineSmall
    else -> MaterialTheme.typography.displaySmall
}

private val SMALL_AVATAR = 56.dp
private val LARGE_AVATAR = 112.dp

/**
 * A round avatar. It is decorative: the name or number is always next to it, so TalkBack ignores it.
 *
 * - A [name] with letters shows its initials on a colour picked from the name.
 * - A missing name or a bare number (an unknown caller) shows a person icon, never "#".
 * - [business] shows a business icon instead ([businessIcon] lets a category icon replace the default shop).
 * - A photo is drawn by the caller on top through [content].
 *
 * Icons scale with [size] (half of it). Sizes: 40 dp in lists, 56 to 64 dp in headers, 96 to 144 dp on the call screen.
 */
@Composable
fun AvatarStyled(
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    business: Boolean = false,
    businessIcon: ImageVector = Icons.Filled.Storefront,
    content: @Composable () -> Unit = {}
) {
    val colors = if (business) {
        AvatarColors(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    } else {
        avatarColors(name.orEmpty())
    }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(colors.container).clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        when {
            business -> Icon(businessIcon, contentDescription = null, tint = colors.content, modifier = Modifier.size(size / 2))
            hasInitials(name) -> Text(text = initialsOf(name.orEmpty()), style = initialsStyle(size), color = colors.content)
            else -> Icon(Icons.Filled.Person, contentDescription = null, tint = colors.content, modifier = Modifier.size(size / 2))
        }
        content()
    }
}

/** Circle with initials, or a person icon for unknown numbers. Kept for existing screens; new code uses [AvatarStyled]. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp, content: @Composable () -> Unit = {}) {
    AvatarStyled(name = name, modifier = modifier, size = size, content = content)
}
