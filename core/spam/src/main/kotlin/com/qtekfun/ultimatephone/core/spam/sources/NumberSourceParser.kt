package com.qtekfun.ultimatephone.core.spam.sources

import com.qtekfun.ultimatephone.core.phonenumber.NormalizedNumber
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import java.io.Reader
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A normalised number from a source file, with an optional label. */
data class ParsedNumber(val e164: String, val label: String?)

/**
 * Counters for a parse run. [invalid] lines had no usable number; [duplicates] repeated an earlier one.
 * Blank lines, comments and a CSV header are not counted anywhere.
 */
data class ParseStats(val accepted: Int, val invalid: Int, val duplicates: Int)

/** CSV settings. Columns are zero based. */
data class CsvOptions(
    val column: Int = 0,
    val labelColumn: Int? = null,
    val delimiter: Char = ',',
    /** Treat the first row as a header even if it looks like data. A header is also detected when it has no digits. */
    val skipHeader: Boolean = false,
    /** Locates [column] by header name, case-insensitively. The first row is then the header. */
    val columnName: String? = null
)

/**
 * Streaming parsers for TXT, CSV and JSON Lines sources. Lines are read one at a time and every accepted number is
 * handed to [onNumber] as it is found, so large files never sit in memory; only the set of seen numbers does,
 * for deduplication. Nothing here throws on bad input.
 */
class NumberSourceParser(private val normalizer: PhoneNormalizer, private val defaultRegion: String?) {
    fun parseTxt(lines: Sequence<String>, onNumber: (ParsedNumber) -> Unit): ParseStats {
        val sink = Sink(onNumber)
        for ((index, line) in lines.withIndex()) {
            val text = stripComment(line.cleanFirst(index)).trim()
            if (text.isNotEmpty()) sink.offer(text, null)
        }
        return sink.stats()
    }

    fun parseCsv(lines: Sequence<String>, options: CsvOptions = CsvOptions(), onNumber: (ParsedNumber) -> Unit): ParseStats {
        val sink = Sink(onNumber)
        var column = options.column
        var rowIndex = 0
        val records = lines.withIndex().map { (index, line) -> line.cleanFirst(index) }
            .filterNot { it.isBlank() || it.trimStart().startsWith("#") }
        for (text in records) {
            val cells = splitCsv(text, options.delimiter)
            if (rowIndex++ == 0 && isHeader(cells, options)) {
                options.columnName?.let { name -> cells.indexOfFirst { it.trim().equals(name, true) }.takeIf { it >= 0 }?.let { column = it } }
            } else {
                val label = options.labelColumn?.let { cells.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }
                sink.offer(cells.getOrNull(column)?.trim().orEmpty(), label)
            }
        }
        return sink.stats()
    }

    fun parseJsonl(lines: Sequence<String>, onNumber: (ParsedNumber) -> Unit): ParseStats {
        val sink = Sink(onNumber)
        for ((index, line) in lines.withIndex()) {
            val text = line.cleanFirst(index).trim()
            if (text.isEmpty()) continue
            val element = try {
                Json.parseToJsonElement(text)
            } catch (_: SerializationException) {
                null
            }
            val (raw, label) = when (element) {
                is JsonObject -> NUMBER_KEYS.firstNotNullOfOrNull { text(element, it) } to LABEL_KEYS.firstNotNullOfOrNull { text(element, it) }
                is JsonPrimitive -> element.contentOrNull to null
                else -> null to null
            }
            sink.offer(raw.orEmpty(), label)
        }
        return sink.stats()
    }

    fun parse(format: SourceFormat, reader: Reader, csv: CsvOptions = CsvOptions(), onNumber: (ParsedNumber) -> Unit): ParseStats {
        val lines = reader.buffered().lineSequence()
        return when (format) {
            SourceFormat.TXT -> parseTxt(lines, onNumber)
            SourceFormat.CSV -> parseCsv(lines, csv, onNumber)
            SourceFormat.JSONL -> parseJsonl(lines, onNumber)
            SourceFormat.PACK -> error("Packs are SQLite files, open them with PackDatabase")
        }
    }

    /** Convenience for small inputs and tests. */
    fun collect(format: SourceFormat, text: String, csv: CsvOptions = CsvOptions()): Pair<List<ParsedNumber>, ParseStats> {
        val out = ArrayList<ParsedNumber>()
        val stats = parse(format, text.reader(), csv) { out += it }
        return out to stats
    }

    private inner class Sink(val onNumber: (ParsedNumber) -> Unit) {
        private val seen = HashSet<String>()
        private var invalid = 0
        private var duplicates = 0

        fun offer(raw: String, label: String?) {
            val e164 = (normalizer.normalize(raw, defaultRegion) as? NormalizedNumber.Valid)?.e164
            when {
                e164 == null -> invalid++
                !seen.add(e164) -> duplicates++
                else -> onNumber(ParsedNumber(e164, label))
            }
        }

        fun stats() = ParseStats(seen.size, invalid, duplicates)
    }

    private fun isHeader(cells: List<String>, options: CsvOptions): Boolean {
        if (options.skipHeader || options.columnName != null) return true
        return cells.getOrNull(options.column)?.none { it.isDigit() } ?: false
    }

    private fun String.cleanFirst(index: Int) = if (index == 0) removePrefix("") else this

    private fun stripComment(line: String): String = line.substringBefore('#')

    private fun text(obj: JsonObject, key: String): String? = (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        private val NUMBER_KEYS = listOf("number", "e164", "phone", "value")
        private val LABEL_KEYS = listOf("label", "name", "description")

        /** Splits one CSV record, honouring double quotes and `""` escapes. Records do not span lines. */
        fun splitCsv(line: String, delimiter: Char = ','): List<String> {
            val cells = ArrayList<String>()
            val cell = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    quoted && c == '"' && line.getOrNull(i + 1) == '"' -> {
                        cell.append('"')
                        i++
                    }
                    c == '"' -> quoted = !quoted
                    c == delimiter && !quoted -> {
                        cells += cell.toString()
                        cell.clear()
                    }
                    else -> cell.append(c)
                }
                i++
            }
            cells += cell.toString()
            return cells
        }
    }
}
