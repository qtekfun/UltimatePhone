package com.qtekfun.ultimatephone.core.spam.lists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListsTest {
    private fun entry(
        value: String,
        kind: EntryKind = EntryKind.NUMBER,
        id: String = "id-$value",
        updatedAt: Long = 1L,
        deviceId: String = "dev",
        deleted: Boolean = false
    ) = ListEntry(id, kind, value, "label", "note", updatedAt, deviceId, deleted)

    // Values and lookup keys.

    @Test
    fun validatesNumbersAndPrefixes() {
        assertTrue(EntryValues.isValid(EntryKind.NUMBER, "+34612345678"))
        assertFalse(EntryValues.isValid(EntryKind.NUMBER, "612345678"))
        assertFalse(EntryValues.isValid(EntryKind.NUMBER, "+34 612 345 678"))
        assertFalse(EntryValues.isValid(EntryKind.NUMBER, "+3412"))
        assertTrue(EntryValues.isValid(EntryKind.PREFIX, "+34900"))
        assertTrue(EntryValues.isValid(EntryKind.PREFIX, "+3"))
        assertFalse(EntryValues.isValid(EntryKind.PREFIX, "+"))
        assertFalse(EntryValues.isValid(EntryKind.PREFIX, "34900"))
        assertFalse(EntryValues.isValid(EntryKind.PREFIX, "+0900"))
    }

    @Test
    fun lookupKeysAreTheNumberThenItsPrefixes() {
        val keys = EntryValues.lookupKeys("+34900123456")
        assertEquals("+34900123456", keys.first())
        assertTrue("+34900" in keys)
        assertTrue("+34" in keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun exactNumberBeatsPrefix() {
        val rows = listOf(entry("+34900", EntryKind.PREFIX), entry("+34900123456"))
        assertEquals("+34900123456", EntryValues.best("+34900123456", rows)?.value)
    }

    @Test
    fun longestPrefixWins() {
        val rows = listOf(entry("+34", EntryKind.PREFIX), entry("+349001", EntryKind.PREFIX), entry("+34900", EntryKind.PREFIX))
        assertEquals("+349001", EntryValues.best("+34900123456", rows)?.value)
    }

    @Test
    fun tombstonesAndNonMatchingRowsAreIgnored() {
        val rows = listOf(entry("+34900", EntryKind.PREFIX, deleted = true), entry("+34911", EntryKind.PREFIX), entry("+34900999999"))
        assertNull(EntryValues.best("+34900123456", rows))
    }

    @Test
    fun aPrefixDoesNotMatchTheExactNumberItself() {
        // A prefix equal to the whole number is not a prefix of it: only a NUMBER entry covers that.
        assertNull(EntryValues.best("+34900123456", listOf(entry("+34900123456", EntryKind.PREFIX))))
    }

    // Merge.

    @Test
    fun newerUpdateWinsAndTieGoesToGreaterDevice() {
        val a = entry("+34600000001", updatedAt = 10, deviceId = "a")
        val b = a.copy(updatedAt = 20, deviceId = "b", label = "newer")
        assertEquals(b, EntryMerge.winner(a, b))
        assertEquals(b, EntryMerge.winner(b, a))
        val tieA = a.copy(updatedAt = 5, deviceId = "a")
        val tieB = a.copy(updatedAt = 5, deviceId = "b")
        assertEquals(tieB, EntryMerge.winner(tieA, tieB))
        assertEquals(tieB, EntryMerge.winner(tieB, tieA))
    }

    @Test
    fun aNewerTombstoneBeatsALiveEntry() {
        val live = entry("+34600000001", updatedAt = 10)
        val dead = live.copy(updatedAt = 11, deleted = true)
        assertTrue(EntryMerge.winner(live, dead).deleted)
        assertFalse(EntryMerge.winner(live.copy(updatedAt = 12), dead).deleted)
    }

    // JSON Lines.

    @Test
    fun roundTripsEntriesIncludingNullsAndTombstones() {
        val entries = listOf(
            entry("+34612345678"),
            ListEntry("u2", EntryKind.PREFIX, "+34900", null, null, 99, "d\"2", true),
            ListEntry("u3", EntryKind.NUMBER, "+4915112345678", "ünï \n cødé", "n", 0, "", false)
        )
        val text = EntryJsonl.encodeAll(entries)
        assertEquals(3, text.lines().filter { it.isNotEmpty() }.size)
        val back = EntryJsonl.decodeAll(text)
        assertEquals(entries, back.entries)
        assertEquals(0, back.badLines)
    }

    @Test
    fun encodedShapeFollowsTheSyncSpec() {
        val line = EntryJsonl.encode(ListEntry("x", EntryKind.PREFIX, "+34900", "l", null, 5, "dev", false))
        assertEquals(
            """{"id":"x","kind":"prefix","value":"+34900","label":"l","note":null,"updatedAt":5,"deviceId":"dev","deleted":false}""",
            line
        )
    }

    @Test
    fun decodeIgnoresUnknownFieldsAndDefaultsOptionalOnes() {
        val e = EntryJsonl.decode("""{"id":"a","kind":"NUMBER","value":"+34612345678","updatedAt":7,"future":{"x":1}}""")!!
        assertEquals(EntryKind.NUMBER, e.kind)
        assertNull(e.label)
        assertEquals("", e.deviceId)
        assertFalse(e.deleted)
    }

    @Test
    fun badLinesAreCountedAndSkipped() {
        val text = listOf(
            """{"id":"a","kind":"number","value":"+34612345678","updatedAt":1,"deviceId":"d"}""",
            "",
            "   ",
            "not json",
            """{"id":"b","kind":"number","value":"612345678","updatedAt":1}""",
            """{"id":"c","kind":"other","value":"+34612345678","updatedAt":1}""",
            """{"kind":"number","value":"+34612345679","updatedAt":1}""",
            """{"id":"d","kind":"number","value":"+34612345679"}""",
            "[1,2]",
            "\"+34612345678\"",
            """{"id":"e","kind":"prefix","value":"+34900","updatedAt":2,"deleted":true}"""
        ).joinToString("\n")
        val result = EntryJsonl.decodeAll(text)
        assertEquals(listOf("a", "e"), result.entries.map { it.id })
        assertEquals(7, result.badLines)
    }

    @Test
    fun byteOrderMarkOnTheFirstLineIsTolerated() {
        val e = EntryJsonl.decode("" + """{"id":"a","kind":"number","value":"+34612345678","updatedAt":1}""")
        assertEquals("a", e?.id)
    }
}
