package com.qtekfun.ultimatephone.feature.spam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.screening.SpamVerdict

/**
 * A history row's spam marker: a red badge with the reason for a flagged number, and calm plain text for an
 * informational result such as a commercial call, which is not unwanted.
 */
@Composable
fun SpamBadge(verdict: SpamVerdict, modifier: Modifier = Modifier) {
    val reason = shortReasonText(verdict.decision)
    if (!verdict.isSpam) {
        Text(
            text = reason.ifBlank { stringResource(R.string.spam_badge_info) },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Filled.Report, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
            val text = if (reason.isBlank()) stringResource(R.string.spam_badge) else stringResource(R.string.spam_badge_reason, reason)
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
