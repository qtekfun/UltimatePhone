package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A filter chip in the app's style: pill shape, `primaryContainer` when selected and a check mark in front of the label, so
 * the selection is never shown by colour alone. Use it for filters that change what a list shows ("Missed", a SIM, a group);
 * for a state label that cannot be clicked use [StatusChip].
 */
@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = modifier,
        shape = CircleShape,
        leadingIcon = {
            AnimatedVisibility(visible = selected) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = scheme.surface,
            selectedContainerColor = scheme.primaryContainer,
            selectedLabelColor = scheme.onPrimaryContainer,
            selectedLeadingIconColor = scheme.onPrimaryContainer
        )
    )
}
