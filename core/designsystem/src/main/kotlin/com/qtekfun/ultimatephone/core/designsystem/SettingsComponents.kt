package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A section title above a group of rows or a list: small, bold, in the primary colour, announced as a heading.
 * Use it for list sections that are not in a [SettingsGroup]; a group takes its own `title`.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().padding(start = Spacing.Medium, end = Spacing.Medium, top = Spacing.Medium, bottom = Spacing.Small)
            .semantics { heading() }
    )
}

/**
 * A rounded tonal container (24 dp) that holds related rows: [SettingsRow], [SwitchRow], [RadioRow], [SegmentedChoice] or any
 * custom row. Groups are separated by space, not by divider lines. Give it side margins with `Modifier.padding(horizontal =
 * Spacing.Medium)` or by putting it in a column that has them.
 *
 * ```
 * SettingsGroup(title = "Appearance", modifier = Modifier.padding(horizontal = Spacing.Medium)) {
 *     SwitchRow(title = "Use system colours", checked = checked, onCheckedChange = onChange)
 * }
 * ```
 *
 * @param title optional [SectionHeader] drawn above the container.
 */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) SectionHeader(title)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = Spacing.XSmall), content = content)
        }
    }
}

/** The 40 dp tonal circle with an icon that leads a row. Decorative: the row's title says what it is. */
@Composable
private fun LeadingIcon(icon: ImageVector) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
    }
}

/** Title, optional summary: the text block shared by all rows. */
@Composable
private fun RowText(title: String, summary: String?, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val RowPadding = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.Small)

/**
 * A row of a [SettingsGroup]: optional leading icon in a tonal circle, a title, an optional summary and an optional trailing
 * element.
 *
 * With [onClick] it is one TalkBack button of at least 56 dp (the whole row is the target, children are merged); without it,
 * it is a read-only row (a label with a value) merged into one item. When it is clickable and has no [trailing], a chevron
 * is drawn.
 *
 * @param trailing a value, a chip or a control; if it is itself a control, make the row non-clickable or accept two targets.
 * @param onClickLabel what activating the row does, when the title alone is not obvious ("Open", "Change").
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val base = modifier.fillMaxWidth().heightIn(min = Spacing.RowHeight)
    val interaction = if (onClick != null) {
        base.clickable(enabled = enabled, role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
    } else {
        base.semantics(mergeDescendants = true) {}
    }
    Row(
        modifier = interaction.then(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        if (icon != null) LeadingIcon(icon)
        RowText(title, summary, Modifier.weight(1f))
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A row with a switch. The whole row toggles (`toggleable`, role Switch, at least 56 dp), so TalkBack says "On"/"Off" for the
 * row and the switch itself is not a second target. The switch shows a check mark when on: state is not colour alone.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Spacing.RowHeight)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .then(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        if (icon != null) LeadingIcon(icon)
        RowText(title, summary, Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            thumbContent = if (checked) {
                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchIconSize)) }
            } else {
                null
            }
        )
    }
}

private val SwitchIconSize = 16.dp

/** A row with a radio button; the whole row selects (`selectable`, role RadioButton, at least 56 dp). */
@Composable
fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, summary: String? = null, enabled: Boolean = true) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Spacing.RowHeight)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .then(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        RowText(title, summary, Modifier.weight(1f))
    }
}

/**
 * Pick one of a few options (2 to 4) in a segmented control. Each segment is a radio button for TalkBack ("selected" /
 * "not selected") and at least 48 dp high; the selected one shows a check mark. For more options use [RadioRow]s.
 *
 * ```
 * SegmentedChoice(options = ThemeMode.entries, selected = mode, onSelected = onMode, label = { stringResource(it.label) })
 * ```
 */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T?,
    onSelected: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = enabled,
                modifier = Modifier.heightIn(min = Spacing.MinTarget),
                label = { Text(label(option), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}
