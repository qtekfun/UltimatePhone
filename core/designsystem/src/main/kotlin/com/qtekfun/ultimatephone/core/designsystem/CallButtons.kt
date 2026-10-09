package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What a call button does: [Answer] is green, [Decline] and [End] are red. They use the fixed call colours ([callColors]),
 * which do not follow the wallpaper: a call button must always read as green or red.
 */
enum class CallButtonKind { Answer, Decline, End }

private fun CallButtonKind.icon(): ImageVector = if (this == CallButtonKind.Answer) Icons.Filled.Call else Icons.Filled.CallEnd

/**
 * A big round Answer, Decline or End button with its label underneath (80 dp by default, comfortable for a thumb). The icon
 * carries the spoken name; the visible label is hidden from TalkBack so it is not read twice. It shrinks a little while
 * pressed unless motion is reduced.
 */
@Composable
fun CallButton(kind: CallButtonKind, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 80.dp) {
    val colors = callColors()
    val container = if (kind == CallButtonKind.Answer) colors.answer else colors.decline
    val content = if (kind == CallButtonKind.Answer) colors.onAnswer else colors.onDecline
    val interaction = remember { MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        FilledIconButton(
            onClick = onClick,
            interactionSource = interaction,
            modifier = Modifier.size(size).pressScale(interaction),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = content)
        ) {
            Icon(kind.icon(), contentDescription = label, modifier = Modifier.size(size / 2))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.Small).clearAndSetSemantics {}
        )
    }
}

/**
 * A wide pill button in the call colours. With [showLabel] it shows an icon and the text (the "End call" bar at the bottom
 * of the call screen); without it, only the icon, named for TalkBack by [label] (the green call button of the keypad).
 *
 * @param height minimum height; 64 to 80 dp is right for the main call action.
 */
@Composable
fun CallPillButton(kind: CallButtonKind, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 72.dp, showLabel: Boolean = true) {
    val colors = callColors()
    val container = if (kind == CallButtonKind.Answer) colors.answer else colors.decline
    val content = if (kind == CallButtonKind.Answer) colors.onAnswer else colors.onDecline
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = height).pressScale(interaction),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)
    ) {
        Icon(kind.icon(), contentDescription = if (showLabel) null else label, modifier = Modifier.size(32.dp))
        if (showLabel) Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.Medium))
    }
}

private val ActionCornerOn = 22.dp

/**
 * A round tonal action of the call screen (mute, hold, keypad, speaker, record, add call) with a label underneath.
 *
 * - [checked] null: a one-shot action (add call); it is a plain button.
 * - [checked] true/false: an on/off control. On is filled with the primary colour and the button morphs from a circle to a
 *   rounded square, so the state is visible without relying on colour only; [stateDescription] ("On"/"Off") is what
 *   TalkBack says. The change is animated unless motion is reduced.
 *
 * The icon carries the spoken name; the visible label is hidden from TalkBack. Labels wrap to three lines at large fonts.
 *
 * @param stateDescription spoken state for a toggle, or the current value of a plain button (for example the audio route).
 */
@Composable
fun CallActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
    enabled: Boolean = true,
    stateDescription: String? = null,
    size: Dp = 68.dp
) {
    val on = checked == true
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (on) scheme.primary else scheme.surfaceContainerHighest, motionEffectsSpec(), label = "actionContainer")
    val content by animateColorAsState(if (on) scheme.onPrimary else scheme.onSurface, motionEffectsSpec(), label = "actionContent")
    val corner by animateDpAsState(if (on) ActionCornerOn else size / 2, motionSpatialSpec(), label = "actionCorner")
    val shape = RoundedCornerShape(corner)
    val interaction = remember { MutableInteractionSource() }
    val semanticsModifier = if (stateDescription != null) Modifier.semantics { this.stateDescription = stateDescription } else Modifier
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        val buttonModifier = Modifier.size(size).pressScale(interaction).then(semanticsModifier)
        if (checked != null) {
            FilledIconToggleButton(
                checked = checked,
                onCheckedChange = { onClick() },
                enabled = enabled,
                shape = shape,
                interactionSource = interaction,
                modifier = buttonModifier,
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = container,
                    contentColor = content,
                    checkedContainerColor = container,
                    checkedContentColor = content
                )
            ) { Icon(icon, contentDescription = label) }
        } else {
            FilledIconButton(
                onClick = onClick,
                enabled = enabled,
                shape = shape,
                interactionSource = interaction,
                modifier = buttonModifier,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = content)
            ) { Icon(icon, contentDescription = label) }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.XSmall).clearAndSetSemantics {}
        )
    }
}
