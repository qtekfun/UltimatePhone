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
import com.qtekfun.ultimatephone.core.spam.sources.SourceFormat
import com.qtekfun.ultimatephone.core.sync.NextcloudEndpoint
import com.qtekfun.ultimatephone.data.CustomSource
import com.qtekfun.ultimatephone.data.DataSettingsRepository
import com.qtekfun.ultimatephone.data.SourceUrls
import com.qtekfun.ultimatephone.screening.SpamSettingsRepository
import com.qtekfun.ultimatephone.settings.SettingsRepository
import com.qtekfun.ultimatephone.sync.SyncRepository
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
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
    const val DATA_SETTINGS = "data_settings"
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

/** Starts a download of the given custom sources without waiting for it (a restore must not depend on the network). */
fun interface SourceRefresher {
    fun refresh(sourceIds: List<String>)
}

/**
 * The data feature's choices: regions, removed packs, Wi-Fi only, automatic updates and the custom sources the user added
 * (name, address, format, level, enabled). Per-device state never travels: update times, errors, ETags, hashes, entry
 * counts and the set-up-completed flag. Restoring replaces the choices and merges the sources by address; sources that are
 * new to this phone are downloaded straight away, as when one is added by hand.
 */
class DataSettingsSection(
    private val repository: DataSettingsRepository,
    private val refresher: SourceRefresher,
    private val newId: () -> String = { UUID.randomUUID().toString().take(SOURCE_ID_LENGTH) }
) : BackupSection {
    override val id = SectionIds.DATA_SETTINGS
    override val title = R.string.backup_section_data_settings

    override suspend fun export(): String {
        val s = repository.current()
        return buildJsonObject {
            put("selectedRegions", buildJsonArray { s.selectedRegions.map { it.uppercase() }.sorted().forEach { add(JsonPrimitive(it)) } })
            put("excludedPacks", buildJsonArray { s.excludedPacks.sorted().forEach { add(JsonPrimitive(it)) } })
            put("wifiOnly", s.wifiOnly)
            put("autoUpdate", s.autoUpdate)
            put(
                "customSources",
                buildJsonArray {
                    s.customSources.forEach { source ->
                        add(
                            buildJsonObject {
                                put("name", source.name)
                                put("url", source.url)
                                put("format", source.format.name)
                                put("level", source.level.name)
                                put("enabled", source.enabled)
                            }
                        )
                    }
                }
            )
        }.toString()
    }

    private class ParsedSource(val name: String, val url: String, val format: SourceFormat, val level: SpamLevel, val enabled: Boolean)

    private class Parsed(val regions: Set<String>, val excluded: Set<String>, val wifiOnly: Boolean, val autoUpdate: Boolean, val sources: List<ParsedSource>)

    private fun strings(obj: JsonObject, key: String): List<String> = (obj[key] as? JsonArray ?: error("Missing $key")).map {
        (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: error("Bad $key")
    }

    private fun parse(data: String): Parsed {
        val obj = parseObject(data)
        val regions = strings(obj, "selectedRegions").onEach { require(Regex("[A-Za-z]{2}").matches(it)) { "Bad region" } }
        val excluded = strings(obj, "excludedPacks").onEach { require(it.isNotBlank()) { "Bad pack" } }
        val sources = (obj["customSources"] as? JsonArray ?: error("Missing sources")).map { element ->
            val o = element as? JsonObject ?: error("Bad source")
            val url = o.text("url")?.trim() ?: error("Missing url")
            require(SourceUrls.isValid(url)) { "Source address must be https" }
            ParsedSource(
                name = o.text("name").orEmpty(),
                url = url,
                format = SourceFormat.valueOf(o.text("format") ?: error("Missing format")),
                level = SpamLevel.valueOf(o.text("level") ?: error("Missing level")),
                enabled = o.flag("enabled") ?: error("Missing enabled")
            )
        }
        return Parsed(
            regions.map { it.uppercase() }.toSet(),
            excluded.toSet(),
            obj.flag("wifiOnly") ?: error("Missing switch"),
            obj.flag("autoUpdate") ?: error("Missing switch"),
            sources
        )
    }

    override fun validate(data: String) {
        parse(data)
    }

    override suspend fun restore(data: String) {
        val parsed = parse(data)
        val added = ArrayList<String>()
        repository.update { current ->
            added.clear()
            current.copy(
                selectedRegions = parsed.regions,
                excludedPacks = parsed.excluded,
                wifiOnly = parsed.wifiOnly,
                autoUpdate = parsed.autoUpdate,
                customSources = mergeSources(current.customSources, parsed.sources, added)
            )
        }
        if (added.isNotEmpty()) refresher.refresh(added.toList())
    }

    /** Same address: take the user's choices and keep the local bookkeeping. New address: a new source with a fresh id. */
    private fun mergeSources(current: List<CustomSource>, incoming: List<ParsedSource>, added: MutableList<String>): List<CustomSource> {
        val result = current.toMutableList()
        for (source in incoming) {
            val index = result.indexOfFirst { it.url.equals(source.url, ignoreCase = true) }
            if (index >= 0) {
                val old = result[index]
                result[index] = old.copy(name = source.name.ifBlank { old.name }, format = source.format, level = source.level, enabled = source.enabled)
            } else {
                val created = CustomSource(
                    id = newId(),
                    name = source.name.trim().ifEmpty { source.url.substringAfter("://").substringBefore('/') },
                    url = source.url,
                    format = source.format,
                    level = source.level,
                    enabled = source.enabled
                )
                result += created
                added += created.id
            }
        }
        return result
    }

    private companion object {
        const val SOURCE_ID_LENGTH = 8
    }
}
