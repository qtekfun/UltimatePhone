package com.qtekfun.ultimatephone.backup

import androidx.annotation.StringRes
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.ThemeMode
import com.qtekfun.ultimatephone.core.settings.ExportSection
import com.qtekfun.ultimatephone.core.spam.decision.SpamAction
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.lists.EntryJsonl
import com.qtekfun.ultimatephone.core.spam.lists.ListType
import com.qtekfun.ultimatephone.core.spam.lists.SpamListsRepository
import com.qtekfun.ultimatephone.core.sync.NextcloudEndpoint
import com.qtekfun.ultimatephone.screening.SpamSettingsRepository
import com.qtekfun.ultimatephone.settings.SettingsRepository
import com.qtekfun.ultimatephone.sync.SyncRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * A backup section the screens can show: the id and contents of [ExportSection] plus the title to display.
 * Features register theirs with Hilt (`@Provides @IntoSet`) and appear in both the export and the import choice.
 */
interface BackupSection : ExportSection {
    @get:StringRes
    val title: Int
}

object SectionIds {
    const val APP = "app_settings"
    const val SPAM_SETTINGS = "spam_settings"
    const val SPAM_LIST = "spam_list"
    const val WHITELIST = "whitelist"
    const val NEXTCLOUD = "nextcloud"
}

private fun parseObject(data: String): JsonObject = Json.parseToJsonElement(data) as? JsonObject ?: error("Not an object")

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.flag(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

/** Theme and region. SIM choices are not exported: SIM keys belong to one phone's SIM cards. */
class AppSettingsSection(private val settings: SettingsRepository) : BackupSection {
    override val id = SectionIds.APP
    override val title = R.string.backup_section_app

    override suspend fun export(): String {
        val s = settings.settings.value
        return buildJsonObject {
            put("themeMode", s.themeMode.name)
            put("regionOverride", s.regionOverride)
        }.toString()
    }

    private class Parsed(val theme: ThemeMode, val region: String?)

    private fun parse(data: String): Parsed {
        val obj = parseObject(data)
        val theme = ThemeMode.entries.first { it.name == obj.text("themeMode") }
        val region = obj.text("regionOverride")
        require(region == null || Regex("[A-Za-z]{2}").matches(region)) { "Bad region" }
        return Parsed(theme, region)
    }

    override fun validate(data: String) {
        parse(data)
    }

    override suspend fun restore(data: String) {
        val parsed = parse(data)
        settings.setThemeMode(parsed.theme)
        settings.setRegionOverride(parsed.region)
    }
}

/** What to do per level and per rule, and the business switch. Never the device id: it must stay unique per phone. */
class SpamSettingsSection(private val settings: SpamSettingsRepository) : BackupSection {
    override val id = SectionIds.SPAM_SETTINGS
    override val title = R.string.backup_section_spam_settings

    override suspend fun export(): String {
        val prefs = settings.current()
        return buildJsonObject {
            put("levels", buildJsonObject { SpamLevel.entries.forEach { put(it.name, prefs.actionOf(it).name) } })
            put("rules", buildJsonObject { prefs.ruleActions.toSortedMap().forEach { (rule, action) -> put(rule, action.name) } })
            put("identifiedBusinessIsNotSpam", prefs.identifiedBusinessIsNotSpam)
        }.toString()
    }

    private class Parsed(val levels: Map<SpamLevel, SpamAction>, val rules: Map<String, SpamAction>, val business: Boolean)

    private fun parse(data: String): Parsed {
        val obj = parseObject(data)
        val levels = (obj["levels"] as JsonObject).entries.associate { (name, value) ->
            SpamLevel.valueOf(name) to SpamAction.valueOf((value as JsonPrimitive).content)
        }
        val rules = (obj["rules"] as JsonObject).entries.associate { (rule, value) -> rule to SpamAction.valueOf((value as JsonPrimitive).content) }
        return Parsed(levels, rules, obj.flag("identifiedBusinessIsNotSpam") ?: error("Missing switch"))
    }

    override fun validate(data: String) {
        parse(data)
    }

    override suspend fun restore(data: String) {
        val parsed = parse(data)
        val before = settings.current()
        parsed.levels.forEach { (level, action) -> settings.setLevelAction(level, action) }
        before.ruleActions.keys.filter { it !in parsed.rules }.forEach { settings.setRuleAction(it, null) }
        parsed.rules.forEach { (rule, action) -> settings.setRuleAction(rule, action) }
        settings.setIdentifiedBusinessIsNotSpam(parsed.business)
    }
}

/**
 * One of the user's lists, as the same JSON Lines that sync uses (tombstones included). Restoring merges: entries are
 * added or updated, the newest change to each wins, and nothing already on this phone is removed.
 */
class ListSection(private val type: ListType, private val lists: SpamListsRepository) : BackupSection {
    override val id = if (type == ListType.OWN) SectionIds.SPAM_LIST else SectionIds.WHITELIST
    override val title = if (type == ListType.OWN) R.string.backup_section_spam_list else R.string.backup_section_whitelist

    override suspend fun export(): String = lists.exportJsonLines(type)

    override fun validate(data: String) {
        require(EntryJsonl.decodeAll(data).badLines == 0) { "Unreadable line" }
    }

    override suspend fun restore(data: String) {
        lists.importJsonLines(type, data.lineSequence())
    }
}

/**
 * The Nextcloud account, with the app password in clear text INSIDE the encrypted backup (it is what lets a new phone
 * start syncing). The data only ever exists in memory and inside the encrypted file.
 */
class NextcloudSection(private val sync: SyncRepository) : BackupSection {
    override val id = SectionIds.NEXTCLOUD
    override val title = R.string.backup_section_nextcloud

    override suspend fun export(): String {
        val credentials = sync.credentials()
        val enabled = sync.isEnabled()
        return buildJsonObject {
            put("configured", credentials != null)
            if (credentials != null) {
                put("serverUrl", credentials.serverUrl)
                put("username", credentials.username)
                put("password", credentials.password)
                put("enabled", enabled)
            }
        }.toString()
    }

    private class Parsed(val server: String, val user: String, val password: String, val enabled: Boolean)

    private fun parse(data: String): Parsed? {
        val obj = parseObject(data)
        if (obj.flag("configured") != true) return null
        val server = obj.text("serverUrl") ?: error("Missing server")
        val user = obj.text("username") ?: error("Missing user")
        val password = obj.text("password")?.takeIf { it.isNotEmpty() } ?: error("Missing password")
        NextcloudEndpoint.davRoot(server, user) // https only, usable address.
        return Parsed(server, user, password, obj.flag("enabled") ?: false)
    }

    override fun validate(data: String) {
        parse(data)
    }

    override suspend fun restore(data: String) {
        val parsed = parse(data) ?: return
        val saved = sync.saveAccount(parsed.server, parsed.user, parsed.password)
        check(saved.isSuccess) { "Account not saved" }
        sync.setEnabled(parsed.enabled)
    }
}
