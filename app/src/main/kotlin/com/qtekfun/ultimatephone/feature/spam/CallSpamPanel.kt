package com.qtekfun.ultimatephone.feature.spam

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.BannerKind
import com.qtekfun.ultimatephone.core.designsystem.InfoBanner
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.designsystem.UiAction
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
 * The spam part of the call screen. A flagged number gets a clear warning banner, shown before the call is answered. An
 * informational result (a commercial call, an identified business) is a calm info banner, not a warning.
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
        ui.showWarning -> WarningBanner(ui, onNotSpam, onWhyFlagged.takeIf { ui.canExplain }, modifier)
        ui.markedSpam -> Note(R.string.spam_incall_marked, BannerKind.Success, modifier)
        ui.allowed -> Note(R.string.spam_incall_allowed, BannerKind.Success, modifier)
        ui.showInfo -> ui.verdict?.let {
            InfoBanner(kind = BannerKind.Info, title = reasonText(it.decision), modifier = modifier.padding(top = Spacing.Medium))
        }
        ui.canMarkSpam(ringing) -> TextButton(onClick = onMarkSpam, modifier = modifier.heightIn(min = Spacing.MinTarget)) {
            Text(stringResource(R.string.spam_incall_mark_spam))
        }
    }
}

@Composable
private fun Note(text: Int, kind: BannerKind, modifier: Modifier) {
    InfoBanner(kind = kind, title = stringResource(text), modifier = modifier.padding(top = Spacing.Medium), liveRegion = true)
}

@Composable
private fun WarningBanner(ui: CallSpamUi, onNotSpam: () -> Unit, onWhyFlagged: (() -> Unit)?, modifier: Modifier) {
    val decision = ui.verdict?.decision ?: return
    val level = decision.level?.let { stringResource(levelLabelRes(it)) }.orEmpty()
    val source = shortReasonText(decision)
    val reason = stringResource(R.string.spam_incall_warning_body, level, source)
    // "Opens the app. The call goes on." explains what the "Why" button does, so it stays part of the text.
    val body = if (onWhyFlagged != null) reason + "\n" + stringResource(R.string.gaps_incall_why_flagged_hint) else reason
    InfoBanner(
        kind = BannerKind.Warning,
        icon = Icons.Filled.Report,
        title = stringResource(R.string.spam_incall_warning_title),
        body = body,
        action = UiAction(stringResource(R.string.spam_not_spam), onNotSpam),
        secondaryAction = onWhyFlagged?.let { UiAction(stringResource(R.string.gaps_incall_why_flagged), it) },
        liveRegion = true,
        modifier = modifier.padding(top = Spacing.Medium)
    )
}
