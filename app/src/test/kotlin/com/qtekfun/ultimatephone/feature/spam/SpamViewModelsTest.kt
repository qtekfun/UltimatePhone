package com.qtekfun.ultimatephone.feature.spam

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.lists.EntryKind
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.core.telecom.AudioRoute
import com.qtekfun.ultimatephone.core.telecom.AudioState
import com.qtekfun.ultimatephone.core.telecom.CallController
import com.qtekfun.ultimatephone.core.telecom.CallInfo
import com.qtekfun.ultimatephone.core.telecom.CallStatus
import com.qtekfun.ultimatephone.feature.recents.FakeContacts
import com.qtekfun.ultimatephone.feature.recents.FakeNormalizer
import com.qtekfun.ultimatephone.feature.recents.FakeRegion
import com.qtekfun.ultimatephone.incall.InCallViewModel
import com.qtekfun.ultimatephone.recording.CallRecording
import com.qtekfun.ultimatephone.recording.RecordingUi
import com.qtekfun.ultimatephone.screening.DecisionEngine
import com.qtekfun.ultimatephone.screening.RULE_ES_400_COMMERCIAL
import com.qtekfun.ultimatephone.screening.RoomDecisionStore
import com.qtekfun.ultimatephone.screening.ScreeningStats
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeRole(override val isAvailable: Boolean = true, held: Boolean = false) : ScreeningRole {
    override var isHeld: Boolean = held

    override fun requestIntent(): Intent? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class SpamHomeViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showsTheRoleAndTheStoredChoices() = runTest {
        val role = FakeRole(held = false)
        val vm = SpamHomeViewModel(FakeSpamSettings(), role, ScreeningStats())
        vm.state.test {
            val first = expectMostRecentItem()
            assertTrue(first.roleAvailable)
            assertFalse(first.roleHeld)
            assertEquals(SpamAction.WARN, first.commercialRuleAction)
            role.isHeld = true
            vm.refreshRole()
            assertTrue(awaitItem().roleHeld)
        }
    }

    @Test
    fun changesAreWrittenToTheSettings() = runTest {
        val settings = FakeSpamSettings()
        val vm = SpamHomeViewModel(settings, FakeRole(), ScreeningStats())
        vm.setLevelAction(SpamLevel.COMMUNITY, SpamAction.REJECT)
        vm.setCommercialRuleAction(SpamAction.SILENCE)
        vm.setBusinessNotSpam(false)
        val prefs = settings.current()
        assertEquals(SpamAction.REJECT, prefs.actionOf(SpamLevel.COMMUNITY))
        assertEquals(SpamAction.SILENCE, prefs.actionOfRule(RULE_ES_400_COMMERCIAL))
        assertFalse(prefs.identifiedBusinessIsNotSpam)
    }

    @Test
    fun theTableListsTheThreeLevelsAndTheThreeActions() {
        assertEquals(listOf(SpamLevel.OWN, SpamLevel.COMMUNITY, SpamLevel.RULE), TABLE_LEVELS)
        assertEquals(listOf(SpamAction.WARN, SpamAction.SILENCE, SpamAction.REJECT), SELECTABLE_ACTIONS)
    }

    @Test
    fun reportsTheSlowestRecentScreening() = runTest {
        val stats = ScreeningStats()
        stats.record(30)
        val vm = SpamHomeViewModel(FakeSpamSettings(), FakeRole(), stats)
        vm.state.test { assertEquals(30L, expectMostRecentItem().slowestScreeningMillis) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SpamListViewModelTest {
    private val lists = InMemoryLists()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(list: String) = SpamListViewModel(SavedStateHandle(mapOf(SpamListViewModel.LIST_ARG to list)), lists, FakeNormalizer(), FakeRegion())

    @Test
    fun addsANormalisedNumberToTheOwnList() = runTest {
        val vm = vm("own")
        assertTrue(vm.add(EntryKind.NUMBER, "600 111 222", " robocall ", ""))
        vm.state.test {
            val entry = expectMostRecentItem().entries.single()
            assertEquals("+34600111222", entry.value)
            assertEquals("robocall", entry.label)
            assertNull(entry.note)
        }
    }

    @Test
    fun invalidInputIsReportedAndNothingIsStored() = runTest {
        val vm = vm("own")
        assertFalse(vm.add(EntryKind.NUMBER, "nope", "", ""))
        assertFalse(vm.add(EntryKind.PREFIX, "900", "", ""))
        vm.state.test {
            assertEquals(ListInputError.INVALID_PREFIX, expectMostRecentItem().error)
        }
        assertTrue(lists.entries(ListType.OWN).isEmpty())
        vm.clearError()
        vm.state.test { assertNull(expectMostRecentItem().error) }
    }

    @Test
    fun removalLeavesATombstoneAndHidesTheEntry() = runTest {
        val vm = vm("own")
        vm.add(EntryKind.PREFIX, "+34900", "", "")
        val entry = vm.state.first { it.entries.isNotEmpty() }.entries.single()
        vm.remove(entry)
        assertTrue(vm.state.first { it.entries.isEmpty() }.entries.isEmpty())
        assertTrue(lists.entries(ListType.OWN).single().deleted)
    }

    @Test
    fun theAllowedListIsSeparateFromTheSpamList() = runTest {
        vm("allowed").add(EntryKind.NUMBER, "600111222", "", "")
        assertEquals(1, lists.entries(ListType.WHITELIST).size)
        assertTrue(lists.entries(ListType.OWN).isEmpty())
    }

    @Test
    fun addingToOneListTakesTheSameEntryOutOfTheOther() = runTest {
        vm("own").add(EntryKind.NUMBER, "600111222", "", "")
        vm("allowed").add(EntryKind.NUMBER, "600111222", "", "")
        assertTrue(lists.entries(ListType.OWN).single().deleted)
        assertFalse(lists.entries(ListType.WHITELIST).single().deleted)
    }

    @Test
    fun routeNamesRoundTrip() {
        ListType.entries.forEach { assertEquals(it, listTypeOfRoute(it.routeName())) }
        assertEquals(ListType.OWN, listTypeOfRoute(null))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WhyFlaggedViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun parts(): Triple<InMemoryLists, DecisionEngine, ListsSpamNumberActions> {
        val lists = InMemoryLists()
        val store = RoomDecisionStore(FakeDecisionDao())
        val engine = DecisionEngine(FakeContacts(), lists, FakePacks(), builtInRuleSet(), FakeSpamSettings(), FakeNormalizer(), FakeRegion(), store)
        val actions = ListsSpamNumberActions(lists, engine, StoreSpamHistory(store), FakeNormalizer(), FakeRegion())
        return Triple(lists, engine, actions)
    }

    @Test
    fun explainsTheLatestDecisionAndNotSpamWhitelistsTheNumber() = runTest {
        val (lists, _, actions) = parts()
        actions.markSpam("600111222")
        val vm = WhyFlaggedViewModel(SavedStateHandle(mapOf(WhyFlaggedViewModel.NUMBER_ARG to "600111222")), actions, FakeNormalizer(), FakeRegion())
        vm.state.test {
            val state = expectMostRecentItem()
            assertEquals(SpamLevel.OWN, state.verdict?.decision?.level)
            assertEquals("fmt:600111222", state.displayNumber)
            assertFalse(state.inAllowed)
            vm.notSpam()
            val after = awaitItem()
            assertTrue(after.inAllowed)
            assertFalse(after.verdict?.isSpam == true)
        }
        assertEquals("+34600111222", lists.entries(ListType.WHITELIST).single().value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class InCallSpamTest {
    private class Controller(calls: List<CallInfo>) : CallController {
        override val calls = MutableStateFlow(calls)
        override val audio = MutableStateFlow(AudioState.Default)

        override fun answer(id: String) = Unit

        override fun reject(id: String) = Unit

        override fun hangup(id: String) = Unit

        override fun setHold(id: String, hold: Boolean) = Unit

        override fun setMuted(muted: Boolean) = Unit

        override fun setRoute(route: AudioRoute) = Unit

        override fun playDtmf(id: String, digit: Char) = Unit

        override fun stopDtmf(id: String) = Unit

        override fun mergeCalls() = Unit

        override fun swapCalls() = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private object NoRecording : CallRecording {
        override val ui = MutableStateFlow(RecordingUi())

        override fun refresh() = Unit

        override fun startManual(call: CallInfo) = Unit

        override fun stop() = Unit

        override fun dismissFailure() = Unit

        override fun dismissMicWarning() = Unit
    }

    private fun call(id: String, number: String?, incoming: Boolean = true, status: CallStatus = CallStatus.RINGING) =
        CallInfo(id, status, number, number.orEmpty(), incoming, null, 0, false, false, false, 0)

    private fun viewModel(
        controller: Controller,
        lists: InMemoryLists = InMemoryLists(),
        store: RoomDecisionStore = RoomDecisionStore(FakeDecisionDao())
    ): InCallViewModel {
        val engine = DecisionEngine(FakeContacts(), lists, FakePacks(), builtInRuleSet(), FakeSpamSettings(), FakeNormalizer(), FakeRegion(), store)
        return InCallViewModel(controller, engine, ListsSpamNumberActions(lists, engine, NoSpamHistory, FakeNormalizer(), FakeRegion()), NoRecording)
    }

    @Test
    fun aFlaggedRingingCallGetsAWarningWithoutTheScreeningRole() = runTest {
        val lists = InMemoryLists()
        val controller = Controller(listOf(call("c1", "600111222")))
        val vm = viewModel(controller, lists)
        // Not flagged yet: no warning.
        assertFalse(vm.spam.value.getValue("c1").showWarning)
        // The user flagged it earlier; a new call from the same number is warned about while it rings.
        lists.add(ListType.OWN, EntryKind.NUMBER, "+34600111223")
        controller.calls.value = listOf(call("c2", "600111223"))
        assertTrue(vm.spam.value.getValue("c2").showWarning)
        assertNull(vm.spam.value["c1"])
    }

    @Test
    fun notSpamClearsTheWarningAndWhitelists() = runTest {
        val lists = InMemoryLists()
        lists.add(ListType.OWN, EntryKind.NUMBER, "+34600111222")
        val controller = Controller(listOf(call("c1", "600111222")))
        val vm = viewModel(controller, lists)
        assertTrue(vm.spam.value.getValue("c1").showWarning)
        vm.notSpam(controller.calls.value.single())
        val ui = vm.spam.value.getValue("c1")
        assertFalse(ui.showWarning)
        assertTrue(ui.allowed)
        assertTrue(lists.isWhitelisted("+34600111222"))
    }

    @Test
    fun markAsSpamDuringTheCallAddsToTheOwnList() = runTest {
        val lists = InMemoryLists()
        val controller = Controller(listOf(call("c1", "600111222", status = CallStatus.ACTIVE)))
        val vm = viewModel(controller, lists)
        assertTrue(vm.spam.value.getValue("c1").canMarkSpam(ringing = false))
        assertFalse(vm.spam.value.getValue("c1").canMarkSpam(ringing = true))
        vm.markSpam(controller.calls.value.single())
        assertTrue(vm.spam.value.getValue("c1").markedSpam)
        assertEquals("+34600111222", lists.matchOwn("+34600111222")?.value)
    }

    @Test
    fun hiddenNumbersGetNoSpamState() = runTest {
        val controller = Controller(listOf(call("c1", null)))
        val vm = viewModel(controller)
        assertNull(vm.spam.value["c1"])
    }

    @Test
    fun theDecisionIsTakenOncePerCall() = runTest {
        val lists = InMemoryLists()
        val dao = FakeDecisionDao()
        val controller = Controller(listOf(call("c1", "600111222")))
        viewModel(controller, lists, RoomDecisionStore(dao))
        // The same call updating (state changes) does not decide again.
        controller.calls.value = listOf(call("c1", "600111222", status = CallStatus.ACTIVE))
        assertEquals(1, dao.rows.value.size)
    }
}
