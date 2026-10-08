package com.qtekfun.ultimatephone.feature.recents

import com.qtekfun.ultimatephone.core.calllog.CallLogFilter
import com.qtekfun.ultimatephone.core.calllog.NumberKeys
import com.qtekfun.ultimatephone.core.spam.decision.Decision
import com.qtekfun.ultimatephone.core.spam.decision.DecisionReason
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.feature.spam.NumberSpamState
import com.qtekfun.ultimatephone.feature.spam.SpamHistory
import com.qtekfun.ultimatephone.screening.SpamVerdict
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentsSpamTest {
    private class FakeHistory(initial: Map<String, SpamVerdict> = emptyMap()) : SpamHistory {
        val state = MutableStateFlow(initial)
        override val verdicts = state
    }

    private val clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC)
    private val today = clock.millis() - 3_600_000
    private lateinit var log: FakeCallLog

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        log = FakeCallLog()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun spamVerdict(e164: String) =
        SpamVerdict(e164, Decision(SpamAction.WARN, SpamLevel.OWN, "own", null, null, DecisionReason.OWN_LIST), informational = false, decidedAt = today)

    private fun commercialVerdict(e164: String) = SpamVerdict(
        e164,
        Decision(SpamAction.WARN, SpamLevel.RULE, "es-400-commercial", null, null, DecisionReason.PREFIX_RULE),
        informational = true,
        decidedAt = today
    )

    private fun viewModel(history: SpamHistory): RecentsViewModel {
        val resolver = CallerResolver(FakeContacts(), FakeNormalizer(), FakeRegion())
        return RecentsViewModel(log, FakeSims(), FakePlacer(), NumberKeys(FakeNormalizer(), FakeRegion()), resolver, clock, history)
    }

    @Test
    fun rowsOfFlaggedNumbersCarryTheVerdictAndOthersDoNot() = runTest {
        log.entries.value = listOf(entry(2, number = "600111222", date = today), entry(1, number = "699000111", date = today - 1000))
        val vm = viewModel(FakeHistory(mapOf("+34600111222" to spamVerdict("+34600111222"))))
        val rows = vm.uiState.first { !it.isLoading }.sections.single().rows
        assertTrue(rows.first { it.number == "600111222" }.spam!!.isSpam)
        assertNull(rows.first { it.number == "699000111" }.spam)
    }

    @Test
    fun informationalResultsAreKeptForCalmDisplay() = runTest {
        log.entries.value = listOf(entry(1, number = "400123456", date = today))
        val vm = viewModel(FakeHistory(mapOf("+34400123456" to commercialVerdict("+34400123456"))))
        val spam = vm.uiState.first { !it.isLoading }.sections.single().rows.single().spam
        assertNotNull(spam)
        assertTrue(!spam!!.isSpam)
    }

    @Test
    fun theSpamFilterShowsOnlyFlaggedNumbers() = runTest {
        log.entries.value = listOf(
            entry(3, number = "600111222", date = today),
            entry(2, number = "699000111", date = today - 1000),
            entry(1, number = "400123456", date = today - 2000)
        )
        val vm = viewModel(
            FakeHistory(
                mapOf(
                    "+34600111222" to spamVerdict("+34600111222"),
                    "+34400123456" to commercialVerdict("+34400123456")
                )
            )
        )
        vm.setFilter(CallLogFilter.Spam)
        val state = vm.uiState.first { !it.isLoading && it.filter == CallLogFilter.Spam }
        assertEquals(listOf("600111222"), state.sections.flatMap { it.rows }.map { it.number })
    }

    @Test
    fun newDecisionsAppearWithoutReloadingTheLog() = runTest {
        log.entries.value = listOf(entry(1, number = "600111222", date = today))
        val history = FakeHistory()
        val vm = viewModel(history)
        vm.setFilter(CallLogFilter.Spam)
        assertTrue(vm.uiState.first { !it.isLoading && it.filter == CallLogFilter.Spam }.isEmpty)
        history.state.value = mapOf("+34600111222" to spamVerdict("+34600111222"))
        assertEquals(1, vm.uiState.first { it.sections.isNotEmpty() }.sections.single().rows.size)
    }
}

class DetailSpamActionsTest {
    private val log = mutableListOf<String>()
    private val handlers = DetailHandlers(
        onCall = {},
        onCreateContact = {},
        onAddToExistingContact = {},
        onOpenContact = {},
        onEditContact = {},
        onDelete = {},
        onMarkSpam = { log += "mark" },
        onRemoveSpam = { log += "unmark" },
        onAllow = { log += "allow" },
        onWhyFlagged = { log += "why" }
    )
    private val caller = CallerInfo(null, null, "600")

    private fun ids(spam: NumberSpamState, isPrivate: Boolean = false) = detailActions(caller, isPrivate, true, handlers, spam).map { it.id }

    @Test
    fun withoutSpamStateNothingSpamRelatedIsOffered() {
        val ids = ids(NumberSpamState.Hidden)
        assertTrue(listOf(DetailActionId.MARK_SPAM, DetailActionId.REMOVE_SPAM, DetailActionId.ALLOW, DetailActionId.WHY_FLAGGED).none { it in ids })
    }

    @Test
    fun anUnlistedNumberCanBeMarkedOrAllowed() {
        val ids = ids(NumberSpamState(available = true))
        assertTrue(DetailActionId.MARK_SPAM in ids && DetailActionId.ALLOW in ids)
        assertTrue(DetailActionId.REMOVE_SPAM !in ids && DetailActionId.WHY_FLAGGED !in ids)
        assertEquals(DetailActionId.DELETE, ids.last())
    }

    @Test
    fun aSpamNumberCanBeRemovedAndExplained() {
        val verdict = SpamVerdict("+34600", Decision(SpamAction.WARN, SpamLevel.OWN, "own", null, null, DecisionReason.OWN_LIST), false, 1)
        val actions = detailActions(caller, false, true, handlers, NumberSpamState(available = true, inOwnList = true, verdict = verdict))
        assertEquals(
            listOf(DetailActionId.REMOVE_SPAM, DetailActionId.ALLOW, DetailActionId.WHY_FLAGGED),
            actions.map { it.id }.filter { it in setOf(DetailActionId.REMOVE_SPAM, DetailActionId.ALLOW, DetailActionId.WHY_FLAGGED, DetailActionId.MARK_SPAM) }
        )
        actions.forEach { it.onClick() }
        assertEquals(listOf("unmark", "allow", "why"), log)
    }

    @Test
    fun allowedNumbersDoNotOfferAllowAgain() {
        assertTrue(DetailActionId.ALLOW !in ids(NumberSpamState(available = true, inAllowed = true)))
    }

    @Test
    fun hiddenNumbersGetNoSpamActions() {
        assertEquals(listOf(DetailActionId.DELETE), ids(NumberSpamState(available = true), isPrivate = true))
    }
}
