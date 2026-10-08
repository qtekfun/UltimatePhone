package com.qtekfun.ultimatephone.screening

import com.qtekfun.ultimatephone.core.spam.decision.Decision
import com.qtekfun.ultimatephone.core.spam.decision.DecisionReason
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.lists.CallDecisionDao
import com.qtekfun.ultimatephone.core.spam.lists.CallDecisionEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A [Decision] for one number at one moment.
 *
 * @property e164 the normalised number the decision is about.
 * @property informational the match was an informational rule (a commercial call, not unwanted): shown calmly.
 */
data class SpamVerdict(val e164: String, val decision: Decision, val informational: Boolean, val decidedAt: Long) {
    /** Flagged as something unwanted, as opposed to nothing found or merely informational. */
    val isSpam: Boolean get() = decision.isFlagged && !informational
}

/**
 * Where decisions are kept. The live map answers the in-call screen without touching the disk; the persisted rows
 * answer "why was this flagged" and feed the history badge.
 */
interface DecisionStore {
    /** The decision taken for [e164] in the last few seconds of a live call, or null. */
    fun live(e164: String, now: Long): SpamVerdict?

    /** Keeps [verdict] in the live map only; instant, for callers on a deadline that persist afterwards with [record]. */
    fun remember(verdict: SpamVerdict)

    /** Keeps [verdict] in memory and persists it. Also prunes old rows now and then. */
    suspend fun record(verdict: SpamVerdict)

    /** Forgets the in-memory decisions, for when lists, settings or packs changed. */
    fun clearLive()

    /** The newest persisted verdict of every number, keyed by E.164. */
    fun observeLatest(): Flow<Map<String, SpamVerdict>>

    suspend fun latest(e164: String): SpamVerdict?

    /** Deletes persisted rows older than the retention period and drops stale live entries. */
    suspend fun prune(now: Long)
}

/** [DecisionStore] on the `call_decision` table. */
class RoomDecisionStore(
    private val dao: CallDecisionDao,
    private val liveTtlMillis: Long = LIVE_TTL_MS,
    private val retentionMillis: Long = RETENTION_MS,
    private val maxLive: Int = MAX_LIVE
) : DecisionStore {
    private val liveMap = ConcurrentHashMap<String, SpamVerdict>()
    private val lastPrune = AtomicLong(0)

    override fun live(e164: String, now: Long): SpamVerdict? {
        val verdict = liveMap[e164] ?: return null
        return verdict.takeIf { now - it.decidedAt in 0..liveTtlMillis }
    }

    override fun remember(verdict: SpamVerdict) {
        liveMap[verdict.e164] = verdict
        evictLive(verdict.decidedAt)
    }

    override suspend fun record(verdict: SpamVerdict) {
        remember(verdict)
        dao.insert(verdict.toEntity())
        if (verdict.decidedAt - lastPrune.get() >= PRUNE_INTERVAL_MS) prune(verdict.decidedAt)
    }

    override fun clearLive() = liveMap.clear()

    override fun observeLatest(): Flow<Map<String, SpamVerdict>> = dao.observeLatest().map { rows -> rows.associate { it.e164 to it.toVerdict() } }

    override suspend fun latest(e164: String): SpamVerdict? = dao.latestFor(e164)?.toVerdict()

    override suspend fun prune(now: Long) {
        lastPrune.set(now)
        dao.deleteOlderThan(now - retentionMillis)
        evictLive(now)
    }

    /** Drops expired entries, and the oldest ones beyond [maxLive]. */
    private fun evictLive(now: Long) {
        liveMap.entries.removeIf { now - it.value.decidedAt > liveTtlMillis }
        val excess = liveMap.size - maxLive
        if (excess > 0) liveMap.entries.sortedBy { it.value.decidedAt }.take(excess).forEach { liveMap.remove(it.key) }
    }

    companion object {
        const val LIVE_TTL_MS = 30_000L
        const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
        const val PRUNE_INTERVAL_MS = 6L * 60 * 60 * 1000
        const val MAX_LIVE = 64
    }
}

internal fun SpamVerdict.toEntity() = CallDecisionEntity(
    e164 = e164,
    decidedAt = decidedAt,
    level = decision.level?.name,
    action = decision.action.name,
    sourceId = decision.sourceId,
    label = decision.label,
    reason = decision.reason.name,
    informational = informational
)

internal fun CallDecisionEntity.toVerdict() = SpamVerdict(
    e164 = e164,
    decision = Decision(
        action = SpamAction.entries.firstOrNull { it.name == action } ?: SpamAction.NONE,
        level = level?.let { name -> SpamLevel.entries.firstOrNull { it.name == name } },
        sourceId = sourceId,
        label = label,
        category = null,
        reason = DecisionReason.entries.firstOrNull { it.name == reason } ?: DecisionReason.NO_MATCH
    ),
    informational = informational,
    decidedAt = decidedAt
)
