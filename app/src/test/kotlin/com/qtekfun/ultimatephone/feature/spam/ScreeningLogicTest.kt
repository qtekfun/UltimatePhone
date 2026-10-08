package com.qtekfun.ultimatephone.feature.spam

import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.spam.decision.ActionPolicy
import com.qtekfun.ultimatephone.core.spam.decision.Decision
import com.qtekfun.ultimatephone.core.spam.decision.DecisionReason
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.lists.EntryKind
import com.qtekfun.ultimatephone.feature.recents.FakeNormalizer
import com.qtekfun.ultimatephone.screening.RollingMax
import com.qtekfun.ultimatephone.screening.RuleActionsCodec
import com.qtekfun.ultimatephone.screening.ScreeningResponse
import com.qtekfun.ultimatephone.screening.ScreeningStats
import com.qtekfun.ultimatephone.screening.SpamPrefs
import com.qtekfun.ultimatephone.screening.responseFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreeningResponseTest {
    @Test
    fun rejectDisallowsAndRejectsButKeepsTheCallInTheLog() {
        val response = responseFor(SpamAction.REJECT)
        assertTrue(response.disallowCall)
        assertTrue(response.rejectCall)
        assertFalse(response.skipCallLog)
        assertFalse(response.silenceCall)
    }

    @Test
    fun silenceOnlySilences() {
        assertEquals(ScreeningResponse(silenceCall = true), responseFor(SpamAction.SILENCE))
    }

    @Test
    fun warnAndNoneLetTheCallThrough() {
        assertEquals(ScreeningResponse.Allow, responseFor(SpamAction.WARN))
        assertEquals(ScreeningResponse.Allow, responseFor(SpamAction.NONE))
        assertFalse(ScreeningResponse.Allow.disallowCall)
    }
}

class RollingMaxTest {
    @Test
    fun emptyHasNoMax() {
        assertNull(RollingMax().max())
        assertNull(ScreeningStats().slowestRecentMillis())
    }

    @Test
    fun keepsTheLargestOfTheLastSamples() {
        val rolling = RollingMax(capacity = 3)
        listOf(5L, 90L, 7L).forEach(rolling::add)
        assertEquals(90L, rolling.max())
        // The 90 falls out of the window after three more samples.
        listOf(1L, 2L, 3L).forEach(rolling::add)
        assertEquals(3L, rolling.max())
    }

    @Test
    fun statsTrackTheLastAndTheSlowest() {
        val stats = ScreeningStats(RollingMax(10))
        stats.record(12)
        stats.record(40)
        stats.record(8)
        assertEquals(8L, stats.lastMillis)
        assertEquals(40L, stats.slowestRecentMillis())
    }
}

class SpamPrefsTest {
    @Test
    fun defaultsWarnEverywhereAndTreatBusinessesAsNotSpam() {
        val settings = SpamPrefs().toSpamSettings()
        assertTrue(settings.identifiedBusinessIsNotSpam)
        SpamLevel.entries.forEach { assertEquals(SpamAction.WARN, settings.policy.actionFor(it)) }
    }

    @Test
    fun levelAndRuleChoicesReachTheActionPolicy() {
        val prefs = SpamPrefs(
            levelActions = mapOf(SpamLevel.OWN to SpamAction.REJECT, SpamLevel.COMMUNITY to SpamAction.SILENCE),
            ruleActions = mapOf("es-400-commercial" to SpamAction.SILENCE),
            identifiedBusinessIsNotSpam = false
        )
        val settings = prefs.toSpamSettings()
        assertFalse(settings.identifiedBusinessIsNotSpam)
        assertEquals(SpamAction.REJECT, settings.policy.actionFor(SpamLevel.OWN))
        assertEquals(SpamAction.SILENCE, settings.policy.actionFor(SpamLevel.COMMUNITY))
        assertEquals(ActionPolicy.DEFAULT_ACTION, settings.policy.actionFor(SpamLevel.RULE))
        assertEquals(SpamAction.SILENCE, settings.policy.actionFor(SpamLevel.RULE, "es-400-commercial"))
        assertEquals(ActionPolicy.DEFAULT_ACTION, settings.policy.actionFor(SpamLevel.RULE, "other-rule"))
    }

    @Test
    fun readersFallBackToWarn() {
        val prefs = SpamPrefs(levelActions = mapOf(SpamLevel.OWN to SpamAction.REJECT))
        assertEquals(SpamAction.REJECT, prefs.actionOf(SpamLevel.OWN))
        assertEquals(SpamAction.WARN, prefs.actionOf(SpamLevel.COMMUNITY))
        assertEquals(SpamAction.WARN, prefs.actionOfRule("anything"))
    }

    @Test
    fun ruleActionsSurviveTheTextForm() {
        val map = mapOf("es-400-commercial" to SpamAction.SILENCE, "x-y" to SpamAction.REJECT)
        assertEquals(map, RuleActionsCodec.decode(RuleActionsCodec.encode(map)))
    }

    @Test
    fun badRuleActionLinesAreDropped() {
        val decoded = RuleActionsCodec.decode("ok=WARN\nnoequals\n=WARN\nbad=EXPLODE\n\nlast=REJECT")
        assertEquals(mapOf("ok" to SpamAction.WARN, "last" to SpamAction.REJECT), decoded)
        assertTrue(RuleActionsCodec.decode(null).isEmpty())
        assertTrue(RuleActionsCodec.decode("").isEmpty())
    }
}

class SpamReasonsTest {
    private fun decision(reason: DecisionReason, sourceId: String? = null, label: String? = null, category: String? = null) =
        Decision(SpamAction.WARN, SpamLevel.RULE, sourceId, label, category, reason)

    @Test
    fun knownRuleIdsMapToTheirString() {
        assertEquals(R.string.spam_rule_es_400_commercial, ruleLabelRes("es-400-commercial"))
    }

    @Test
    fun unknownRuleIdsFallBackToAGenericLabel() {
        assertEquals(R.string.spam_rule_generic, ruleLabelRes("xx-999-new-rule"))
        assertEquals(R.string.spam_rule_generic, ruleLabelRes(""))
    }

    @Test
    fun everyLevelAndActionHasALabel() {
        assertEquals(3, SpamLevel.entries.map(::levelLabelRes).toSet().size)
        assertEquals(4, SpamAction.entries.map(::actionLabelRes).toSet().size)
    }

    @Test
    fun reasonsAreDescribedFromTheDecision() {
        assertEquals(ReasonDisplay.OwnList, describeReason(decision(DecisionReason.OWN_LIST, "own")))
        assertEquals(ReasonDisplay.Source("src-a"), describeReason(decision(DecisionReason.SOURCE, "src-a")))
        assertEquals(
            ReasonDisplay.Rule(R.string.spam_rule_es_400_commercial, "es-400-commercial"),
            describeReason(decision(DecisionReason.PREFIX_RULE, "es-400-commercial"))
        )
        assertEquals(ReasonDisplay.Rule(R.string.spam_rule_generic, "new"), describeReason(decision(DecisionReason.PREFIX_RULE, "new")))
        assertEquals(ReasonDisplay.Business("Pizza", "food"), describeReason(decision(DecisionReason.BUSINESS, label = "Pizza", category = "food")))
        assertEquals(ReasonDisplay.Allowed, describeReason(decision(DecisionReason.WHITELIST)))
        assertEquals(ReasonDisplay.Contact, describeReason(decision(DecisionReason.CONTACT)))
        assertEquals(ReasonDisplay.NothingFound, describeReason(decision(DecisionReason.NO_MATCH)))
    }
}

class ListInputTest {
    private val normalizer: PhoneNormalizer = FakeNormalizer()

    @Test
    fun numbersAreNormalisedToE164() {
        assertEquals("+34600111222", ListInput.validate(EntryKind.NUMBER, " 600 111 222 ", normalizer, "ES"))
    }

    @Test
    fun invalidNumbersAreRejected() {
        assertNull(ListInput.validate(EntryKind.NUMBER, "abc", normalizer, "ES"))
        assertNull(ListInput.validate(EntryKind.NUMBER, "", normalizer, "ES"))
    }

    @Test
    fun prefixesNeedACountryCodeAndEnoughDigits() {
        assertEquals("+34900", ListInput.validate(EntryKind.PREFIX, "+34 900", normalizer, "ES"))
        assertNull(ListInput.validate(EntryKind.PREFIX, "900", normalizer, "ES"))
        assertNull(ListInput.validate(EntryKind.PREFIX, "+34", normalizer, "ES"))
        assertNull(ListInput.validate(EntryKind.PREFIX, "+34+900", normalizer, "ES"))
        assertNull(ListInput.validate(EntryKind.PREFIX, "+0900", normalizer, "ES"))
    }
}
