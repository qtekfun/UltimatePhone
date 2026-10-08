package com.qtekfun.ultimatephone.core.spam.decision

/** True when the number belongs to a system contact. */
fun interface ContactChecker {
    fun isContact(e164: String): Boolean
}

/** Numbers and prefixes that are never filtered. */
fun interface Whitelist {
    fun isWhitelisted(e164: String): Boolean
}

/** A hit in the user's own list. */
data class OwnMatch(val label: String?, val entryId: String? = null)

/** The user's own spam list: exact numbers and prefixes. Picking the best match is the implementation's business. */
fun interface OwnList {
    fun match(e164: String): OwnMatch?
}

data class BusinessMatch(val name: String, val category: String?, val sourceId: String? = null)

/** Identified businesses from installed packs. */
fun interface BusinessLookup {
    fun find(e164: String): BusinessMatch?
}

/** A hit in a downloaded source. [level] is what the source declares. */
data class SourceMatch(val sourceId: String, val level: SpamLevel = SpamLevel.COMMUNITY, val label: String? = null)

fun interface SourceLookup {
    fun find(e164: String): SourceMatch?
}

/**
 * A matching prefix rule.
 *
 * @property ruleId stable id (`es-400-commercial`) that the UI maps to a localised string.
 * @property informational true when the rule identifies something legitimate rather than unwanted.
 */
data class RuleMatch(val ruleId: String, val level: SpamLevel = SpamLevel.RULE, val informational: Boolean = false, val source: String? = null)

fun interface PrefixRules {
    /** @param region ISO region of the number when known, used to scope country rules. */
    fun match(e164: String, region: String?): RuleMatch?
}
