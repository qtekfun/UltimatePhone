package com.qtekfun.ultimatephone.core.spam.lists

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Entries read from JSON Lines plus the number of lines that could not be used. */
data class JsonlResult(val entries: List<ListEntry>, val badLines: Int)

/**
 * JSON Lines form of list entries, `{id, kind: number|prefix, value, label, note, updatedAt, deviceId, deleted}`.
 * Reading ignores unknown fields and skips bad lines instead of failing.
 */
object EntryJsonl {
    fun encode(entry: ListEntry): String = buildJsonObject {
        put("id", entry.id)
        put("kind", entry.kind.name.lowercase())
        put("value", entry.value)
        put("label", entry.label)
        put("note", entry.note)
        put("updatedAt", entry.updatedAt)
        put("deviceId", entry.deviceId)
        put("deleted", entry.deleted)
    }.toString()

    fun encodeAll(entries: Iterable<ListEntry>): String = entries.joinToString(separator = "") { encode(it) + "\n" }

    /** Null when the line is not a usable entry. Blank lines are not entries either. */
    fun decode(line: String): ListEntry? {
        val obj = try {
            Json.parseToJsonElement(line.trim().removePrefix("")) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return null
        val id = text(obj, "id")?.takeIf { it.isNotBlank() } ?: return null
        val kind = text(obj, "kind")?.let { k -> EntryKind.entries.firstOrNull { it.name.equals(k, ignoreCase = true) } }
        val value = text(obj, "value")?.trim()
        val updatedAt = (obj["updatedAt"] as? JsonPrimitive)?.longOrNull
        if (kind == null || value == null || updatedAt == null || !EntryValues.isValid(kind, value)) return null
        return ListEntry(
            id = id,
            kind = kind,
            value = value,
            label = text(obj, "label"),
            note = text(obj, "note"),
            updatedAt = updatedAt,
            deviceId = text(obj, "deviceId").orEmpty(),
            deleted = (obj["deleted"] as? JsonPrimitive)?.booleanOrNull ?: false
        )
    }

    fun decodeAll(lines: Sequence<String>): JsonlResult {
        val entries = ArrayList<ListEntry>()
        var bad = 0
        for (line in lines) {
            if (line.isBlank()) continue
            val entry = decode(line)
            if (entry == null) bad++ else entries += entry
        }
        return JsonlResult(entries, bad)
    }

    fun decodeAll(text: String): JsonlResult = decodeAll(text.lineSequence())

    private fun text(obj: JsonObject, key: String): String? {
        val element = obj[key]
        return if (element is JsonNull) null else (element as? JsonPrimitive)?.contentOrNull
    }
}
