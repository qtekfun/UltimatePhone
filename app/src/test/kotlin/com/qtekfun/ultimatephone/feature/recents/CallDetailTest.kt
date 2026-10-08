package com.qtekfun.ultimatephone.feature.recents

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.qtekfun.ultimatephone.core.calllog.CallType
import com.qtekfun.ultimatephone.core.calllog.NumberKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DetailActionsTest {
    private var log = mutableListOf<String>()
    private val handlers = DetailHandlers(
        onCall = { log += "call" },
        onCreateContact = { log += "create" },
        onAddToExistingContact = { log += "add" },
        onOpenContact = { log += "open:$it" },
        onEditContact = { log += "edit:$it" },
        onDelete = { log += "delete" }
    )

    private fun ids(caller: CallerInfo?, isPrivate: Boolean = false, hasCalls: Boolean = true) =
        detailActions(caller, isPrivate, hasCalls, handlers).map { it.id }

    @Test
    fun unknownNumberCanBeCalledAndSavedAsContact() {
        val caller = CallerInfo(null, null, "600 11 12 22")
        assertEquals(
            listOf(DetailActionId.CALL, DetailActionId.CREATE_CONTACT, DetailActionId.ADD_TO_CONTACT, DetailActionId.DELETE),
            ids(caller)
        )
    }

    @Test
    fun knownNumberOffersOpenAndEditInsteadOfCreate() {
        val caller = CallerInfo(match("Ana", "lk1"), "Ana", "600")
        val actions = detailActions(caller, false, true, handlers)
        assertEquals(listOf(DetailActionId.CALL, DetailActionId.OPEN_CONTACT, DetailActionId.EDIT_CONTACT, DetailActionId.DELETE), actions.map { it.id })
        actions.single { it.id == DetailActionId.OPEN_CONTACT }.onClick()
        actions.single { it.id == DetailActionId.EDIT_CONTACT }.onClick()
        assertEquals(listOf("open:lk1", "edit:lk1"), log)
    }

    @Test
    fun hiddenNumberOnlyOffersDelete() {
        assertEquals(listOf(DetailActionId.DELETE), ids(CallerInfo(null, null, ""), isPrivate = true))
    }

    @Test
    fun nothingToDeleteHidesDelete() {
        assertTrue(DetailActionId.DELETE !in ids(CallerInfo(null, null, "600"), hasCalls = false))
    }

    @Test
    fun noCallerYetStillOffersTheBasics() {
        assertTrue(DetailActionId.CALL in ids(null))
    }

    @Test
    fun noActionIsAPlaceholder() {
        // Every listed action must do something: invoking each one reaches a handler.
        detailActions(CallerInfo(null, null, "600"), false, true, handlers).forEach { it.onClick() }
        assertEquals(listOf("call", "create", "add", "delete"), log)
    }

    @Test
    fun lookupKeyIsExtractedFromAPickedContactUri() {
        assertEquals("0r1-2A3B", lookupKeyFromContactUri("content://com.android.contacts/contacts/lookup/0r1-2A3B/42"))
        assertEquals("3789r12-abc%2B", lookupKeyFromContactUri("content://com.android.contacts/contacts/lookup/3789r12-abc%2B/7"))
        assertEquals("abc", lookupKeyFromContactUri("content://com.android.contacts/contacts/lookup/abc"))
    }

    @Test
    fun otherUrisHaveNoLookupKey() {
        assertNull(lookupKeyFromContactUri("content://com.android.contacts/contacts/42"))
        assertNull(lookupKeyFromContactUri("content://com.android.contacts/contacts/lookup/"))
        assertNull(lookupKeyFromContactUri(""))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CallDetailViewModelTest {
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

    private fun viewModel(number: String) = CallDetailViewModel(
        SavedStateHandle(mapOf(CallDetailViewModel.NUMBER_ARG to number)),
        log,
        contacts,
        placer,
        NumberKeys(FakeNormalizer(), FakeRegion()),
        FakeNormalizer(),
        FakeRegion()
    )

    private suspend fun app.cash.turbine.ReceiveTurbine<CallDetailUiState>.loaded(): CallDetailUiState {
        while (true) {
            val item = awaitItem()
            if (!item.isLoading) return item
        }
    }

    @Test
    fun showsOnlyTheCallsOfThatNumberWithStatsAndContact() = runTest {
        log.entries.value = listOf(
            entry(5, number = "+34 600 111 222", type = CallType.MISSED),
            entry(4, number = "699000111"),
            entry(3, number = "600111222", type = CallType.OUTGOING, duration = 90),
            entry(2, number = "600111222", type = CallType.INCOMING, duration = 30)
        )
        viewModel("600111222").uiState.test {
            val state = loaded()
            assertEquals(listOf(5L, 3L, 2L), state.entries.map { it.id })
            assertEquals("Ana", state.caller?.title)
            assertEquals(3, state.stats.total)
            assertEquals(1, state.stats.missed)
            assertEquals(1, state.stats.incoming)
            assertEquals(1, state.stats.outgoing)
            assertEquals(120, state.stats.totalDurationSeconds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unknownNumbersFallBackToTheFormattedNumber() = runTest {
        log.entries.value = listOf(entry(1, number = "699000111"))
        viewModel("699000111").uiState.test {
            assertEquals("fmt:699000111", loaded().caller?.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deletingRemovesEveryCallOfTheNumberAndSignalsTheScreen() = runTest {
        log.entries.value = listOf(entry(3, number = "600111222"), entry(2, number = "699000111"), entry(1, number = "+34600111222"))
        val vm = viewModel("600111222")
        vm.uiState.test {
            loaded()
            vm.events.test {
                vm.deleteHistory()
                assertEquals(CallDetailEvent.Deleted, awaitItem())
            }
            assertEquals(listOf(3L, 1L), log.deleted)
            assertEquals(listOf(2L), log.entries.value.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun callPlacesTheCallOrReportsFailure() = runTest {
        val vm = viewModel("600111222")
        vm.events.test {
            vm.call()
            assertEquals(listOf("600111222"), placer.placed)
            placer.succeeds = false
            vm.call()
            assertEquals(CallDetailEvent.CallFailed, awaitItem())
        }
    }

    @Test
    fun hiddenNumbersAreNeverCalled() = runTest {
        viewModel("").call()
        assertTrue(placer.placed.isEmpty())
    }
}

class CallerResolverTest {
    @Test
    fun formatsAndPrefersContactNameThenCachedName() = kotlinx.coroutines.test.runTest {
        val resolver = CallerResolver(FakeContacts(mapOf("600111222" to match("Ana"))), FakeNormalizer(), FakeRegion())
        assertEquals("Ana", resolver.resolve("600111222", "Old").title)
        assertEquals("Old", resolver.resolve("699000111", "Old").title)
        assertEquals("fmt:699000111", resolver.resolve("699000111", "  ").title)
    }

    @Test
    fun cachesUntilCleared() = kotlinx.coroutines.test.runTest {
        val contacts = FakeContacts()
        val resolver = CallerResolver(contacts, FakeNormalizer(), FakeRegion())
        resolver.resolve("600111222", null)
        resolver.resolve("600111222", null)
        assertEquals(1, contacts.lookups)
        assertTrue(resolver.clear())
        resolver.resolve("600111222", null)
        assertEquals(2, contacts.lookups)
    }
}
