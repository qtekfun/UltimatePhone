package com.qtekfun.ultimatephone.core.spam.decision

import com.qtekfun.ultimatephone.core.phonenumber.NormalizedNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamDeciderTest {
    private val n = ES_NUMBER
    private val business = BusinessMatch("Pizzeria Roma", "restaurant", "businesses-es")
    private val source = SourceMatch("src-a", SpamLevel.COMMUNITY, "robocall")
    private val rule = RuleMatch("some-rule", SpamLevel.RULE)

    /** A context where the number matches every step except those listed in [without]. */
    private fun everything(vararg without: DecisionReason, settings: SpamSettings = SpamSettings(), raw: String? = null) = ctx(
        contacts = if (DecisionReason.CONTACT in without) emptySet() else setOf(n),
        whitelist = if (DecisionReason.WHITELIST in without) emptySet() else setOf(n),
        own = if (DecisionReason.OWN_LIST in without) emptyMap() else mapOf(n to "mine"),
        business = if (DecisionReason.BUSINESS in without) emptyMap() else mapOf(n to business),
        sources = if (DecisionReason.SOURCE in without) emptyMap() else mapOf(n to source),
        rules = if (DecisionReason.PREFIX_RULE in without) FakeRules() else FakeRules(mapOf(n to rule)),
        settings = settings,
        raw = raw
    )

    private fun reason(c: DecisionContext, number: NormalizedNumber = valid()) = SpamDecider.decide(number, c).reason

    // Precedence matrix: each step beats every later one.

    @Test
    fun emergencyBeatsEverything() {
        assertEquals(DecisionReason.EMERGENCY, reason(everything(raw = "112"), NormalizedNumber.Unknown))
        assertEquals(DecisionReason.EMERGENCY, reason(everything(raw = "112"), valid()))
    }

    @Test
    fun contactBeatsEveryLaterStep() {
        assertEquals(DecisionReason.CONTACT, reason(everything()))
    }

    @Test
    fun whitelistBeatsOwnListAndEverythingAfter() {
        assertEquals(DecisionReason.WHITELIST, reason(everything(DecisionReason.CONTACT)))
    }

    @Test
    fun ownListBeatsBusinessSourceAndRule() {
        val d = SpamDecider.decide(valid(), everything(DecisionReason.CONTACT, DecisionReason.WHITELIST))
        assertEquals(DecisionReason.OWN_LIST, d.reason)
        assertEquals(SpamLevel.OWN, d.level)
        assertEquals(SpamDecider.SOURCE_OWN, d.sourceId)
        assertEquals("mine", d.label)
    }

    @Test
    fun businessBeatsSourceAndRuleWhenNotSpamSettingIsOn() {
        val d = SpamDecider.decide(valid(), everything(DecisionReason.CONTACT, DecisionReason.WHITELIST, DecisionReason.OWN_LIST))
        assertEquals(DecisionReason.BUSINESS, d.reason)
        assertEquals(SpamAction.NONE, d.action)
        assertNull(d.level)
        assertEquals("Pizzeria Roma", d.label)
        assertEquals("restaurant", d.category)
        assertEquals("businesses-es", d.sourceId)
        assertFalse(d.isFlagged)
    }

    @Test
    fun sourceBeatsRule() {
        val c = everything(DecisionReason.CONTACT, DecisionReason.WHITELIST, DecisionReason.OWN_LIST, DecisionReason.BUSINESS)
        val d = SpamDecider.decide(valid(), c)
        assertEquals(DecisionReason.SOURCE, d.reason)
        assertEquals(SpamLevel.COMMUNITY, d.level)
        assertEquals("src-a", d.sourceId)
        assertEquals("robocall", d.label)
        assertEquals(SpamAction.WARN, d.action)
    }

    @Test
    fun ruleIsTheLastStepBeforeNothing() {
        val c = everything(
            DecisionReason.CONTACT,
            DecisionReason.WHITELIST,
            DecisionReason.OWN_LIST,
            DecisionReason.BUSINESS,
            DecisionReason.SOURCE
        )
        val d = SpamDecider.decide(valid(), c)
        assertEquals(DecisionReason.PREFIX_RULE, d.reason)
        assertEquals(SpamLevel.RULE, d.level)
        assertEquals("some-rule", d.sourceId)
    }

    @Test
    fun nothingMatchesMeansNoAction() {
        val d = SpamDecider.decide(valid(), ctx())
        assertEquals(Decision(SpamAction.NONE, null, null, null, null, DecisionReason.NO_MATCH), d)
    }

    // Hidden, invalid and emergency numbers.

    @Test
    fun unknownNumberIsNeverSpamEvenWhenEveryListWouldMatch() {
        val d = SpamDecider.decide(NormalizedNumber.Unknown, everything())
        assertEquals(SpamAction.NONE, d.action)
        assertNull(d.level)
        assertEquals(DecisionReason.UNKNOWN_NUMBER, d.reason)
    }

    @Test
    fun unknownNumberStaysNoActionEvenWithRejectEverywhere() {
        val all = SpamSettings(policy = ActionPolicy(SpamLevel.entries.associateWith { SpamAction.REJECT }))
        assertEquals(SpamAction.NONE, SpamDecider.decide(NormalizedNumber.Unknown, everything(settings = all)).action)
    }

    @Test
    fun emergencyShortCodesAreRecognisedFromTheRawNumber() {
        listOf("112" to "ES", "110" to "DE", "000" to "AU", "911" to "US", "999" to null, "112" to null).forEach { (raw, region) ->
            val d = SpamDecider.decide(NormalizedNumber.Unknown, ctx(raw = raw, region = region))
            assertEquals("$raw/$region", DecisionReason.EMERGENCY, d.reason)
            assertEquals(SpamAction.NONE, d.action)
        }
    }

    @Test
    fun emergencyIsNotFilteredEvenIfUserBlockedItEverywhere() {
        val reject = SpamSettings(policy = ActionPolicy(SpamLevel.entries.associateWith { SpamAction.REJECT }))
        val c = everything(DecisionReason.CONTACT, DecisionReason.WHITELIST, settings = reject, raw = "112")
        assertEquals(SpamAction.NONE, SpamDecider.decide(valid(), c).action)
    }

    @Test
    fun ordinaryNumbersAreNotEmergencies() {
        val d = SpamDecider.decide(valid(), ctx(raw = "+34612345678"))
        assertEquals(DecisionReason.NO_MATCH, d.reason)
        assertEquals(DecisionReason.UNKNOWN_NUMBER, SpamDecider.decide(NormalizedNumber.Unknown, ctx(raw = "12345")).reason)
        assertEquals(DecisionReason.UNKNOWN_NUMBER, SpamDecider.decide(NormalizedNumber.Unknown, ctx(raw = null)).reason)
    }

    @Test
    fun customEmergencyCheckerIsUsed() {
        val c = ctx().copy(emergency = EmergencyNumbers { _, _, _ -> true })
        assertEquals(DecisionReason.EMERGENCY, SpamDecider.decide(valid(), c).reason)
    }

    // Whitelist and contacts.

    @Test
    fun whitelistBeatsOwnList() {
        val d = SpamDecider.decide(valid(), ctx(whitelist = setOf(n), own = mapOf(n to null)))
        assertEquals(SpamAction.NONE, d.action)
        assertEquals(DecisionReason.WHITELIST, d.reason)
    }

    @Test
    fun contactIsNeverFlaggedByAnyList() {
        val d = SpamDecider.decide(valid(), ctx(contacts = setOf(n), own = mapOf(n to null), sources = mapOf(n to source)))
        assertEquals(SpamAction.NONE, d.action)
        assertNull(d.level)
    }

    // Business setting.

    @Test
    fun businessNotSpamOffLetsSourceFlagItAndKeepsTheBusinessName() {
        val settings = SpamSettings(identifiedBusinessIsNotSpam = false)
        val d = SpamDecider.decide(valid(), ctx(business = mapOf(n to business), sources = mapOf(n to SourceMatch("src-a")), settings = settings))
        assertEquals(DecisionReason.SOURCE, d.reason)
        assertEquals(SpamAction.WARN, d.action)
        assertEquals("Pizzeria Roma", d.label)
        assertEquals("restaurant", d.category)
    }

    @Test
    fun sourceLabelWinsOverBusinessNameWhenSettingIsOff() {
        val settings = SpamSettings(identifiedBusinessIsNotSpam = false)
        val d = SpamDecider.decide(valid(), ctx(business = mapOf(n to business), sources = mapOf(n to source), settings = settings))
        assertEquals("robocall", d.label)
        assertEquals("restaurant", d.category)
    }

    @Test
    fun businessNotSpamOffAndNothingElseStillShowsTheBusiness() {
        val settings = SpamSettings(identifiedBusinessIsNotSpam = false)
        val d = SpamDecider.decide(valid(), ctx(business = mapOf(n to business), settings = settings))
        assertEquals(SpamAction.NONE, d.action)
        assertEquals(DecisionReason.BUSINESS, d.reason)
        assertEquals("Pizzeria Roma", d.label)
    }

    @Test
    fun businessNotSpamOffStillLosesToOwnListAndWins_nothing() {
        val settings = SpamSettings(identifiedBusinessIsNotSpam = false)
        val d = SpamDecider.decide(valid(), ctx(business = mapOf(n to business), own = mapOf(n to "x"), settings = settings))
        assertEquals(DecisionReason.OWN_LIST, d.reason)
    }

    @Test
    fun businessNotSpamOffFlagsWithRuleToo() {
        val settings = SpamSettings(identifiedBusinessIsNotSpam = false)
        val d = SpamDecider.decide(valid(), ctx(business = mapOf(n to business), rules = FakeRules(mapOf(n to rule)), settings = settings))
        assertEquals(DecisionReason.PREFIX_RULE, d.reason)
        assertEquals("Pizzeria Roma", d.label)
    }

    // Actions per level.

    @Test
    fun defaultActionIsWarnForEveryLevel() {
        SpamLevel.entries.forEach { assertEquals(SpamAction.WARN, ActionPolicy().actionFor(it)) }
    }

    @Test
    fun actionIsConfigurablePerLevel() {
        val policy = ActionPolicy(
            mapOf(SpamLevel.OWN to SpamAction.REJECT, SpamLevel.COMMUNITY to SpamAction.SILENCE, SpamLevel.RULE to SpamAction.NONE)
        )
        val settings = SpamSettings(policy = policy)
        assertEquals(SpamAction.REJECT, SpamDecider.decide(valid(), ctx(own = mapOf(n to null), settings = settings)).action)
        assertEquals(SpamAction.SILENCE, SpamDecider.decide(valid(), ctx(sources = mapOf(n to source), settings = settings)).action)
        val ruleDecision = SpamDecider.decide(valid(), ctx(rules = FakeRules(mapOf(n to rule)), settings = settings))
        assertEquals(SpamAction.NONE, ruleDecision.action)
        assertEquals(SpamLevel.RULE, ruleDecision.level)
        assertTrue(ruleDecision.isFlagged)
    }

    @Test
    fun sourceCanDeclareItsOwnLevelAndTheActionFollowsIt() {
        val declared = SourceMatch("src-rule", SpamLevel.RULE, null)
        val settings = SpamSettings(policy = ActionPolicy(mapOf(SpamLevel.RULE to SpamAction.SILENCE)))
        val d = SpamDecider.decide(valid(), ctx(sources = mapOf(n to declared), settings = settings))
        assertEquals(SpamLevel.RULE, d.level)
        assertEquals(SpamAction.SILENCE, d.action)
    }

    @Test
    fun businessAndContactResultsAlwaysHaveActionNone() {
        val reject = SpamSettings(policy = ActionPolicy(SpamLevel.entries.associateWith { SpamAction.REJECT }))
        assertEquals(SpamAction.NONE, SpamDecider.decide(valid(), ctx(business = mapOf(n to business), settings = reject)).action)
        assertEquals(SpamAction.NONE, SpamDecider.decide(valid(), ctx(contacts = setOf(n), settings = reject)).action)
    }

    // D-001: Spanish 400 is an informational commercial call, level RULE, default WARN, user configurable.

    private val es400 = "+34400123456"

    @Test
    fun spanish400IsAnInformationalRuleWithDefaultWarn() {
        val d = SpamDecider.decide(valid(es400), ctx(rules = builtInEs400()))
        assertEquals(SpamLevel.RULE, d.level)
        assertEquals(SpamAction.WARN, d.action)
        assertEquals("es-400-commercial", d.sourceId)
        assertEquals(DecisionReason.PREFIX_RULE, d.reason)
    }

    @Test
    fun userCanChangeTheActionForRuleLevelOrForThe400RuleAlone() {
        val levelWide = SpamSettings(policy = ActionPolicy(mapOf(SpamLevel.RULE to SpamAction.NONE)))
        assertEquals(SpamAction.NONE, SpamDecider.decide(valid(es400), ctx(rules = builtInEs400(), settings = levelWide)).action)
        val single = SpamSettings(policy = ActionPolicy(byRuleId = mapOf("es-400-commercial" to SpamAction.SILENCE)))
        assertEquals(SpamAction.SILENCE, SpamDecider.decide(valid(es400), ctx(rules = builtInEs400(), settings = single)).action)
    }

    @Test
    fun ruleIdOverrideDoesNotAffectOtherRules() {
        val policy = ActionPolicy(mapOf(SpamLevel.RULE to SpamAction.WARN), mapOf("es-400-commercial" to SpamAction.REJECT))
        assertEquals(SpamAction.WARN, policy.actionFor(SpamLevel.RULE, "other"))
        assertEquals(SpamAction.REJECT, policy.actionFor(SpamLevel.RULE, "es-400-commercial"))
    }

    @Test
    fun a400NumberInTheWhitelistOrContactsIsLeftAlone() {
        assertEquals(DecisionReason.WHITELIST, SpamDecider.decide(valid(es400), ctx(rules = builtInEs400(), whitelist = setOf(es400))).reason)
        assertEquals(DecisionReason.CONTACT, SpamDecider.decide(valid(es400), ctx(rules = builtInEs400(), contacts = setOf(es400))).reason)
    }

    @Test
    fun a400LookalikeOutsideSpainIsNotMatched() {
        val d = SpamDecider.decide(valid("+49400123456", "DE"), ctx(rules = builtInEs400()))
        assertEquals(DecisionReason.NO_MATCH, d.reason)
    }
}
