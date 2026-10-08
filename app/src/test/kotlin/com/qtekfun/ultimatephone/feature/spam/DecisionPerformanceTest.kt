package com.qtekfun.ultimatephone.feature.spam

import com.qtekfun.ultimatephone.core.spam.decision.BusinessLookup
import com.qtekfun.ultimatephone.core.spam.decision.BusinessMatch
import com.qtekfun.ultimatephone.core.spam.decision.SourceLookup
import com.qtekfun.ultimatephone.core.spam.decision.SourceMatch
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import com.qtekfun.ultimatephone.core.spam.lists.EntryKind
import com.qtekfun.ultimatephone.core.spam.lists.EntryValues
import com.qtekfun.ultimatephone.core.spam.lists.ImportResult
import com.qtekfun.ultimatephone.core.spam.lists.ListEntry
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.core.spam.lists.SpamListsRepository
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRule
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRuleSet
import com.qtekfun.ultimatephone.feature.recents.FakeContacts
import com.qtekfun.ultimatephone.feature.recents.FakeNormalizer
import com.qtekfun.ultimatephone.feature.recents.FakeRegion
import com.qtekfun.ultimatephone.screening.DecisionEngine
import com.qtekfun.ultimatephone.screening.RoomDecisionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hash-indexed stand-in for the indexed Room queries, so building 20,000 entries is instant. */
private class IndexedLists : SpamListsRepository {
    private val byList = ListType.entries.associateWith { HashMap<String, ListEntry>() }

    fun put(list: ListType, e164: String) {
        byList.getValue(list)[e164] = ListEntry("id-$e164", EntryKind.NUMBER, e164, null, null, 1, "dev")
    }

    override suspend fun isWhitelisted(e164: String) = lookup(ListType.WHITELIST, e164) != null

    override suspend fun matchOwn(e164: String) = lookup(ListType.OWN, e164)

    private fun lookup(list: ListType, e164: String): ListEntry? = EntryValues.best(e164, EntryValues.lookupKeys(e164).mapNotNull { byList.getValue(list)[it] })

    override fun observe(list: ListType): Flow<List<ListEntry>> = emptyFlow()

    override suspend fun add(list: ListType, kind: EntryKind, value: String, label: String?, note: String?): ListEntry = error("unused")

    override suspend fun remove(list: ListType, id: String): Boolean = error("unused")

    override suspend fun entries(list: ListType): List<ListEntry> = error("unused")

    override suspend fun exportJsonLines(list: ListType): String = error("unused")

    override suspend fun importJsonLines(list: ListType, lines: Sequence<String>): ImportResult = error("unused")
}

/**
 * The screening service has seconds, not minutes; the decision itself must be a rounding error of that. This runs
 * the whole engine with 10,000 entries in every list and a populated fake pack, on the JVM.
 */
class DecisionPerformanceTest {
    private fun number(i: Int) = "+34" + (600_000_000 + i).toString()

    @Test
    fun decisionWithTenThousandEntriesStaysUnderFiftyMilliseconds() = runTest {
        val lists = IndexedLists()
        repeat(SIZE) { i ->
            lists.put(ListType.OWN, number(i))
            lists.put(ListType.WHITELIST, number(SIZE + i))
        }
        val sourceMap = (0 until SIZE).associate { number(2 * SIZE + it) to SourceMatch("src-${it % 7}") }
        val businessMap = (0 until SIZE).associate { number(3 * SIZE + it) to BusinessMatch("Business $it", "shop") }
        val rules = PrefixRuleSet((0 until SIZE).map { PrefixRule("r-$it", "ES", (800_000 + it).toString()) })
        val packs = FakePacks(
            sources = SourceLookup { sourceMap[it] },
            businesses = BusinessLookup { businessMap[it] },
            prefixRules = rules
        )
        val engine = DecisionEngine(
            FakeContacts(),
            lists,
            packs,
            builtInRuleSet(),
            FakeSpamSettings(),
            FakeNormalizer(),
            FakeRegion(),
            RoomDecisionStore(FakeDecisionDao())
        )
        val probes = listOf(number(5), number(SIZE + 5), number(2 * SIZE + 5), number(3 * SIZE + 5), "+34800123456", "+34699999999")
        // Warm up the JIT and libphonenumber, then measure the slowest single decision.
        probes.forEach { engine.decide(it.removePrefix("+34"), record = false) }
        var slowest = 0L
        repeat(20) {
            probes.forEach { probe ->
                val started = System.nanoTime()
                engine.decide(probe.removePrefix("+34"), record = false)
                slowest = maxOf(slowest, System.nanoTime() - started)
            }
        }
        assertTrue("slowest decision took ${slowest / 1_000_000} ms", slowest < LIMIT_NANOS)
    }

    @Test
    fun theProbesReallyHitEveryStep() = runTest {
        val lists = InMemoryLists()
        lists.add(ListType.OWN, EntryKind.NUMBER, number(5))
        val engine = DecisionEngine(
            FakeContacts(),
            lists,
            FakePacks(sources = SourceLookup { if (it == number(7)) SourceMatch("src") else null }),
            builtInRuleSet(),
            FakeSpamSettings(),
            FakeNormalizer(),
            FakeRegion(),
            RoomDecisionStore(FakeDecisionDao())
        )
        assertEquals(SpamAction.WARN, engine.decide(number(5).removePrefix("+34"), record = false)!!.decision.action)
        assertEquals("src", engine.decide(number(7).removePrefix("+34"), record = false)!!.decision.sourceId)
    }

    private companion object {
        const val SIZE = 10_000
        const val LIMIT_NANOS = 50_000_000L
    }
}
