package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's confirmation dialog: 28 dp corners, a title, one explanation, the dismiss button on the left and the confirm button
 * on the right (always in that order). With [destructive] the confirm label is in the error colour; the wording of
 * [confirmLabel] ("Delete") still carries the meaning, colour is only a hint. Both buttons are at least 48 dp high.
 *
 * [onConfirm] is called first, then [onDismiss], so callers only need to clear their "dialog open" flag in [onDismiss].
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false
) {
    val confirmColors = if (destructive) {
        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
    } else {
        ButtonDefaults.textButtonColors()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = Spacing.MinTarget),
                colors = confirmColors
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Spacing.MinTarget)) { Text(dismissLabel) }
        }
    )
}
