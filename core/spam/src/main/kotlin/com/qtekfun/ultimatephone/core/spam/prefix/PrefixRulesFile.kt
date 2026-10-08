package com.qtekfun.ultimatephone.core.spam.prefix

import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** A versioned set of rules. [skipped] counts malformed rules that were ignored. */
data class PrefixRulesFile(val schema: Int, val version: String, val rules: List<PrefixRule>, val skipped: Int = 0)

class RulesFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * Parser for the rules file shipped in the app and by the data repo:
 * `{"schema":1,"version":"...","rules":[{"id","country","prefix","level","informational","source"}]}`.
 * Unknown fields are ignored. A malformed rule is skipped; an unreadable file or a newer schema throws.
 */
object PrefixRulesParser {
    const val SUPPORTED_SCHEMA = 1

    fun parse(text: String): PrefixRulesFile {
        val root = readRoot(text)
        val schema = (root["schema"] as? JsonPrimitive)?.intOrNull ?: invalid("Missing schema")
        if (schema < 1 || schema > SUPPORTED_SCHEMA) invalid("Unsupported schema $schema")
        val version = (root["version"] as? JsonPrimitive)?.contentOrNull ?: invalid("Missing version")
        val array = root["rules"] as? JsonArray ?: invalid("Missing rules")
        val rules = array.map { parseRule(it as? JsonObject) }
        return PrefixRulesFile(schema, version, rules.filterNotNull(), rules.count { it == null })
    }

    private fun readRoot(text: String): JsonObject {
        val element = try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw RulesFormatException("Rules file is not valid JSON", e)
        }
        return element as? JsonObject ?: invalid("Rules file must be a JSON object")
    }

    private fun invalid(message: String): Nothing = throw RulesFormatException(message)

    private fun parseRule(obj: JsonObject?): PrefixRule? {
        if (obj == null) return null
        val id = text(obj, "id")?.takeIf { it.isNotBlank() } ?: return null
        val prefix = text(obj, "prefix")?.takeIf { it.isNotBlank() } ?: return null
        val country = text(obj, "country")?.uppercase()
        val level = text(obj, "level")?.let { name -> SpamLevel.entries.firstOrNull { it.name.equals(name, true) } }
        if (level == null || (country == null && !prefix.startsWith("+"))) return null
        val informational = (obj["informational"] as? JsonPrimitive)?.booleanOrNull ?: false
        return PrefixRule(id, country, prefix, level, informational, text(obj, "source"))
    }

    private fun text(obj: JsonObject, key: String): String? = (obj[key] as? JsonPrimitive)?.contentOrNull
}

/** The rules compiled into the app, loaded from the `spam/builtin-prefix-rules.json` resource. */
object BuiltInRules {
    const val RESOURCE = "spam/builtin-prefix-rules.json"

    fun load(): PrefixRulesFile {
        val stream = BuiltInRules::class.java.classLoader?.getResourceAsStream(RESOURCE)
            ?: throw RulesFormatException("Missing resource $RESOURCE")
        return stream.use { PrefixRulesParser.parse(it.readBytes().decodeToString()) }
    }
}
