package com.qtekfun.ultimatephone.core.recording

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A recording's file name read back: the date in the user's zone, the direction and the label (never a full number). */
data class ParsedName(val dateTime: LocalDateTime, val incoming: Boolean, val label: String)

/**
 * File names look like `call_20261008-143210_in_xxx123.m4a`: the date, the direction and either the last three digits of
 * the number or, when the user allows it, the contact's name. A full phone number is never part of a name.
 */
object RecordingNames {
    private const val PREFIX = "call_"
    private const val MAX_LABEL = 40
    private const val MAX_NAME = 80
    private const val VISIBLE_DIGITS = 3
    private const val UNKNOWN = "unknown"
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val pattern = Regex("""^call_(\d{8}-\d{6})_(in|out)_(.+?)(?: \(\d+\))?\.(?:m4a|ogg)$""")
    private val forbidden = Regex("""[/\\:*?"<>|]""")

    /** "xxx123", or "unknown" when there are no digits. */
    fun maskedId(number: String?): String {
        val digits = number?.filter { it.isDigit() }.orEmpty()
        return if (digits.isEmpty()) UNKNOWN else "xxx" + digits.takeLast(VISIBLE_DIGITS)
    }

    /** Keeps letters, digits, '-' and '_'; other runs become one '-'. Never longer than [MAX_LABEL]. */
    fun sanitize(name: String): String {
        val cleaned = buildString {
            name.trim().forEach { c ->
                when {
                    c.isLetterOrDigit() || c == '_' -> append(c)
                    isNotEmpty() && !endsWith('-') -> append('-')
                    else -> Unit
                }
            }
        }
        return cleaned.take(MAX_LABEL).trim('-')
    }

    @Suppress("LongParameterList") // Every value is a separate fact about the call and the user's settings.
    fun build(
        startMillis: Long,
        incoming: Boolean,
        number: String?,
        contactName: String?,
        includeContactName: Boolean,
        format: RecordingFormat,
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val label = (if (includeContactName && contactName != null) sanitize(contactName) else "").ifEmpty { maskedId(number) }
        val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), zone).format(stamp)
        return "$PREFIX${time}_${if (incoming) "in" else "out"}_$label.${format.extension}"
    }

    /** Null for a name the user changed to something else. */
    fun parse(fileName: String): ParsedName? {
        val match = pattern.matchEntire(fileName) ?: return null
        val (time, direction, label) = match.destructured
        val dateTime = runCatching { LocalDateTime.parse(time, stamp) }.getOrNull() ?: return null
        return ParsedName(dateTime, direction == "in", label)
    }

    /** The extension (with the dot) of [fileName], or "". */
    fun extensionOf(fileName: String): String = fileName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }

    /** A name typed by the user, made safe and given back the original extension. Null when nothing usable is left. */
    fun renamed(typed: String, original: String): String? {
        val ext = extensionOf(original)
        val base = typed.trim().removeSuffix(ext).replace(forbidden, "").trim().take(MAX_NAME)
        return if (base.isEmpty()) null else base + ext
    }
}
