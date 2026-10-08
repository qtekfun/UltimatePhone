package com.qtekfun.ultimatephone.core.spam.decision

import com.qtekfun.ultimatephone.core.phonenumber.LibPhoneNormalizer
import com.qtekfun.ultimatephone.core.phonenumber.NormalizedNumber
import com.qtekfun.ultimatephone.core.spam.prefix.LazyPrefixRules
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRule
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRuleSet
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Benchmarks as tests for the incoming-call decision. The target is 50 ms per decision on the phone with every pack
 * installed; the budgets here are deliberately loose (CI machines are noisy and shared) but far above the cost
 * measured on a laptop, so an accidental linear scan or a rebuild per call fails them while normal variance does not.
 * They exercise the pure in-memory part of the path: normalisation, the decider, the user's lists, a large pack
 * and the prefix rules. Contacts, the lists query and the pack query of the real phone are single indexed lookups.
 */
class ScreeningBenchmarkTest {
    private val normalizer = LibPhoneNormalizer()

    /** 10,000 entries in the user's list. */
    private val ownNumbers: Set<String> = (0 until OWN_ENTRIES).map { "+346${(10_000_000 + it).toString().padStart(8, '0')}" }.toSet()
    private val ownList = OwnList { e164 -> if (e164 in ownNumbers) OwnMatch("own", e164) else null }

    /** A fake pack of 500,000 spam numbers, answered with one hash lookup like the primary key of a real pack. */
    private val packNumbers: Set<String> = HashSet<String>(PACK_ENTRIES * 2).also { set ->
        for (i in 0 until PACK_ENTRIES) set += "+3490${(1_000_000 + i).toString().padStart(7, '0')}"
    }
    private val sources = SourceLookup { e164 -> if (e164 in packNumbers) SourceMatch("spam-es", SpamLevel.COMMUNITY, null) else null }

    /** 3,000 rules over many country codes, as the installed rule packs plus the built-in ones. */
    private val rules = PrefixRuleSet(
        (0 until RULES).map { PrefixRule("rule-$it", country = null, prefix = "+${20 + it % 900}${1000 + it}", level = SpamLevel.RULE) } +
            PrefixRule("es-400-commercial", "ES", "400", SpamLevel.RULE, informational = true)
    )

    private fun context(raw: String?) = DecisionContext(
        contacts = ContactChecker { false },
        whitelist = Whitelist { false },
        ownList = ownList,
        businesses = BusinessLookup { null },
        sources = sources,
        prefixRules = rules,
        rawNumber = raw,
        region = "ES"
    )

    private fun decide(raw: String): Decision = SpamDecider.decide(normalizer.normalize(raw, "ES"), context(raw))

    private fun numberFor(i: Int): String = when (i % 5) {
        0 -> "+346${(10_000_000 + (i * 7) % OWN_ENTRIES).toString().padStart(8, '0')}"
        1 -> "+3490${(1_000_000 + (i * 13) % PACK_ENTRIES).toString().padStart(7, '0')}"
        2 -> "+34400${(100_000 + i).toString().padStart(6, '0')}"
        3 -> "612${(345_000 + i).toString().padStart(6, '0')}"
        else -> "+4420${(7_000_000 + i).toString().padStart(8, '0')}"
    }

    @Test
    fun answersAreCorrectForEachKindOfHit() {
        assertEquals(DecisionReason.OWN_LIST, decide(numberFor(0)).reason)
        assertEquals(DecisionReason.SOURCE, decide(numberFor(1)).reason)
        assertEquals(DecisionReason.PREFIX_RULE, decide(numberFor(2)).reason)
        assertEquals(DecisionReason.NO_MATCH, decide(numberFor(3)).reason)
    }

    @Test
    fun athousandConsecutiveDecisionsStayInsideTheBudget() {
        repeat(WARM_UP_ROUNDS) { decide(numberFor(it)) } // libphonenumber metadata and JIT; the app does this in the background
        val started = System.nanoTime()
        var flagged = 0
        for (i in 0 until DECISIONS) if (decide(numberFor(i)).isFlagged) flagged++
        val totalMs = (System.nanoTime() - started) / NANOS_PER_MILLI
        assertTrue("some of the numbers must be flagged, got $flagged", flagged > 0)
        println("1000 decisions (10k list entries, 500k pack, ${RULES + 1} rules): $totalMs ms")
        assertTrue("1000 decisions took $totalMs ms, budget $BUDGET_MS ms", totalMs < BUDGET_MS)
    }

    @Test
    fun noSingleDecisionComesNearTheFiftyMillisecondTarget() {
        repeat(WARM_UP_ROUNDS) { decide(numberFor(it)) }
        var slowest = 0L
        for (i in 0 until DECISIONS) {
            val started = System.nanoTime()
            decide(numberFor(i))
            slowest = maxOf(slowest, System.nanoTime() - started)
        }
        val slowestMs = slowest / NANOS_PER_MILLI
        println("slowest of $DECISIONS decisions: $slowestMs ms")
        // A garbage collection pause can land on one decision; three times the target still catches a real regression.
        assertTrue("slowest decision took $slowestMs ms", slowestMs < SLOWEST_BUDGET_MS)
    }

    @Test
    fun prefixMatchingStaysFastWithManyRules() {
        repeat(WARM_UP_ROUNDS * 50) { rules.match("+34612345678", "ES") }
        val started = System.nanoTime()
        repeat(MATCHES) { rules.match("+34612345678", "ES") }
        val ms = (System.nanoTime() - started) / NANOS_PER_MILLI
        println("$MATCHES prefix matches against ${RULES + 1} rules: $ms ms")
        assertTrue("prefix matching took $ms ms for $MATCHES numbers", ms < MATCH_BUDGET_MS)
    }

    @Test
    fun hiddenAndShortNumbersAreCheapAndNeverFlagged() {
        val started = System.nanoTime()
        repeat(DECISIONS) {
            assertEquals(NormalizedNumber.Unknown, normalizer.normalize("", "ES"))
            assertEquals(NormalizedNumber.Unknown, normalizer.normalize("112", "ES"))
        }
        val ms = (System.nanoTime() - started) / NANOS_PER_MILLI
        assertTrue("took $ms ms", ms < BUDGET_MS)
    }

    @Test
    fun lazyBuiltInRulesAreBuiltOnceOnFirstUse() {
        val builds = AtomicInteger()
        val lazy = LazyPrefixRules {
            builds.incrementAndGet()
            builtInEs400()
        }
        assertEquals(0, builds.get())
        assertNull(lazy.match("+34600000000", "ES"))
        assertNotNull(lazy.match("+34400123456", "ES"))
        lazy.warmUp()
        repeat(DECISIONS) { lazy.match("+34400123456", "ES") }
        assertEquals(1, builds.get())
    }

    private companion object {
        const val OWN_ENTRIES = 10_000
        const val PACK_ENTRIES = 500_000
        const val RULES = 3_000
        const val DECISIONS = 1_000
        const val WARM_UP_ROUNDS = 200
        const val MATCHES = 100_000
        const val NANOS_PER_MILLI = 1_000_000L
        const val BUDGET_MS = 2_000L
        const val SLOWEST_BUDGET_MS = 150L
        const val MATCH_BUDGET_MS = 2_000L
    }
}
