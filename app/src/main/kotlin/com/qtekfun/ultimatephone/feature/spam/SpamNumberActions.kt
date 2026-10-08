package com.qtekfun.ultimatephone.feature.spam

import com.qtekfun.ultimatephone.core.phonenumber.NormalizedNumber
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.spam.lists.EntryKind
import com.qtekfun.ultimatephone.core.spam.lists.ListEntry
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.core.spam.lists.SpamListsRepository
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.screening.DecisionEngine
import com.qtekfun.ultimatephone.screening.SpamVerdict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * Where one number stands in the user's lists, for the buttons of the history detail and the in-call screen.
 *
 * @property available false for numbers that cannot be listed (hidden, malformed): no spam buttons at all.
 * @property inOwnList the number itself is in the spam list (a prefix that covers it does not count).
 * @property inAllowed the number itself is in the allowed numbers.
 * @property verdict the latest recorded decision for the number, if any.
 */
data class NumberSpamState(val available: Boolean = false, val inOwnList: Boolean = false, val inAllowed: Boolean = false, val verdict: SpamVerdict? = null) {
    companion object {
        val Hidden = NumberSpamState()
    }
}

/** What the user can do about a number from the history and the call screen. */
interface SpamNumberActions {
    fun observeState(rawNumber: String): Flow<NumberSpamState>

    /** Adds the number to the spam list (and takes it out of the allowed numbers). False if it cannot be listed. */
    suspend fun markSpam(rawNumber: String): Boolean

    suspend fun removeFromSpam(rawNumber: String): Boolean

    /** Adds the number to the allowed numbers (and takes it out of the spam list). False if it cannot be listed. */
    suspend fun allow(rawNumber: String): Boolean

    suspend fun removeFromAllowed(rawNumber: String): Boolean
}

/** Used where no spam feature is wired, for example in tests of other screens. */
object NoSpamNumberActions : SpamNumberActions {
    override fun observeState(rawNumber: String): Flow<NumberSpamState> = flowOf(NumberSpamState.Hidden)

    override suspend fun markSpam(rawNumber: String) = false

    override suspend fun removeFromSpam(rawNumber: String) = false

    override suspend fun allow(rawNumber: String) = false

    override suspend fun removeFromAllowed(rawNumber: String) = false
}

class ListsSpamNumberActions(
    private val lists: SpamListsRepository,
    private val engine: DecisionEngine,
    private val history: SpamHistory,
    private val normalizer: PhoneNormalizer,
    private val regions: RegionProvider
) : SpamNumberActions {
    private fun e164Of(raw: String): String? = (normalizer.normalize(raw, regions.defaultRegion()) as? NormalizedNumber.Valid)?.e164

    override fun observeState(rawNumber: String): Flow<NumberSpamState> {
        val e164 = e164Of(rawNumber) ?: return flowOf(NumberSpamState.Hidden)
        return combine(lists.observe(ListType.OWN), lists.observe(ListType.WHITELIST), history.verdicts) { own, allowed, verdicts ->
            NumberSpamState(
                available = true,
                inOwnList = own.any { it.isNumber(e164) },
                inAllowed = allowed.any { it.isNumber(e164) },
                verdict = verdicts[e164]
            )
        }
    }

    override suspend fun markSpam(rawNumber: String): Boolean {
        val e164 = e164Of(rawNumber) ?: return false
        removeExact(ListType.WHITELIST, e164)
        lists.add(ListType.OWN, EntryKind.NUMBER, e164)
        refreshDecision(rawNumber)
        return true
    }

    override suspend fun removeFromSpam(rawNumber: String): Boolean {
        val e164 = e164Of(rawNumber) ?: return false
        val removed = removeExact(ListType.OWN, e164)
        refreshDecision(rawNumber)
        return removed
    }

    override suspend fun allow(rawNumber: String): Boolean {
        val e164 = e164Of(rawNumber) ?: return false
        removeExact(ListType.OWN, e164)
        lists.add(ListType.WHITELIST, EntryKind.NUMBER, e164)
        refreshDecision(rawNumber)
        return true
    }

    override suspend fun removeFromAllowed(rawNumber: String): Boolean {
        val e164 = e164Of(rawNumber) ?: return false
        val removed = removeExact(ListType.WHITELIST, e164)
        refreshDecision(rawNumber)
        return removed
    }

    private suspend fun removeExact(list: ListType, e164: String): Boolean {
        val ids = lists.entries(list).filter { !it.deleted && it.isNumber(e164) }.map { it.id }
        return ids.map { lists.remove(list, it) }.any { it }
    }

    /** Records the decision the number gets now, so the next screening and the history badge agree with the list. */
    private suspend fun refreshDecision(rawNumber: String) {
        engine.decide(rawNumber, record = true)
    }

    private fun ListEntry.isNumber(e164: String) = kind == EntryKind.NUMBER && value == e164 && !deleted
}
