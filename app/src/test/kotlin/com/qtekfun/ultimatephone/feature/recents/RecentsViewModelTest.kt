package com.qtekfun.ultimatephone.feature.recents

import app.cash.turbine.test
import com.qtekfun.ultimatephone.core.calllog.CallLogFilter
import com.qtekfun.ultimatephone.core.calllog.CallType
import com.qtekfun.ultimatephone.core.calllog.DaySection
import com.qtekfun.ultimatephone.core.calllog.NumberKeys
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class RecentsViewModelTest {
    private val now = Instant.parse("2026-10-08T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val today = now.toEpochMilli() - 3_600_000
    private val yesterday = now.toEpochMilli() - 26 * 3_600_000

    private lateinit var log: FakeCallLog
    private lateinit var contacts: FakeContacts
    private lateinit var placer: FakePlacer

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        log = FakeCallLog()
        contacts = FakeContacts(mapOf("600111222" to match("Ana")))
        placer = FakePlacer()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): RecentsViewModel {
        val resolver = CallerResolver(contacts, FakeNormalizer(), FakeRegion())
        return RecentsViewModel(log, FakeSims(), placer, NumberKeys(FakeNormalizer(), FakeRegion()), resolver, clock)
    }

    /** The first state that is no longer the initial loading one. */
    private suspend fun RecentsViewModel.loaded() = uiState.first { !it.isLoading }

    @Test
    fun startsLoadingThenShowsRowsUnderDaySections() = runTest {
        log.entries.value =
            listOf(entry(3, date = today), entry(2, number = "699000111", date = yesterday), entry(1, number = "699000111", date = yesterday - 1000))
        val vm = viewModel()
        assertTrue(vm.uiState.value.isLoading)
        vm.uiState.test {
            val state = awaitState { !it.isLoading }
            assertEquals(listOf(DaySection.Today, DaySection.Yesterday), state.sections.map { it.section })
            assertEquals(listOf(3L), state.sections[0].rows.map { it.key })
            assertEquals(listOf(2L, 1L), state.sections[1].rows.single().entryIds)
            assertEquals(2, state.sections[1].rows.single().count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun titleIsContactNameThenCachedNameThenFormattedNumber() = runTest {
        log.entries.value = listOf(
            entry(3, number = "600111222", cachedName = "Stale name", date = today),
            entry(2, number = "699000111", cachedName = "Cached", date = today - 1000, type = CallType.OUTGOING),
            entry(1, number = "655444333", date = today - 2000, type = CallType.MISSED)
        )
        val vm = viewModel()
        vm.uiState.test {
            val rows = awaitState { !it.isLoading }.sections.single().rows
            assertEquals(listOf("Ana", "Cached", "fmt:655444333"), rows.map { it.caller.title })
            assertEquals(listOf("key-Ana", null, null), rows.map { it.caller.contact?.lookupKey })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun hiddenNumbersHaveNoTitleAndAreNotLookedUp() = runTest {
        log.entries.value = listOf(entry(1, number = "", date = today), entry(2, number = "-1", date = today - 1000))
        val vm = viewModel()
        val rows = vm.loaded().sections.single().rows
        assertTrue(rows.all { it.isPrivate && it.caller.title == null })
        assertEquals(0, contacts.lookups)
    }

    @Test
    fun eachNumberIsLookedUpOnce() = runTest {
        log.entries.value = listOf(entry(3, date = today), entry(2, type = CallType.MISSED, date = today - 1000), entry(1, date = today - 2000))
        viewModel().loaded()
        assertEquals(1, contacts.lookups)
    }

    @Test
    fun filterRestrictsRowsAndClearsSelection() = runTest {
        log.entries.value =
            listOf(entry(2, type = CallType.MISSED, date = today), entry(1, type = CallType.OUTGOING, number = "699000111", date = today - 1000))
        val vm = viewModel()
        vm.uiState.test {
            skipLoading()
            vm.toggleSelection(2)
            assertEquals(setOf(2L), awaitState { it.selected.isNotEmpty() }.selected)
            vm.setFilter(CallLogFilter.Outgoing)
            // The chip updates at once; the rows follow when the filtered query answers.
            val state = awaitState { it.filter == CallLogFilter.Outgoing && it.sections.flatMap { s -> s.rows }.map { r -> r.key } == listOf(1L) }
            assertEquals(listOf(1L), state.sections.single().rows.map { it.key })
            assertTrue(state.selected.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<RecentsUiState>.skipLoading() {
        awaitState { !it.isLoading }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<RecentsUiState>.awaitState(predicate: (RecentsUiState) -> Boolean): RecentsUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun deletingASelectedGroupDeletesEveryCallInIt() = runTest {
        log.entries.value = listOf(entry(3, date = today), entry(2, date = today - 1000), entry(1, number = "699000111", date = today - 2000))
        val vm = viewModel()
        vm.uiState.test {
            skipLoading()
            vm.toggleSelection(3)
            awaitState { it.isSelecting }
            vm.deleteSelected()
            val state = awaitState { it.sections.flatMap { s -> s.rows }.size == 1 }
            assertEquals(listOf(3L, 2L), log.deleted)
            assertTrue(state.selected.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun togglingTwiceUnselects() = runTest {
        log.entries.value = listOf(entry(1, date = today))
        val vm = viewModel()
        vm.uiState.test {
            skipLoading()
            vm.toggleSelection(1)
            assertTrue(awaitState { it.isSelecting }.isSelecting)
            vm.toggleSelection(1)
            assertFalse(awaitState { !it.isSelecting }.isSelecting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearHistoryClearsEverything() = runTest {
        log.entries.value = listOf(entry(1, date = today))
        val vm = viewModel()
        vm.uiState.test {
            skipLoading()
            vm.clearHistory()
            assertTrue(awaitState { it.isEmpty }.isEmpty)
            assertEquals(1, log.cleared)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun markAllReadForwardsToTheRepository() = runTest {
        viewModel().markAllRead()
        assertEquals(1, log.markedRead)
    }

    @Test
    fun callBackPlacesTheCallAndReportsFailure() = runTest {
        log.entries.value = listOf(entry(1, date = today))
        val vm = viewModel()
        val row = vm.loaded().sections.single().rows.single()
        vm.events.test {
            vm.callBack(row)
            assertEquals(listOf("600111222"), placer.placed)
            expectNoEvents()
            placer.succeeds = false
            vm.callBack(row)
            assertEquals(RecentsEvent.CallFailed, awaitItem())
        }
    }

    @Test
    fun hiddenNumbersCannotBeCalledBack() = runTest {
        log.entries.value = listOf(entry(1, number = "", date = today))
        val vm = viewModel()
        vm.callBack(vm.loaded().sections.single().rows.single())
        assertTrue(placer.placed.isEmpty())
    }

    @Test
    fun refreshingContactsReReadsNames() = runTest {
        log.entries.value = listOf(entry(1, number = "699000111", date = today))
        val vm = viewModel()
        vm.uiState.test {
            val before = awaitState { !it.isLoading }
            assertNull(before.sections.single().rows.single().caller.contact)
            contacts.byNumber = mapOf("699000111" to match("Bea"))
            vm.refreshContacts()
            val after = awaitState { it.sections.firstOrNull()?.rows?.firstOrNull()?.caller?.contact != null }
            assertEquals("Bea", after.sections.single().rows.single().caller.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun newCallsAreFlagged() = runTest {
        log.entries.value = listOf(entry(2, type = CallType.MISSED, isNew = true, date = today), entry(1, number = "699000111", date = today - 1000))
        val rows = viewModel().loaded().sections.single().rows
        assertEquals(listOf(true, false), rows.map { it.isNew })
    }
}
