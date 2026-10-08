package com.qtekfun.ultimatephone.feature.spam

import com.qtekfun.ultimatephone.core.spam.decision.BusinessLookup
import com.qtekfun.ultimatephone.core.spam.decision.PrefixRules
import com.qtekfun.ultimatephone.core.spam.decision.SourceLookup
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.lists.CallDecisionDao
import com.qtekfun.ultimatephone.core.spam.lists.CallDecisionEntity
import com.qtekfun.ultimatephone.core.spam.lists.EntryKind
import com.qtekfun.ultimatephone.core.spam.lists.EntryValues
import com.qtekfun.ultimatephone.core.spam.lists.ImportResult
import com.qtekfun.ultimatephone.core.spam.lists.ListEntry
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.core.spam.lists.SpamListsRepository
import com.qtekfun.ultimatephone.core.spam.prefix.BuiltInRules
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRule
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRuleSet
import com.qtekfun.ultimatephone.data.InstalledPackLookups
import com.qtekfun.ultimatephone.screening.SpamPrefs
import com.qtekfun.ultimatephone.screening.SpamSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/** List repository in memory; matching follows the real one (exact number first, then the longest prefix). */
class InMemoryLists : SpamListsRepository {
    private val rows = MutableStateFlow<List<Pair<ListType, ListEntry>>>(emptyList())
    private var counter = 0

    override fun observe(list: ListType): Flow<List<ListEntry>> =
        rows.map { all -> all.filter { it.first == list && !it.second.deleted }.map { it.second }.sortedByDescending { it.updatedAt } }

    override suspend fun add(list: ListType, kind: EntryKind, value: String, label: String?, note: String?): ListEntry {
        require(EntryValues.isValid(kind, value))
        val existing = rows.value.firstOrNull { it.first == list && it.second.kind == kind && it.second.value == value }?.second
        val entry = ListEntry(existing?.id ?: "id-${counter++}", kind, value, label, note, counter.toLong(), "dev")
        rows.value = rows.value.filterNot { it.second.id == entry.id } + (list to entry)
        return entry
    }

    override suspend fun remove(list: ListType, id: String): Boolean {
        val row = rows.value.firstOrNull { it.first == list && it.second.id == id && !it.second.deleted } ?: return false
        rows.value = rows.value.map { if (it === row) list to it.second.copy(deleted = true) else it }
        return true
    }

    override suspend fun isWhitelisted(e164: String) = best(ListType.WHITELIST, e164) != null

    override suspend fun matchOwn(e164: String) = best(ListType.OWN, e164)

    private fun best(list: ListType, e164: String): ListEntry? {
        val keys = EntryValues.lookupKeys(e164).toSet()
        return EntryValues.best(e164, rows.value.filter { it.first == list && it.second.value in keys }.map { it.second })
    }

    override suspend fun entries(list: ListType) = rows.value.filter { it.first == list }.map { it.second }

    override suspend fun exportJsonLines(list: ListType) = ""

    override suspend fun importJsonLines(list: ListType, lines: Sequence<String>) = ImportResult(0, 0, 0, 0)
}

class FakeSpamSettings(initial: SpamPrefs = SpamPrefs()) : SpamSettingsRepository {
    private val state = MutableStateFlow(initial)
    override val prefs get() = state

    override suspend fun current() = state.value

    override suspend fun setLevelAction(level: SpamLevel, action: SpamAction) {
        state.value = state.value.copy(levelActions = state.value.levelActions + (level to action))
    }

    override suspend fun setRuleAction(ruleId: String, action: SpamAction?) {
        state.value = state.value.copy(ruleActions = if (action == null) state.value.ruleActions - ruleId else state.value.ruleActions + (ruleId to action))
    }

    override suspend fun setIdentifiedBusinessIsNotSpam(value: Boolean) {
        state.value = state.value.copy(identifiedBusinessIsNotSpam = value)
    }

    override suspend fun deviceId() = "test-device"
}

class FakeDecisionDao : CallDecisionDao {
    val rows = MutableStateFlow<List<CallDecisionEntity>>(emptyList())
    var cutoffs = mutableListOf<Long>()

    override suspend fun insert(row: CallDecisionEntity) {
        rows.value = rows.value + row.copy(id = rows.value.size + 1L)
    }

    override fun observeLatest(): Flow<List<CallDecisionEntity>> = rows.map { all -> all.groupBy { it.e164 }.map { (_, v) -> v.maxBy { it.id } } }

    override suspend fun latestFor(e164: String) = rows.value.filter { it.e164 == e164 }.maxByOrNull { it.id }

    override suspend fun deleteOlderThan(cutoff: Long) {
        cutoffs += cutoff
        rows.value = rows.value.filter { it.decidedAt >= cutoff }
    }
}

/** Installed packs with fixed answers. */
class FakePacks(
    override val sources: SourceLookup = SourceLookup { null },
    override val businesses: BusinessLookup = BusinessLookup { null },
    override val prefixRules: PrefixRules = PrefixRules { _, _ -> null },
    override val changes: Flow<Unit> = emptyFlow()
) : InstalledPackLookups

/** The rules shipped in the app, loaded from the real resource. */
fun builtInRuleSet() = PrefixRuleSet(BuiltInRules.load().rules)

fun commercialRuleSet(level: SpamLevel = SpamLevel.RULE) = PrefixRuleSet(listOf(PrefixRule("es-400-commercial", "ES", "400", level, informational = true)))
