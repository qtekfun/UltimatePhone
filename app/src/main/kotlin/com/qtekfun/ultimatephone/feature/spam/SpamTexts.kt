package com.qtekfun.ultimatephone.feature.spam

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.spam.decision.Decision

/** The sentence that explains a [ReasonDisplay]. */
@Composable
fun reasonText(reason: ReasonDisplay): String = when (reason) {
    ReasonDisplay.OwnList -> stringResource(R.string.spam_reason_own)
    is ReasonDisplay.Source -> stringResource(R.string.spam_reason_source, reason.sourceId)
    is ReasonDisplay.Rule -> stringResource(R.string.spam_reason_rule, stringResource(reason.label))
    is ReasonDisplay.Business ->
        if (reason.category.isNullOrBlank()) {
            stringResource(R.string.spam_reason_business, reason.name)
        } else {
            stringResource(R.string.spam_reason_business_category, reason.name, reason.category)
        }
    ReasonDisplay.Contact -> stringResource(R.string.spam_reason_contact)
    ReasonDisplay.Allowed -> stringResource(R.string.spam_reason_allowed)
    ReasonDisplay.NothingFound -> stringResource(R.string.spam_reason_nothing)
}

@Composable
fun reasonText(decision: Decision): String = reasonText(describeReason(decision))

/** Short form for a row or a banner: where the flag comes from, without a sentence around it. */
@Composable
fun shortReasonText(decision: Decision): String = when (val reason = describeReason(decision)) {
    ReasonDisplay.OwnList -> stringResource(R.string.spam_level_own)
    is ReasonDisplay.Source -> stringResource(R.string.spam_short_source, reason.sourceId)
    is ReasonDisplay.Rule -> stringResource(reason.label)
    is ReasonDisplay.Business -> reason.name
    ReasonDisplay.Contact, ReasonDisplay.Allowed, ReasonDisplay.NothingFound -> ""
}
