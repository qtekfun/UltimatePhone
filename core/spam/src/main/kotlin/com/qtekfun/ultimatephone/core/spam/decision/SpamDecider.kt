package com.qtekfun.ultimatephone.core.spam.decision

import com.qtekfun.ultimatephone.core.phonenumber.NormalizedNumber

/** Everything [SpamDecider] consults. Plain interfaces, so the engine has no Android dependency. */
data class DecisionContext(
    val contacts: ContactChecker,
    val whitelist: Whitelist,
    val ownList: OwnList,
    val businesses: BusinessLookup,
    val sources: SourceLookup,
    val prefixRules: PrefixRules,
    val settings: SpamSettings = SpamSettings(),
    /** Raw number as reported by the system, needed to recognise short emergency codes. Never logged. */
    val rawNumber: String? = null,
    /** SIM country or user chosen region. */
    val region: String? = null,
    val emergency: EmergencyNumbers = LibEmergencyNumbers
)

/**
 * The single decision function shared by the call screening service and the in-call screen.
 *
 * Steps run in order and the first match wins: emergency, contact, whitelist, own list, identified business,
 * downloaded sources, prefix rules, nothing. A hidden or invalid number ([NormalizedNumber.Unknown]) is never spam.
 */
object SpamDecider {
    const val SOURCE_OWN = "own"

    fun decide(number: NormalizedNumber, ctx: DecisionContext): Decision {
        if (ctx.emergency.isEmergency(ctx.rawNumber, number, ctx.region)) return pass(DecisionReason.EMERGENCY)
        val valid = number as? NormalizedNumber.Valid ?: return pass(DecisionReason.UNKNOWN_NUMBER)
        val e164 = valid.e164
        return when {
            ctx.contacts.isContact(e164) -> pass(DecisionReason.CONTACT)
            ctx.whitelist.isWhitelisted(e164) -> pass(DecisionReason.WHITELIST)
            else -> decideFlagged(valid, ctx)
        }
    }

    private fun decideFlagged(valid: NormalizedNumber.Valid, ctx: DecisionContext): Decision {
        val e164 = valid.e164
        val policy = ctx.settings.policy
        ctx.ownList.match(e164)?.let { own ->
            return flagged(policy.actionFor(SpamLevel.OWN), SpamLevel.OWN, SOURCE_OWN, own.label, null, DecisionReason.OWN_LIST)
        }
        val business = ctx.businesses.find(e164)
        if (business != null && ctx.settings.identifiedBusinessIsNotSpam) return identified(business)
        // With the setting off the business is still shown on top of whatever flags it.
        val name = business?.name
        val category = business?.category
        val bySource = ctx.sources.find(e164)?.let { src ->
            flagged(policy.actionFor(src.level), src.level, src.sourceId, src.label ?: name, category, DecisionReason.SOURCE)
        }
        return bySource
            ?: ctx.prefixRules.match(e164, valid.region)?.let { rule ->
                flagged(policy.actionFor(rule.level, rule.ruleId), rule.level, rule.ruleId, name, category, DecisionReason.PREFIX_RULE)
            }
            ?: business?.let(::identified)
            ?: pass(DecisionReason.NO_MATCH)
    }

    private fun identified(business: BusinessMatch) =
        Decision(SpamAction.NONE, null, business.sourceId, business.name, business.category, DecisionReason.BUSINESS)

    private fun pass(reason: DecisionReason) = Decision(SpamAction.NONE, null, null, null, null, reason)

    @Suppress("LongParameterList") // Mirrors the Decision fields one to one.
    private fun flagged(
        action: SpamAction,
        level: SpamLevel,
        sourceId: String,
        label: String?,
        category: String?,
        reason: DecisionReason
    ) = Decision(action, level, sourceId, label, category, reason)
}
