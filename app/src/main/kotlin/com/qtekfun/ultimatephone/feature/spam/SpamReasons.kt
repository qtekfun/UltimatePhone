package com.qtekfun.ultimatephone.feature.spam

import androidx.annotation.StringRes
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.spam.decision.Decision
import com.qtekfun.ultimatephone.core.spam.decision.DecisionReason
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel

/** Rule ids as shipped in the rules file and the packs. The id is stable; the text shown for it lives in the string files. */
@StringRes
fun ruleLabelRes(ruleId: String): Int = when (ruleId) {
    "es-400-commercial" -> R.string.spam_rule_es_400_commercial
    else -> R.string.spam_rule_generic
}

@StringRes
fun levelLabelRes(level: SpamLevel): Int = when (level) {
    SpamLevel.OWN -> R.string.spam_level_own
    SpamLevel.COMMUNITY -> R.string.spam_level_community
    SpamLevel.RULE -> R.string.spam_level_rule
}

@StringRes
fun actionLabelRes(action: SpamAction): Int = when (action) {
    SpamAction.NONE -> R.string.spam_action_none
    SpamAction.WARN -> R.string.spam_action_warn
    SpamAction.SILENCE -> R.string.spam_action_silence
    SpamAction.REJECT -> R.string.spam_action_reject
}

/** Why a number got the decision it got, as something the UI can turn into text. */
sealed interface ReasonDisplay {
    data object OwnList : ReasonDisplay

    data class Source(val sourceId: String) : ReasonDisplay

    data class Rule(@StringRes val label: Int, val ruleId: String) : ReasonDisplay

    data class Business(val name: String, val category: String?) : ReasonDisplay

    data object Contact : ReasonDisplay

    data object Allowed : ReasonDisplay

    data object NothingFound : ReasonDisplay
}

fun describeReason(decision: Decision): ReasonDisplay = when (decision.reason) {
    DecisionReason.OWN_LIST -> ReasonDisplay.OwnList
    DecisionReason.SOURCE -> ReasonDisplay.Source(decision.sourceId.orEmpty())
    DecisionReason.PREFIX_RULE -> decision.sourceId.orEmpty().let { ReasonDisplay.Rule(ruleLabelRes(it), it) }
    DecisionReason.BUSINESS -> ReasonDisplay.Business(decision.label.orEmpty(), decision.category)
    DecisionReason.CONTACT -> ReasonDisplay.Contact
    DecisionReason.WHITELIST -> ReasonDisplay.Allowed
    DecisionReason.EMERGENCY, DecisionReason.UNKNOWN_NUMBER, DecisionReason.NO_MATCH -> ReasonDisplay.NothingFound
}
