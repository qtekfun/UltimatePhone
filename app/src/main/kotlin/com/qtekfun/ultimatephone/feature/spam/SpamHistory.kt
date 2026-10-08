package com.qtekfun.ultimatephone.feature.spam

import com.qtekfun.ultimatephone.screening.DecisionStore
import com.qtekfun.ultimatephone.screening.SpamVerdict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The latest recorded decision of every number, keyed by E.164, for the history badge and filter. */
interface SpamHistory {
    val verdicts: Flow<Map<String, SpamVerdict>>
}

/** No decisions known; keeps the history working where the spam feature is not wired (tests). */
object NoSpamHistory : SpamHistory {
    override val verdicts: Flow<Map<String, SpamVerdict>> = flowOf(emptyMap())
}

class StoreSpamHistory(store: DecisionStore) : SpamHistory {
    override val verdicts: Flow<Map<String, SpamVerdict>> = store.observeLatest()
}
