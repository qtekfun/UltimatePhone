package com.qtekfun.ultimatephone.screening

import com.qtekfun.ultimatephone.core.contacts.ContactsRepository
import com.qtekfun.ultimatephone.core.phonenumber.NormalizedNumber
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.spam.decision.ActionPolicy
import com.qtekfun.ultimatephone.core.spam.decision.DecisionContext
import com.qtekfun.ultimatephone.core.spam.decision.OwnList
import com.qtekfun.ultimatephone.core.spam.decision.OwnMatch
import com.qtekfun.ultimatephone.core.spam.decision.PrefixRules
import com.qtekfun.ultimatephone.core.spam.decision.RuleMatch
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import com.qtekfun.ultimatephone.core.spam.decision.SpamDecider
import com.qtekfun.ultimatephone.core.spam.decision.SpamSettings
import com.qtekfun.ultimatephone.core.spam.lists.SpamListsRepository
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.data.InstalledPackLookups
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** First rule set that matches wins; [primary] is the installed packs (they can update a built-in rule), then the built-in rules. */
internal class CompositePrefixRules(private val primary: PrefixRules, private val fallback: PrefixRules) : PrefixRules {
    override fun match(e164: String, region: String?): RuleMatch? = primary.match(e164, region) ?: fallback.match(e164, region)
}

/**
 * Builds the [DecisionContext] for one number and runs [SpamDecider], the single decision used by the call screening
 * service, the in-call screen and the history actions.
 *
 * Everything it reads is local: contacts, two indexed list queries, and in-memory lookups of the installed packs.
 * There is no network access, so it is safe to run while a call is ringing.
 */
class DecisionEngine(
    private val contacts: ContactsRepository,
    private val lists: SpamListsRepository,
    private val packs: InstalledPackLookups,
    private val builtInRules: PrefixRules,
    private val settings: SpamSettingsRepository,
    private val normalizer: PhoneNormalizer,
    private val regions: RegionProvider,
    private val store: DecisionStore,
    private val clock: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope? = null
) {
    private val rules: PrefixRules = CompositePrefixRules(
        PrefixRules { e164, region -> packs.prefixRules.match(e164, region) },
        builtInRules
    )

    init {
        // A pack that was installed or removed can change the answer for a call that is still ringing.
        scope?.let { packs.changes.onEach { store.clearLive() }.launchIn(it) }
    }

    /**
     * The decision for [raw], or null when the number cannot be actioned (hidden, malformed, a short code).
     * With [record] the verdict is stored; callers on a deadline pass false and call [record] after answering.
     */
    suspend fun decide(raw: String?, record: Boolean = true): SpamVerdict? {
        val region = regions.defaultRegion()
        val valid = normalizer.normalize(raw, region) as? NormalizedNumber.Valid ?: return null
        return decideValid(raw, valid, region, record)
    }

    /**
     * Touches everything the first decision needs (the built-in rules, the installed packs, the lists database and the
     * stored settings) so that it is paid for in the background rather than while a call rings. Optional: a decision
     * taken before it has finished builds the same state on demand.
     */
    suspend fun warmUp() {
        rules.match(WARM_UP_NUMBER, null)
        lists.isWhitelisted(WARM_UP_NUMBER)
        settings.current()
    }

    private suspend fun decideValid(raw: String?, valid: NormalizedNumber.Valid, region: String?, record: Boolean): SpamVerdict {
        val e164 = valid.e164
        val isContact = contacts.lookupByNumber(raw?.takeIf { it.isNotBlank() } ?: e164) != null
        val whitelisted = !isContact && lists.isWhitelisted(e164)
        val own = if (isContact || whitelisted) null else lists.matchOwn(e164)
        val rule = if (isContact || whitelisted) null else rules.match(e164, valid.region)
        val prefs = settings.current()
        val context = DecisionContext(
            contacts = { isContact },
            whitelist = { whitelisted },
            ownList = OwnList { if (own != null) OwnMatch(own.label, own.id) else null },
            businesses = packs.businesses,
            sources = packs.sources,
            prefixRules = PrefixRules { _, _ -> rule },
            settings = prefs.toSpamSettings().withInformationalDefault(rule),
            rawNumber = raw,
            region = region
        )
        val decision = SpamDecider.decide(valid, context)
        val verdict = SpamVerdict(e164, decision, informational = rule?.informational == true && decision.sourceId == rule.ruleId, clock())
        if (record) store.record(verdict)
        return verdict
    }

    /**
     * The decision for a call that is ringing or in progress: the one the screening service just took when there is one
     * (so the work is done once per call), else a new one.
     */
    suspend fun verdictForCall(raw: String?): SpamVerdict? {
        val region = regions.defaultRegion()
        val valid = normalizer.normalize(raw, region) as? NormalizedNumber.Valid ?: return null
        // Normalised once for both the live lookup and, on a miss, the decision.
        return store.live(valid.e164, clock()) ?: decideValid(raw, valid, region, record = true)
    }

    /** Makes [verdict] visible to the in-call screen right away. */
    fun remember(verdict: SpamVerdict) = store.remember(verdict)

    suspend fun record(verdict: SpamVerdict) = store.record(verdict)

    /**
     * An informational rule is not an unwanted-call level: unless the user chose an action for that very rule it only
     * warns (shows the label), whatever the action of the RULE level is.
     */
    private fun SpamSettings.withInformationalDefault(rule: RuleMatch?) = if (rule == null || !rule.informational || rule.ruleId in policy.byRuleId) {
        this
    } else {
        copy(policy = ActionPolicy(policy.byLevel, policy.byRuleId + (rule.ruleId to SpamAction.WARN)))
    }

    private companion object {
        /** A well formed number nobody owns; only used to make the lookups load their state. */
        const val WARM_UP_NUMBER = "+10000000000"
    }
}
