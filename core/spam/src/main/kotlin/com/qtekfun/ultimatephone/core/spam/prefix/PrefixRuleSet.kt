package com.qtekfun.ultimatephone.core.spam.prefix

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.qtekfun.ultimatephone.core.spam.decision.PrefixRules
import com.qtekfun.ultimatephone.core.spam.decision.RuleMatch
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel

/**
 * A prefix rule as shipped in the rules file or a pack.
 *
 * @property country ISO region the rule is scoped to, or null for a rule written with a full international [prefix].
 * @property prefix national digits (`400`) when [country] is set, otherwise an international prefix (`+34400`).
 * @property id stable id the UI maps to a localised string, never a user facing text.
 * @property source legal or documentary reference, for example `BOE-A-2026-8409`.
 */
data class PrefixRule(
    val id: String,
    val country: String?,
    val prefix: String,
    val level: SpamLevel = SpamLevel.RULE,
    val informational: Boolean = false,
    val source: String? = null
)

/** In-memory rules. Longest matching prefix wins; a country rule only applies to numbers of that country. */
class PrefixRuleSet(rules: List<PrefixRule>, private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()) : PrefixRules {
    private class Compiled(val rule: PrefixRule, val international: String)

    private val byPrefix: Map<String, List<Compiled>> = rules.mapNotNull(::compile).groupBy { it.international }

    val size: Int get() = byPrefix.values.sumOf { it.size }

    override fun match(e164: String, region: String?): RuleMatch? {
        if (byPrefix.isEmpty()) return null
        val candidates = listOf(e164) + PrefixCandidates.of(e164)
        for (candidate in candidates) {
            val hit = byPrefix[candidate]?.firstOrNull { applies(it.rule, region) } ?: continue
            return RuleMatch(hit.rule.id, hit.rule.level, hit.rule.informational, hit.rule.source)
        }
        return null
    }

    // A number of unknown region can only be matched by a rule that is not tied to a country.
    private fun applies(rule: PrefixRule, region: String?): Boolean = rule.country == null || region == null || rule.country.equals(region, ignoreCase = true)

    private fun compile(rule: PrefixRule): Compiled? {
        val digits = rule.prefix.removePrefix("+")
        if (digits.isEmpty() || !digits.all { it.isDigit() }) return null
        val country = rule.country
        val international = when {
            country == null -> if (rule.prefix.startsWith("+")) rule.prefix else null
            else -> util.getCountryCodeForRegion(country.uppercase()).takeIf { it > 0 }?.let { "+$it$digits" }
        }
        return international?.let { Compiled(rule, it) }
    }
}
