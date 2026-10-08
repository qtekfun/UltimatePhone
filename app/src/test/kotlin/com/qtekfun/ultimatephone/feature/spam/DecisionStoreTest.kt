package com.qtekfun.ultimatephone.feature.spam

import com.qtekfun.ultimatephone.core.spam.decision.Decision
import com.qtekfun.ultimatephone.core.spam.decision.DecisionReason
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.screening.RoomDecisionStore
import com.qtekfun.ultimatephone.screening.SpamVerdict
import com.qtekfun.ultimatephone.screening.toEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionStoreTest {
    private val dao = FakeDecisionDao()
    private val day = 24L * 60 * 60 * 1000

    private fun verdict(e164: String, at: Long, flagged: Boolean = true, informational: Boolean = false) = SpamVerdict(
        e164,
        if (flagged) {
            Decision(SpamAction.WARN, SpamLevel.COMMUNITY, "src-a", "label", null, DecisionReason.SOURCE)
        } else {
            Decision(SpamAction.NONE, null, null, null, null, DecisionReason.NO_MATCH)
        },
        informational,
        at
    )

    @Test
    fun recordedDecisionsAreReadBackWithAllTheirFields() = runTest {
        val store = RoomDecisionStore(dao)
        store.record(verdict("+34600000001", at = 100))
        val back = store.latest("+34600000001")!!
        assertEquals(SpamLevel.COMMUNITY, back.decision.level)
        assertEquals(SpamAction.WARN, back.decision.action)
        assertEquals("src-a", back.decision.sourceId)
        assertEquals("label", back.decision.label)
        assertEquals(DecisionReason.SOURCE, back.decision.reason)
        assertEquals(100L, back.decidedAt)
    }

    @Test
    fun theNewestDecisionOfANumberWins() = runTest {
        val store = RoomDecisionStore(dao)
        store.record(verdict("+34600000001", at = 100))
        store.record(verdict("+34600000001", at = 200, flagged = false))
        val all = store.observeLatest().first()
        assertEquals(1, all.size)
        assertTrue(!all.getValue("+34600000001").isSpam)
    }

    @Test
    fun pruneDeletesRowsOlderThanTheRetention() = runTest {
        val store = RoomDecisionStore(dao, retentionMillis = 90 * day)
        val now = 200 * day
        dao.insert(verdict("+34600000001", at = now - 91 * day).toEntity())
        dao.insert(verdict("+34600000002", at = now - 89 * day).toEntity())
        store.prune(now)
        assertEquals(listOf(now - 90 * day), dao.cutoffs)
        assertEquals(listOf("+34600000002"), dao.rows.value.map { it.e164 })
    }

    @Test
    fun recordingPrunesAtMostOnceEverySixHours() = runTest {
        val store = RoomDecisionStore(dao)
        val start = 1_000 * day
        store.record(verdict("+34600000001", at = start))
        store.record(verdict("+34600000002", at = start + 1_000))
        assertEquals(1, dao.cutoffs.size)
        store.record(verdict("+34600000003", at = start + 7 * 60 * 60 * 1000))
        assertEquals(2, dao.cutoffs.size)
    }

    @Test
    fun liveDecisionsExpireAfterTheirTtl() = runTest {
        val store = RoomDecisionStore(dao, liveTtlMillis = 30_000)
        store.record(verdict("+34600000001", at = 1_000))
        assertEquals(1_000L, store.live("+34600000001", now = 20_000)?.decidedAt)
        assertNull(store.live("+34600000001", now = 40_000))
        assertNull(store.live("+34600000009", now = 2_000))
    }

    @Test
    fun theLiveMapIsBoundedAndDropsTheOldest() {
        val store = RoomDecisionStore(dao, liveTtlMillis = 1_000_000, maxLive = 2)
        store.remember(verdict("+34600000001", at = 10))
        store.remember(verdict("+34600000002", at = 20))
        store.remember(verdict("+34600000003", at = 30))
        assertNull(store.live("+34600000001", now = 40))
        assertEquals(20L, store.live("+34600000002", now = 40)?.decidedAt)
        assertEquals(30L, store.live("+34600000003", now = 40)?.decidedAt)
    }

    @Test
    fun clearLiveForgetsEverythingInMemoryOnly() = runTest {
        val store = RoomDecisionStore(dao)
        store.record(verdict("+34600000001", at = 1_000))
        store.clearLive()
        assertNull(store.live("+34600000001", now = 1_001))
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun pruneAlsoDropsStaleLiveEntries() = runTest {
        val store = RoomDecisionStore(dao, liveTtlMillis = 30_000)
        store.remember(verdict("+34600000001", at = 1_000))
        store.prune(now = 100_000)
        assertNull(store.live("+34600000001", now = 1_001))
    }
}
