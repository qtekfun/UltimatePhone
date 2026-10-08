package com.qtekfun.ultimatephone.feature.spam

import com.qtekfun.ultimatephone.core.spam.decision.BusinessLookup
import com.qtekfun.ultimatephone.core.spam.decision.BusinessMatch
import com.qtekfun.ultimatephone.core.spam.decision.DecisionReason
import com.qtekfun.ultimatephone.core.spam.decision.PrefixRules
import com.qtekfun.ultimatephone.core.spam.decision.SourceLookup
import com.qtekfun.ultimatephone.core.spam.decision.SourceMatch
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.feature.recents.FakeContacts
import com.qtekfun.ultimatephone.feature.recents.FakeNormalizer
import com.qtekfun.ultimatephone.feature.recents.FakeRegion
import com.qtekfun.ultimatephone.feature.recents.match
import com.qtekfun.ultimatephone.screening.DecisionEngine
import com.qtekfun.ultimatephone.screening.RULE_ES_400_COMMERCIAL
import com.qtekfun.ultimatephone.screening.RoomDecisionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionEngineTest {
    private val lists = InMemoryLists()
    private val dao = FakeDecisionDao()
    private val store = RoomDecisionStore(dao)
    private val settings = FakeSpamSettings()
    private var now = 1_000_000L

    private fun engine(packs: FakePacks = FakePacks(), contacts: FakeContacts = FakeContacts(), rules: PrefixRules = builtInRuleSet()) =
        DecisionEngine(contacts, lists, packs, rules, settings, FakeNormalizer(), FakeRegion(), store, clock = { now++ })

    private fun actions(engine: DecisionEngine) = ListsSpamNumberActions(lists, engine, NoSpamHistory, FakeNormalizer(), FakeRegion())

    @Test
    fun unknownNumberIsLeftAlone() = runTest {
        val verdict = engine().decide("600111222")!!
        assertFalse(verdict.decision.isFlagged)
        assertEquals(SpamAction.NONE, verdict.decision.action)
    }

    @Test
    fun hiddenAndMalformedNumbersAreNeverActioned() = runTest {
        val engine = engine()
        assertNull(engine.decide(null))
        assertNull(engine.decide(""))
        assertNull(engine.decide("-1"))
        assertNull(engine.decide("112"))
    }

    @Test
    fun markingAsSpamAddsToOwnListAndTheNextCallIsWarned() = runTest {
        val engine = engine()
        val actions = actions(engine)
        assertEquals(SpamAction.NONE, engine.decide("600111222")!!.decision.action)

        assertTrue(actions.markSpam("600111222"))

        assertEquals("+34600111222", lists.matchOwn("+34600111222")?.value)
        // The next call from that number is decided from scratch, as the screening service does.
        store.clearLive()
        val next = engine.verdictForCall("600 111 222")!!
        assertEquals(SpamAction.WARN, next.decision.action)
        assertEquals(SpamLevel.OWN, next.decision.level)
        assertEquals(DecisionReason.OWN_LIST, next.decision.reason)
        assertTrue(next.isSpam)
    }

    @Test
    fun theOwnListLevelActionIsHonouredOnTheNextCall() = runTest {
        val engine = engine()
        actions(engine).markSpam("600111222")
        settings.setLevelAction(SpamLevel.OWN, SpamAction.REJECT)
        store.clearLive()
        assertEquals(SpamAction.REJECT, engine.verdictForCall("600111222")!!.decision.action)
    }

    @Test
    fun markingAsSpamAlsoRecordsTheDecisionForTheHistory() = runTest {
        val engine = engine()
        actions(engine).markSpam("600111222")
        val latest = store.latest("+34600111222")!!
        assertTrue(latest.isSpam)
        assertEquals(SpamLevel.OWN, latest.decision.level)
    }

    @Test
    fun removingFromSpamListLiftsTheWarning() = runTest {
        val engine = engine()
        val actions = actions(engine)
        actions.markSpam("600111222")
        assertTrue(actions.removeFromSpam("600111222"))
        store.clearLive()
        assertFalse(engine.verdictForCall("600111222")!!.isSpam)
    }

    @Test
    fun notSpamWhitelistsAndOverrulesTheOwnList() = runTest {
        val engine = engine()
        val actions = actions(engine)
        actions.markSpam("600111222")
        assertTrue(actions.allow("600111222"))
        store.clearLive()
        val verdict = engine.verdictForCall("600111222")!!
        assertEquals(DecisionReason.WHITELIST, verdict.decision.reason)
        assertFalse(verdict.isSpam)
        // Marking again takes it off the allowed numbers, otherwise the whitelist would still win.
        actions.markSpam("600111222")
        store.clearLive()
        assertTrue(engine.verdictForCall("600111222")!!.isSpam)
    }

    @Test
    fun stateReflectsTheListsAndNeverOffersActionsForHiddenNumbers() = runTest {
        val engine = engine()
        val actions = actions(engine)
        actions.markSpam("600111222")
        val state = actions.observeState("600111222").first()
        assertTrue(state.available && state.inOwnList && !state.inAllowed)
        assertFalse(actions.observeState("-1").first().available)
        assertFalse(actions.markSpam("-1"))
    }

    @Test
    fun contactsAreNeverFlaggedEvenWhenListed() = runTest {
        val engine = engine(contacts = FakeContacts(mapOf("600111222" to match("Ana"))))
        actions(engine).markSpam("600111222")
        assertEquals(DecisionReason.CONTACT, engine.decide("600111222")!!.decision.reason)
    }

    @Test
    fun communitySourceFlagsWithItsLevelAction() = runTest {
        val packs = FakePacks(sources = SourceLookup { if (it == "+34699000111") SourceMatch("src-a") else null })
        settings.setLevelAction(SpamLevel.COMMUNITY, SpamAction.SILENCE)
        val verdict = engine(packs).decide("699000111")!!
        assertEquals(SpamAction.SILENCE, verdict.decision.action)
        assertEquals("src-a", verdict.decision.sourceId)
        assertTrue(verdict.isSpam)
    }

    @Test
    fun identifiedBusinessIsNotSpamByDefaultButCanBeTurnedOff() = runTest {
        val packs = FakePacks(
            sources = SourceLookup { SourceMatch("src-a") },
            businesses = BusinessLookup { BusinessMatch("Pizza Luigi", "restaurant") }
        )
        val engine = engine(packs)
        val calm = engine.decide("699000111")!!
        assertEquals(DecisionReason.BUSINESS, calm.decision.reason)
        assertFalse(calm.isSpam)
        assertEquals("Pizza Luigi", calm.decision.label)

        settings.setIdentifiedBusinessIsNotSpam(false)
        assertEquals(DecisionReason.SOURCE, engine.decide("699000111")!!.decision.reason)
    }

    @Test
    fun commercialRangeIsInformationalAndWarnsByDefault() = runTest {
        val verdict = engine().decide("400123456")!!
        // 9 digits starting 400: valid for the fake normaliser.
        assertEquals(DecisionReason.PREFIX_RULE, verdict.decision.reason)
        assertEquals(RULE_ES_400_COMMERCIAL, verdict.decision.sourceId)
        assertEquals(SpamAction.WARN, verdict.decision.action)
        assertTrue(verdict.informational)
        assertFalse(verdict.isSpam)
    }

    @Test
    fun rejectingAllRulesDoesNotRejectTheInformationalCommercialRange() = runTest {
        settings.setLevelAction(SpamLevel.RULE, SpamAction.REJECT)
        assertEquals(SpamAction.WARN, engine().decide("400123456")!!.decision.action)
    }

    @Test
    fun commercialRangeHasItsOwnControl() = runTest {
        settings.setRuleAction(RULE_ES_400_COMMERCIAL, SpamAction.SILENCE)
        assertEquals(SpamAction.SILENCE, engine().decide("400123456")!!.decision.action)
    }

    @Test
    fun aPackRuleOverridesTheBuiltInOneWithTheSameRange() = runTest {
        val packs = FakePacks(prefixRules = commercialRuleSet(SpamLevel.RULE))
        assertTrue(engine(packs).decide("400123456")!!.informational)
    }

    @Test
    fun settingsAreReadFreshOnEveryDecision() = runTest {
        val packs = FakePacks(sources = SourceLookup { SourceMatch("src-a") })
        val engine = engine(packs)
        assertEquals(SpamAction.WARN, engine.decide("699000111")!!.decision.action)
        settings.setLevelAction(SpamLevel.COMMUNITY, SpamAction.REJECT)
        assertEquals(SpamAction.REJECT, engine.decide("699000111")!!.decision.action)
    }

    @Test
    fun theLiveDecisionOfTheScreeningServiceIsReusedByTheCallScreen() = runTest {
        var lookups = 0
        val packs = FakePacks(
            sources = SourceLookup {
                lookups++
                SourceMatch("src-a")
            }
        )
        val engine = engine(packs)
        val screened = engine.decide("699000111", record = false)!!
        engine.remember(screened)
        val reused = engine.verdictForCall("699000111")
        assertEquals(screened, reused)
        assertEquals(1, lookups)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun recordedDecisionsReachTheHistory() = runTest {
        val packs = FakePacks(sources = SourceLookup { SourceMatch("src-a") })
        engine(packs).decide("699000111")
        val latest = store.latest("+34699000111")!!
        assertEquals("src-a", latest.decision.sourceId)
        assertNotNull(dao.rows.value.single())
    }
}
