package com.qtekfun.ultimatephone.feature.spam

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Report
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.BannerKind
import com.qtekfun.ultimatephone.core.designsystem.StatusChip
import com.qtekfun.ultimatephone.screening.SpamVerdict

/**
 * A history row's spam marker: an error-coloured chip with the report icon and the reason for a flagged number, and a calm
 * neutral chip for an informational result such as a commercial call, which is not unwanted.
 */
@Composable
fun SpamBadge(verdict: SpamVerdict, modifier: Modifier = Modifier) {
    val reason = shortReasonText(verdict.decision)
    if (!verdict.isSpam) {
        StatusChip(text = reason.ifBlank { stringResource(R.string.spam_badge_info) }, modifier = modifier)
        return
    }
    val text = if (reason.isBlank()) stringResource(R.string.spam_badge) else stringResource(R.string.spam_badge_reason, reason)
    StatusChip(text = text, icon = Icons.Filled.Report, kind = BannerKind.Error, modifier = modifier)
}
