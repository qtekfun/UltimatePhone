package com.qtekfun.ultimatephone.core.spam.decision

import com.qtekfun.ultimatephone.core.phonenumber.NormalizedNumber
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRule
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRuleSet

internal const val ES_NUMBER = "+34612345678"

internal fun valid(e164: String = ES_NUMBER, region: String? = "ES") = NormalizedNumber.Valid(e164, region)

internal class FakeContacts(private val numbers: Set<String> = emptySet()) : ContactChecker {
    override fun isContact(e164: String) = e164 in numbers
}

internal class FakeWhitelist(private val numbers: Set<String> = emptySet()) : Whitelist {
    override fun isWhitelisted(e164: String) = e164 in numbers
}

internal class FakeOwnList(private val labels: Map<String, String?> = emptyMap()) : OwnList {
    override fun match(e164: String): OwnMatch? = if (e164 in labels) OwnMatch(labels[e164], "id-$e164") else null
}

internal class FakeBusinesses(private val map: Map<String, BusinessMatch> = emptyMap()) : BusinessLookup {
    override fun find(e164: String): BusinessMatch? = map[e164]
}

internal class FakeSources(private val map: Map<String, SourceMatch> = emptyMap()) : SourceLookup {
    override fun find(e164: String): SourceMatch? = map[e164]
}

internal class FakeRules(private val map: Map<String, RuleMatch> = emptyMap()) : PrefixRules {
    override fun match(e164: String, region: String?): RuleMatch? = map[e164]
}

internal fun ctx(
    contacts: Set<String> = emptySet(),
    whitelist: Set<String> = emptySet(),
    own: Map<String, String?> = emptyMap(),
    business: Map<String, BusinessMatch> = emptyMap(),
    sources: Map<String, SourceMatch> = emptyMap(),
    rules: PrefixRules = FakeRules(),
    settings: SpamSettings = SpamSettings(),
    raw: String? = null,
    region: String? = "ES"
) = DecisionContext(
    contacts = FakeContacts(contacts),
    whitelist = FakeWhitelist(whitelist),
    ownList = FakeOwnList(own),
    businesses = FakeBusinesses(business),
    sources = FakeSources(sources),
    prefixRules = rules,
    settings = settings,
    rawNumber = raw,
    region = region
)

/** The Spanish commercial range as shipped in the app. */
internal fun builtInEs400() = PrefixRuleSet(
    listOf(PrefixRule("es-400-commercial", "ES", "400", SpamLevel.RULE, informational = true, source = "BOE-A-2026-8409"))
)
