package com.qtekfun.ultimatephone.feature.spam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.spam.decision.DecisionReason
import com.qtekfun.ultimatephone.screening.SpamVerdict

/**
 * What the in-call screen knows about the spam side of one call.
 *
 * @property verdict the decision taken for the number; null for hidden numbers or before it is known.
 * @property markedSpam the user added the number to the spam list during this call.
 * @property allowed the user said "not spam" during this call.
 * @property hasNumber the call has a number that can be listed.
 */
data class CallSpamUi(val verdict: SpamVerdict? = null, val markedSpam: Boolean = false, val allowed: Boolean = false, val hasNumber: Boolean = false) {
    /** The warning is shown for a flagged number until the user overrules it. */
    val showWarning: Boolean get() = verdict?.isSpam == true && !allowed && !markedSpam

    /** The warning can open its explanation (needs the number the explanation is about). */
    val canExplain: Boolean get() = showWarning && hasNumber

    /** Something worth saying calmly: a commercial call, an identified business. */
    val showInfo: Boolean get() = !showWarning && verdict?.let { it.informational || it.decision.reason == DecisionReason.BUSINESS } == true

    /** "Mark as spam" is offered during and after a call for numbers that are not flagged and not contacts. */
    fun canMarkSpam(ringing: Boolean): Boolean =
        hasNumber && !ringing && !markedSpam && verdict?.isSpam != true && verdict?.decision?.reason != DecisionReason.CONTACT
}

/**
 * The spam part of the call screen. A flagged number gets a clear warning, shown before the call is answered. An
 * informational result is a calm line of text, not a warning.
 */
@Composable
fun CallSpamPanel(
    ui: CallSpamUi,
    ringing: Boolean,
    onNotSpam: () -> Unit,
    onMarkSpam: () -> Unit,
    modifier: Modifier = Modifier,
    onWhyFlagged: (() -> Unit)? = null
) {
    when {
        ui.showWarning -> WarningCard(ui, onNotSpam, onWhyFlagged.takeIf { ui.canExplain }, modifier)
        ui.markedSpam -> Note(R.string.spam_incall_marked, modifier)
        ui.allowed -> Note(R.string.spam_incall_allowed, modifier)
        ui.showInfo -> ui.verdict?.let {
            Text(
                reasonText(it.decision),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = modifier.padding(top = 8.dp)
            )
        }
        ui.canMarkSpam(ringing) -> TextButton(onClick = onMarkSpam, modifier = modifier) { Text(stringResource(R.string.spam_incall_mark_spam)) }
    }
}

@Composable
private fun Note(text: Int, modifier: Modifier) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(top = 8.dp)
    )
}

@Composable
private fun WarningCard(ui: CallSpamUi, onNotSpam: () -> Unit, onWhyFlagged: (() -> Unit)?, modifier: Modifier) {
    val decision = ui.verdict?.decision ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
        modifier = modifier.fillMaxWidth().padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Report, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.spam_incall_warning_title), style = MaterialTheme.typography.titleMedium)
            }
            val level = decision.level?.let { stringResource(levelLabelRes(it)) }.orEmpty()
            val source = shortReasonText(decision)
            Text(
                text = stringResource(R.string.spam_incall_warning_body, level, source),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onNotSpam,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                    contentColor = MaterialTheme.colorScheme.errorContainer
                )
            ) { Text(stringResource(R.string.spam_not_spam)) }
            if (onWhyFlagged != null) {
                TextButton(onClick = onWhyFlagged, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.gaps_incall_why_flagged)) }
                Text(stringResource(R.string.gaps_incall_why_flagged_hint), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}
