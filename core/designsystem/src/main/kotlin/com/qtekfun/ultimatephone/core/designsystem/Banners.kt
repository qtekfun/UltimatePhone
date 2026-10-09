package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * What a banner or chip says about its subject. Pick by what the user must do, not by how loud it should look:
 *
 * - [Info]: neutral news or an optional suggestion (set as default phone app, a business was identified).
 * - [Warning]: something deserves attention but nothing failed (spam suspected, battery optimisation on).
 * - [Error]: something failed or is blocked (a download failed, a recording could not start).
 * - [Success]: something worked or is active (role granted, backup done).
 */
enum class BannerKind { Info, Warning, Error, Success }

/** A labelled action: a button of a banner or an empty state. */
@Immutable
class UiAction(val label: String, val onClick: () -> Unit)

/** Container, content and icon colours of a [BannerKind] in the current theme. */
@Immutable
private class KindColors(val container: Color, val content: Color, val accent: Color)

@Composable
private fun BannerKind.colors(): KindColors {
    val scheme = MaterialTheme.colorScheme
    val status = statusColors()
    return when (this) {
        BannerKind.Info -> KindColors(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.primary)
        BannerKind.Warning -> KindColors(status.warningContainer, status.onWarningContainer, status.warningAccent)
        BannerKind.Error -> KindColors(scheme.errorContainer, scheme.onErrorContainer, scheme.onErrorContainer)
        BannerKind.Success -> KindColors(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.onTertiaryContainer)
    }
}

/** The icon used when none is given: i, triangle, exclamation, check. */
fun BannerKind.defaultIcon(): ImageVector = when (this) {
    BannerKind.Info -> Icons.Filled.Info
    BannerKind.Warning -> Icons.Filled.Warning
    BannerKind.Error -> Icons.Filled.Error
    BannerKind.Success -> Icons.Filled.CheckCircle
}

/**
 * A rounded (24 dp) tonal banner with an icon, a title, an optional body and up to two actions. Replaces the ad-hoc cards for
 * the default-phone offer, spam warnings, the role prompts and error messages.
 *
 * The kind is conveyed by the icon and the wording as well as by colour. The title is a heading. Buttons are at least 48 dp
 * and are separate TalkBack targets after the text.
 *
 * @param liveRegion true for a banner that appears without the user asking (a spam warning on the call screen, a recording
 * error): TalkBack reads it when it shows up.
 * @param icon the leading icon; the default depends on [kind].
 * @param action the main action (a text button); [secondaryAction] sits before it.
 */
@Composable
fun InfoBanner(
    kind: BannerKind,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector = kind.defaultIcon(),
    action: UiAction? = null,
    secondaryAction: UiAction? = null,
    liveRegion: Boolean = false
) {
    val colors = kind.colors()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.container,
        contentColor = colors.content,
        modifier = modifier.fillMaxWidth().then(if (liveRegion) Modifier.semantics { this.liveRegion = LiveRegionMode.Polite } else Modifier)
    ) {
        Row(modifier = Modifier.padding(Spacing.Medium), horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.padding(top = 2.dp).size(24.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium)
                if (action != null || secondaryAction != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                        secondaryAction?.let { BannerButton(it, colors.content) }
                        action?.let { BannerButton(it, colors.content) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerButton(action: UiAction, color: Color) {
    TextButton(
        onClick = action.onClick,
        modifier = Modifier.heightIn(min = Spacing.MinTarget),
        colors = ButtonDefaults.textButtonColors(contentColor = color)
    ) { Text(action.label, style = MaterialTheme.typography.labelLarge) }
}

/**
 * A small pill with an optional icon and a short text: a category, a SIM, "Recording", "Spam". It is one TalkBack item
 * (icon and text merged). Not clickable; use a filter chip for choices.
 */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, kind: BannerKind = BannerKind.Info) {
    val colors = kind.colors()
    Surface(shape = CircleShape, color = colors.container, contentColor = colors.content, modifier = modifier.semantics(mergeDescendants = true) {}) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Small + Spacing.XSmall, vertical = Spacing.XSmall + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
