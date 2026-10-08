package com.qtekfun.ultimatephone.core.spam.decision

/** How a number came to be flagged. The user picks an action per level in Settings. */
enum class SpamLevel {
    /** The user's own list (exact numbers and prefixes). */
    OWN,

    /** A downloaded source, unless the source declares another level. */
    COMMUNITY,

    /** A prefix rule (for example the informational Spanish commercial range 400). */
    RULE
}

/** What the call screening layer should do. [NONE] means leave the call alone. */
enum class SpamAction { NONE, WARN, SILENCE, REJECT }

/** Which step of the decision produced the result, for the "why was this flagged" screen. */
enum class DecisionReason {
    EMERGENCY,
    UNKNOWN_NUMBER,
    CONTACT,
    WHITELIST,
    OWN_LIST,
    BUSINESS,
    SOURCE,
    PREFIX_RULE,
    NO_MATCH
}

/**
 * Outcome of [SpamDecider.decide].
 *
 * @property level null when nothing flagged the number.
 * @property sourceId id of the source, the rule or [SpamDecider.SOURCE_OWN]; null when not applicable.
 * @property label business name or source label, when known. Rule ids are mapped to strings by the UI.
 */
data class Decision(
    val action: SpamAction,
    val level: SpamLevel?,
    val sourceId: String?,
    val label: String?,
    val category: String?,
    val reason: DecisionReason
) {
    val isFlagged: Boolean get() = level != null
}

/** Per-level actions, plus optional overrides per rule id (the user can silence 400 without touching other rules). */
data class ActionPolicy(val byLevel: Map<SpamLevel, SpamAction> = emptyMap(), val byRuleId: Map<String, SpamAction> = emptyMap()) {
    fun actionFor(level: SpamLevel, ruleId: String? = null): SpamAction {
        val override = if (ruleId != null) byRuleId[ruleId] else null
        return override ?: byLevel[level] ?: DEFAULT_ACTION
    }

    companion object {
        val DEFAULT_ACTION = SpamAction.WARN
    }
}

/** User settings the engine reads. */
data class SpamSettings(val identifiedBusinessIsNotSpam: Boolean = true, val policy: ActionPolicy = ActionPolicy())
