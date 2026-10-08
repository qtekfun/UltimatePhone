package com.qtekfun.ultimatephone.core.contacts

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Base64

/**
 * Tolerant vCard reader for versions 2.1, 3.0 and 4.0. Pure.
 *
 * Handles line folding (and QUOTED-PRINTABLE soft breaks), property groups (`item1.TEL` with `X-ABLabel`), multiple
 * TEL/EMAIL with TYPE (also the bare 2.1 form), CHARSET on quoted-printable values, base64 PHOTO (`ENCODING=b` and the
 * 4.0 `data:` URI; a photo that is a web address is ignored, nothing is ever downloaded), BDAY in the usual forms.
 * Properties it does not know (X- fields, PRODID, REV ...) are ignored. A card that is broken (never closed, or with
 * no name, phone, e-mail or organisation) is skipped and counted, and the cards around it still load.
 *
 * Files are decoded as UTF-8, or as windows-1252 when they are not valid UTF-8.
 */
object VCardParser {
    private val LINE_BREAK = Regex("\r\n|\n|\r")
    private val FALLBACK_CHARSET: Charset = Charset.forName("windows-1252")
    private val GROUP_PREFIX = Regex("""^(.*)\.([^.]+)$""")

    private class Prop(val group: String?, val name: String, val types: Set<String>, val params: Map<String, String>, val value: String)

    fun parse(bytes: ByteArray): VCardParseResult = parse(decodeFile(bytes))

    fun parse(text: String): VCardParseResult {
        val contacts = ArrayList<VCardContact>()
        var skipped = 0
        var card: ArrayList<String>? = null
        for (line in logicalLines(text)) {
            val upper = line.trim().uppercase()
            when {
                upper == "BEGIN:VCARD" -> {
                    if (card != null) skipped++
                    card = ArrayList()
                }
                upper == "END:VCARD" -> {
                    val lines = card
                    card = null
                    if (lines != null) {
                        val parsed = buildCard(lines)
                        if (parsed != null) contacts += parsed else skipped++
                    }
                }
                else -> card?.add(line)
            }
        }
        if (card != null) skipped++
        return VCardParseResult(contacts, skipped)
    }

    private fun decodeFile(bytes: ByteArray): String {
        var start = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) start = 3
        val body = ByteBuffer.wrap(bytes, start, bytes.size - start)
        return strictDecode(body, Charsets.UTF_8) ?: FALLBACK_CHARSET.decode(body.duplicate()).toString()
    }

    private fun strictDecode(buffer: ByteBuffer, charset: Charset): String? = try {
        charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(buffer.duplicate()).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /** Physical lines joined: continuation lines (leading blank) and quoted-printable soft breaks (trailing `=`). */
    private fun logicalLines(text: String): List<String> {
        val out = ArrayList<String>()
        var current: StringBuilder? = null
        for (raw in text.split(LINE_BREAK)) {
            val open = current
            if (open != null && endsWithSoftBreak(open)) {
                open.setLength(open.length - 1)
                open.append(raw)
            } else if (open != null && raw.isNotEmpty() && (raw[0] == ' ' || raw[0] == '\t')) {
                open.append(raw, 1, raw.length)
            } else {
                if (open != null) out += open.toString()
                current = StringBuilder(raw)
            }
        }
        current?.let { out += it.toString() }
        return out
    }

    private fun endsWithSoftBreak(line: StringBuilder): Boolean {
        if (line.isEmpty() || line[line.length - 1] != '=') return false
        val colon = line.indexOf(":")
        val header = if (colon < 0) line else line.subSequence(0, colon)
        return header.contains("QUOTED-PRINTABLE", ignoreCase = true)
    }

    // ---- one card ----

    private fun buildCard(lines: List<String>): VCardContact? {
        val props = lines.mapNotNull(::parseLine)
        val labels = HashMap<String, String>()
        for (p in props) if (p.group != null && p.name == "X-ABLABEL") labels[p.group.lowercase()] = unescape(p.value)
        val card = CardAccumulator()
        for (p in props) card.add(p, p.group?.let { labels[it.lowercase()] })
        return card.build().takeIf { it.isUsable }
    }

    private class CardAccumulator {
        private var name = VCardName()
        private var formatted = ""
        private val nicknames = ArrayList<String>()
        private var organization = ""
        private var title = ""
        private val phones = ArrayList<LabeledValue>()
        private val emails = ArrayList<LabeledValue>()
        private var birthday: ContactDate? = null
        private val notes = ArrayList<String>()
        private var photo: VCardPhoto? = null
        private val websites = ArrayList<String>()
        private val addresses = ArrayList<VCardAddress>()

        fun add(p: Prop, label: String?) {
            when (p.name) {
                "N" -> name = parseName(p.value)
                "FN" -> formatted = unescape(p.value).trim()
                "NICKNAME" -> splitEscaped(p.value, ',').map { unescape(it).trim() }.filterTo(nicknames) { it.isNotEmpty() }
                "ORG" -> if (organization.isEmpty()) organization = parseOrganization(p.value)
                "TITLE" -> if (title.isEmpty()) title = unescape(p.value).trim()
                "TEL" -> telValue(p.value)?.let { phones += labeled(it, phoneType(p.types), label, email = false) }
                "EMAIL" -> unescape(p.value).trim().takeIf { it.isNotEmpty() }?.let { emails += labeled(it, emailType(p.types), label, email = true) }
                "BDAY" -> if (birthday == null) birthday = parseBirthday(unescape(p.value))
                "NOTE" -> unescape(p.value).trim().takeIf { it.isNotEmpty() }?.let(notes::add)
                "PHOTO" -> if (photo == null) photo = parsePhoto(p)
                "URL" -> unescape(p.value).trim().takeIf { it.isNotEmpty() }?.let(websites::add)
                "ADR" -> parseAddress(p)?.let(addresses::add)
            }
        }

        fun build() = VCardContact(
            name = name.copy(formatted = formatted),
            nicknames = nicknames.distinct(),
            organization = organization,
            title = title,
            phones = phones,
            emails = emails,
            birthday = birthday,
            note = notes.joinToString("\n"),
            photo = photo,
            websites = websites,
            addresses = addresses
        )
    }

    private fun parseOrganization(value: String) = splitEscaped(value, ';').map { unescape(it).trim() }.filter { it.isNotEmpty() }.joinToString(", ")

    private fun parseName(value: String): VCardName {
        val parts = splitEscaped(value, ';').map { unescape(it).trim() }
        fun part(i: Int) = parts.getOrElse(i) { "" }
        return VCardName(family = part(0), given = part(1), middle = part(2), prefix = part(3), suffix = part(4))
    }

    private fun telValue(raw: String): String? {
        val text = unescape(raw).trim().removePrefix("tel:").removePrefix("TEL:").trim()
        return text.takeIf { it.isNotEmpty() }
    }

    private fun parseAddress(p: Prop): VCardAddress? {
        val text = splitEscaped(p.value, ';').map { unescape(it).trim() }.filter { it.isNotEmpty() }.joinToString(", ")
        if (text.isEmpty()) return null
        val type = when {
            "HOME" in p.types -> VCardAddress.ADDRESS_HOME
            "WORK" in p.types -> VCardAddress.ADDRESS_WORK
            else -> VCardAddress.ADDRESS_OTHER
        }
        return VCardAddress(text, type)
    }

    private fun labeled(value: String, type: Int, label: String?, email: Boolean): LabeledValue {
        if (label.isNullOrBlank()) return LabeledValue(value, type)
        val known = knownLabel(label, email)
        return if (known != null) LabeledValue(value, known) else LabeledValue(value, LabelTypes.CUSTOM, label)
    }

    // Apple writes standard labels as "_$!<Mobile>!$_"; those are the platform's own types, not custom ones.
    private fun knownLabel(label: String, email: Boolean): Int? = when (label.removePrefix("_\$!<").removeSuffix(">!\$_").lowercase()) {
        "mobile", "iphone" -> if (email) LabelTypes.EMAIL_MOBILE else LabelTypes.MOBILE
        "home" -> if (email) LabelTypes.EMAIL_HOME else LabelTypes.HOME
        "work" -> if (email) LabelTypes.EMAIL_WORK else LabelTypes.WORK
        "main" -> if (email) null else LabelTypes.MAIN
        "other" -> if (email) LabelTypes.EMAIL_OTHER else LabelTypes.OTHER
        else -> null
    }

    private fun phoneType(types: Set<String>) = when {
        "CELL" in types -> LabelTypes.MOBILE
        "WORK" in types -> LabelTypes.WORK
        "HOME" in types -> LabelTypes.HOME
        "MAIN" in types -> LabelTypes.MAIN
        else -> LabelTypes.OTHER
    }

    private fun emailType(types: Set<String>) = when {
        "CELL" in types || "MOBILE" in types -> LabelTypes.EMAIL_MOBILE
        "WORK" in types -> LabelTypes.EMAIL_WORK
        "HOME" in types -> LabelTypes.EMAIL_HOME
        else -> LabelTypes.EMAIL_OTHER
    }

    // ---- BDAY / PHOTO ----

    private val COMPACT_DATE = Regex("""(\d{4})(\d{2})(\d{2})""")
    private val NO_YEAR_COMPACT = Regex("""--(\d{2})(\d{2})""")
    private const val PLACEHOLDER_YEAR = 1604

    private fun parseBirthday(raw: String): ContactDate? {
        var text = raw.trim().substringBefore('T')
        COMPACT_DATE.matchEntire(text)?.let { text = "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }
        NO_YEAR_COMPACT.matchEntire(text)?.let { text = "--${it.groupValues[1]}-${it.groupValues[2]}" }
        val date = ContactDate.parse(text) ?: return null
        // Some programs write a made-up year for "no year".
        return if (date.year == PLACEHOLDER_YEAR || date.year == 0) date.copy(year = null) else date
    }

    private fun parsePhoto(p: Prop): VCardPhoto? {
        var data = p.value.trim()
        if (data.startsWith("data:", ignoreCase = true)) {
            val comma = data.indexOf(',')
            if (comma < 0 || !data.substring(0, comma).contains("base64", ignoreCase = true)) return null
            data = data.substring(comma + 1)
        } else if (data.contains("://")) {
            return null
        }
        return try {
            val bytes = Base64.getMimeDecoder().decode(data.filterNot { it.isWhitespace() })
            if (bytes.isEmpty()) null else VCardPhoto(bytes)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private val BASE64_NAMES = setOf("B", "BASE64")

    // ---- property line ----

    private fun parseLine(line: String): Prop? {
        val colon = indexOfUnquoted(line, ':')
        if (colon <= 0) return null
        val header = splitUnquoted(line.substring(0, colon), ';')
        var nameToken = header[0].trim()
        var group: String? = null
        GROUP_PREFIX.matchEntire(nameToken)?.let {
            group = it.groupValues[1]
            nameToken = it.groupValues[2]
        }
        val types = LinkedHashSet<String>()
        val params = HashMap<String, String>()
        for (token in header.drop(1)) {
            val eq = token.indexOf('=')
            if (eq < 0) {
                val bare = token.trim().uppercase()
                if (bare == "QUOTED-PRINTABLE" || bare in BASE64_NAMES) {
                    params["ENCODING"] = bare
                } else if (bare.isNotEmpty()) {
                    types += bare
                }
            } else {
                val key = token.substring(0, eq).trim().uppercase()
                val value = token.substring(eq + 1).trim().trim('"')
                if (key == "TYPE") value.split(',').forEach { types += it.trim().trim('"').uppercase() } else params[key] = value
            }
        }
        var value = line.substring(colon + 1)
        if (params["ENCODING"].equals("QUOTED-PRINTABLE", ignoreCase = true)) value = decodeQuotedPrintable(value, params["CHARSET"])
        return Prop(group, nameToken.uppercase(), types, params, value)
    }

    private fun indexOfUnquoted(text: String, target: Char): Int {
        var quoted = false
        for (i in text.indices) {
            val c = text[i]
            if (c == '"') {
                quoted = !quoted
            } else if (c == target && !quoted) {
                return i
            }
        }
        return -1
    }

    private fun splitUnquoted(text: String, separator: Char): List<String> {
        val out = ArrayList<String>()
        var quoted = false
        var start = 0
        for (i in text.indices) {
            val c = text[i]
            if (c == '"') {
                quoted = !quoted
            } else if (c == separator && !quoted) {
                out += text.substring(start, i)
                start = i + 1
            }
        }
        out += text.substring(start)
        return out
    }

    // ---- values ----

    /** Splits on [separator] where it is not escaped with a backslash; the parts keep their escapes. */
    private fun splitEscaped(text: String, separator: Char): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> {
                    current.append(c).append(text[i + 1])
                    i++
                }
                c == separator -> {
                    out += current.toString()
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        out += current.toString()
        return out
    }

    private fun unescape(text: String): String {
        if ('\\' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                val next = text[i + 1]
                out.append(if (next == 'n' || next == 'N') '\n' else next)
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    /** Decodes `=C3=A9` style escapes into bytes, then the bytes with [charsetName] (UTF-8, then windows-1252, when unknown or invalid). */
    private fun decodeQuotedPrintable(value: String, charsetName: String?): String {
        val bytes = ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            val hi = if (c == '=' && i + 2 < value.length) Character.digit(value[i + 1], 16) else -1
            val lo = if (hi >= 0) Character.digit(value[i + 2], 16) else -1
            if (hi >= 0 && lo >= 0) {
                bytes.write(hi * 16 + lo)
                i += 3
            } else {
                // Not an escape (or a truncated one): keep the character as it is.
                val encoded = c.toString().toByteArray(Charsets.UTF_8)
                bytes.write(encoded, 0, encoded.size)
                i++
            }
        }
        val raw = ByteBuffer.wrap(bytes.toByteArray())
        val declared = charsetName?.let { runCatching { Charset.forName(it.trim()) }.getOrNull() }
        return (declared?.let { strictDecode(raw, it) }) ?: strictDecode(raw, Charsets.UTF_8) ?: FALLBACK_CHARSET.decode(raw.duplicate()).toString()
    }
}
