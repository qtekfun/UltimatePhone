package com.qtekfun.ultimatephone.core.spam.sources

import com.qtekfun.ultimatephone.core.phonenumber.LibPhoneNormalizer
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourcesTest {
    private val parser = NumberSourceParser(LibPhoneNormalizer(), "ES")

    private fun numbers(format: SourceFormat, text: String, csv: CsvOptions = CsvOptions()) = parser.collect(format, text, csv)

    @Test
    fun txtSkipsCommentsAndBlankLinesAndCountsInvalid() {
        val (out, stats) = numbers(
            SourceFormat.TXT,
            "# header\n612345678\n\n+34 611 22 33 44  # trailing\nnot a number\n123\n   # indented comment\n"
        )
        assertEquals(listOf("+34612345678", "+34611223344"), out.map { it.e164 })
        assertEquals(ParseStats(accepted = 2, invalid = 2, duplicates = 0), stats)
    }

    @Test
    fun duplicatesAcrossFormatsAreCollapsed() {
        val (out, stats) = numbers(SourceFormat.TXT, "612345678\n+34612345678\n0034612345678\n611223344\n")
        assertEquals(2, out.size)
        assertEquals(2, stats.duplicates)
    }

    @Test
    fun txtUsesTheDefaultRegionForNationalNumbers() {
        val de = NumberSourceParser(LibPhoneNormalizer(), "DE")
        val out = ArrayList<ParsedNumber>()
        de.parseTxt(sequenceOf("01511 2345678")) { out += it }
        assertEquals("+4915112345678", out.single().e164)
    }

    @Test
    fun csvWithHeaderAutoDetected() {
        val (out, stats) = numbers(SourceFormat.CSV, "number,label\n612345678,Robocall\n611223344,\"Acme, Inc\"\n", CsvOptions(labelColumn = 1))
        assertEquals(listOf("+34612345678" to "Robocall", "+34611223344" to "Acme, Inc"), out.map { it.e164 to it.label })
        assertEquals(0, stats.invalid)
    }

    @Test
    fun csvWithoutHeaderAndOtherColumnAndDelimiter() {
        val (out, stats) = numbers(SourceFormat.CSV, "1;612345678\n2;bad\n3;611223344\n", CsvOptions(column = 1, delimiter = ';'))
        assertEquals(2, out.size)
        assertEquals(1, stats.invalid)
    }

    @Test
    fun csvColumnByHeaderName() {
        val (out, _) = numbers(SourceFormat.CSV, "id,phone,notes\n1,612345678,x\n2,611223344,y\n", CsvOptions(columnName = "PHONE"))
        assertEquals(listOf("+34612345678", "+34611223344"), out.map { it.e164 })
    }

    @Test
    fun csvSkipHeaderForcesFirstRowToBeIgnored() {
        val (out, _) = numbers(SourceFormat.CSV, "612345678\n611223344\n", CsvOptions(skipHeader = true))
        assertEquals(listOf("+34611223344"), out.map { it.e164 })
    }

    @Test
    fun csvShortRowsAndCommentsDoNotCrash() {
        val (out, stats) = numbers(SourceFormat.CSV, "# c\na,b\n\n612345678\n,\n", CsvOptions(column = 1))
        assertEquals(0, out.size)
        assertEquals(2, stats.invalid)
    }

    @Test
    fun splitCsvHandlesQuotesAndEscapes() {
        assertEquals(listOf("a", "b,c", "d\"e", ""), NumberSourceParser.splitCsv("a,\"b,c\",\"d\"\"e\","))
    }

    @Test
    fun jsonlAcceptsObjectsAndPlainStringsAndSkipsGarbage() {
        val text = """
            {"number":"612345678","label":"A","extra":1}
            {"e164":"+34611223344","name":"B"}
            "+34600111222"
            {"nothing":true}
            [1]
            broken {
            {"phone":"612345678"}
        """.trimIndent()
        val (out, stats) = numbers(SourceFormat.JSONL, text)
        assertEquals(listOf("+34612345678" to "A", "+34611223344" to "B", "+34600111222" to null), out.map { it.e164 to it.label })
        assertEquals(ParseStats(accepted = 3, invalid = 3, duplicates = 1), stats)
    }

    @Test
    fun streamingEmitsNumbersWithoutBuildingAList() {
        var count = 0
        val lines = generateSequence(600000000) { it + 1 }.take(1000).map { "+34$it" }
        val stats = parser.parseTxt(lines) { count++ }
        assertEquals(count, stats.accepted)
        assertEquals(1000, stats.accepted + stats.invalid + stats.duplicates)
    }

    @Test
    fun emptyInputYieldsNothing() {
        SourceFormat.entries.filter { it != SourceFormat.PACK }.forEach {
            assertEquals(ParseStats(0, 0, 0), numbers(it, "").second)
        }
    }

    // Packs.

    private class FakePack(private val rows: Map<String, PackEntry>, private val rules: List<PrefixRule> = emptyList()) : PackDatabase {
        var closed = false

        override fun lookup(e164: String) = rows[e164]

        override fun prefixRules() = rules

        override fun close() {
            closed = true
        }
    }

    @Test
    fun packSourceLookupReturnsSourceLevelAndLabel() {
        val pack = FakePack(mapOf("+34612345678" to PackEntry("+34612345678", label = "Spam")))
        val lookup = PackSourceLookup("pack-a", pack, SpamLevel.RULE)
        val hit = lookup.find("+34612345678")!!
        assertEquals("pack-a", hit.sourceId)
        assertEquals(SpamLevel.RULE, hit.level)
        assertEquals("Spam", hit.label)
        assertNull(lookup.find("+34611111111"))
    }

    @Test
    fun compositeSourceLookupPrefersTheFirstSource() {
        val a = PackSourceLookup("a", FakePack(mapOf("+34612345678" to PackEntry("+34612345678"))))
        val b = PackSourceLookup("b", FakePack(mapOf("+34612345678" to PackEntry("+34612345678"), "+34611223344" to PackEntry("+34611223344"))))
        val both = CompositeSourceLookup(listOf(a, b))
        assertEquals("a", both.find("+34612345678")?.sourceId)
        assertEquals("b", both.find("+34611223344")?.sourceId)
        assertNull(both.find("+34600000000"))
    }

    @Test
    fun businessLookupNeedsAName() {
        val pack = FakePack(
            mapOf(
                "+34911111111" to PackEntry("+34911111111", name = "Bar Pepe", category = "bar"),
                "+34922222222" to PackEntry("+34922222222")
            )
        )
        val lookup = PackBusinessLookup(listOf("businesses-es" to pack))
        val hit = lookup.find("+34911111111")!!
        assertEquals("Bar Pepe", hit.name)
        assertEquals("bar", hit.category)
        assertEquals("businesses-es", hit.sourceId)
        assertNull(lookup.find("+34922222222"))
    }

    @Test
    fun sourceDefinitionDefaultsToEnabledCommunity() {
        val d = SourceDefinition("id", "Name", "https://example.org/x.txt", SourceFormat.TXT, "MIT", RedistributionPolicy.REDISTRIBUTABLE)
        assertEquals(SpamLevel.COMMUNITY, d.level)
        assertEquals(true, d.enabled)
    }
}
