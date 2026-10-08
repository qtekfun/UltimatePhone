package com.qtekfun.ultimatephone.screening

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qtekfun.ultimatephone.core.spam.decision.ActionPolicy
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.decision.SpamSettings
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Id of the informational Spanish commercial range rule; it has its own control in Settings. */
const val RULE_ES_400_COMMERCIAL = "es-400-commercial"

/**
 * What the user chose in Settings > Spam.
 *
 * @property levelActions action per level; a missing level means [ActionPolicy.DEFAULT_ACTION].
 * @property ruleActions action per rule id, overriding the level action (for example the 400 range).
 * @property identifiedBusinessIsNotSpam an identified business is shown as such even when a source lists it.
 */
data class SpamPrefs(
    val levelActions: Map<SpamLevel, SpamAction> = emptyMap(),
    val ruleActions: Map<String, SpamAction> = emptyMap(),
    val identifiedBusinessIsNotSpam: Boolean = true
) {
    fun actionOf(level: SpamLevel): SpamAction = levelActions[level] ?: ActionPolicy.DEFAULT_ACTION

    fun actionOfRule(ruleId: String): SpamAction = ruleActions[ruleId] ?: ActionPolicy.DEFAULT_ACTION

    /** The engine's view of these preferences. */
    fun toSpamSettings(): SpamSettings = SpamSettings(
        identifiedBusinessIsNotSpam = identifiedBusinessIsNotSpam,
        policy = ActionPolicy(byLevel = levelActions, byRuleId = ruleActions)
    )
}

/** Reads and writes [SpamPrefs]. The screening path uses [current], which waits for the stored value. */
interface SpamSettingsRepository {
    val prefs: StateFlow<SpamPrefs>

    /** The stored preferences, never the placeholder a [StateFlow] has before its first read. */
    suspend fun current(): SpamPrefs

    suspend fun setLevelAction(level: SpamLevel, action: SpamAction)

    /** Sets or, with null, clears the override of one rule. */
    suspend fun setRuleAction(ruleId: String, action: SpamAction?)

    suspend fun setIdentifiedBusinessIsNotSpam(value: Boolean)

    /** Random id of this installation, stable across restarts, used as `deviceId` in list entries. */
    suspend fun deviceId(): String
}

/** Text form of the per-rule overrides: one `ruleId=ACTION` per line. Unknown actions and malformed lines are dropped. */
object RuleActionsCodec {
    fun encode(map: Map<String, SpamAction>): String = map.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value.name}" }

    fun decode(text: String?): Map<String, SpamAction> {
        if (text.isNullOrBlank()) return emptyMap()
        return text.lineSequence().mapNotNull(::parseLine).toMap()
    }

    private fun parseLine(line: String): Pair<String, SpamAction>? {
        val index = line.lastIndexOf('=')
        val action = if (index > 0) parseAction(line.substring(index + 1)) else null
        return action?.let { line.substring(0, index) to it }
    }
}

internal fun parseAction(name: String?): SpamAction? = SpamAction.entries.firstOrNull { it.name == name }

private val Context.spamStore: DataStore<Preferences> by preferencesDataStore(name = "spam_settings")

class DataStoreSpamSettingsRepository(context: Context, scope: CoroutineScope) : SpamSettingsRepository {
    private val store = context.applicationContext.spamStore

    private val stored: Flow<SpamPrefs> = store.data.map { prefs ->
        SpamPrefs(
            levelActions = SpamLevel.entries.mapNotNull { level -> parseAction(prefs[levelKey(level)])?.let { level to it } }.toMap(),
            ruleActions = RuleActionsCodec.decode(prefs[RULE_ACTIONS]),
            identifiedBusinessIsNotSpam = prefs[BUSINESS_NOT_SPAM] ?: true
        )
    }

    override val prefs: StateFlow<SpamPrefs> = stored.stateIn(scope, SharingStarted.Eagerly, SpamPrefs())

    override suspend fun current(): SpamPrefs = stored.first()

    override suspend fun setLevelAction(level: SpamLevel, action: SpamAction) {
        store.edit { it[levelKey(level)] = action.name }
    }

    override suspend fun setRuleAction(ruleId: String, action: SpamAction?) {
        store.edit { prefs ->
            val rules = RuleActionsCodec.decode(prefs[RULE_ACTIONS]).toMutableMap()
            if (action == null) rules.remove(ruleId) else rules[ruleId] = action
            prefs[RULE_ACTIONS] = RuleActionsCodec.encode(rules)
        }
    }

    override suspend fun setIdentifiedBusinessIsNotSpam(value: Boolean) {
        store.edit { it[BUSINESS_NOT_SPAM] = value }
    }

    override suspend fun deviceId(): String {
        store.data.first()[DEVICE_ID]?.let { return it }
        var id = ""
        store.edit { prefs ->
            id = prefs[DEVICE_ID] ?: UUID.randomUUID().toString().also { prefs[DEVICE_ID] = it }
        }
        return id
    }

    private companion object {
        val RULE_ACTIONS = stringPreferencesKey("rule_actions")
        val BUSINESS_NOT_SPAM = booleanPreferencesKey("identified_business_is_not_spam")
        val DEVICE_ID = stringPreferencesKey("device_id")

        fun levelKey(level: SpamLevel) = stringPreferencesKey("level_action_${level.name.lowercase()}")
    }
}
