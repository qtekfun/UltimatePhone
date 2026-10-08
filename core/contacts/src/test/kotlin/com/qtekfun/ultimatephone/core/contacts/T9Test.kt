package com.qtekfun.ultimatephone.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class T9Test {
    @Test
    fun `every letter maps to its keypad digit`() {
        val expected = mapOf(
            "abc" to '2',
            "def" to '3',
            "ghi" to '4',
            "jkl" to '5',
            "mno" to '6',
            "pqrs" to '7',
            "tuv" to '8',
            "wxyz" to '9'
        )
        expected.forEach { (letters, key) ->
            letters.forEach { assertEquals("letter $it", key, T9.keyOf(it)) }
            letters.uppercase().forEach { assertEquals("letter $it", key, T9.keyOf(it)) }
        }
    }

    @Test
    fun `digits map to themselves and other characters to nothing`() {
        "0123456789".forEach { assertEquals(it, T9.keyOf(it)) }
        assertNull(T9.keyOf(' '))
        assertNull(T9.keyOf('-'))
        assertNull(T9.keyOf('中'))
    }

    @Test
    fun `accents are folded`() {
        assertEquals("emilie", T9.fold("Émilie"))
        assertEquals("nino", T9.fold("Niño"))
        assertEquals("strasse", T9.fold("Straße"))
        assertEquals("oyvind", T9.fold("Øyvind"))
        assertEquals("zoe", T9.fold("Zoë"))
        assertEquals("a", T9.fold("Å"))
        assertEquals(T9.encode("jose"), T9.encode("José"))
    }

    @Test
    fun `encode turns a word into keypad digits`() {
        assertEquals("5646", T9.encode("john"))
        assertEquals("5646", T9.encode("JOHN"))
        assertEquals("76484", T9.encode("smith"))
        assertEquals("73376", T9.encode("pedro"))
        assertEquals("7355", T9.encode("7eLL"))
        assertEquals("", T9.encode("--"))
    }

    @Test
    fun `words split on spaces punctuation and drop empties`() {
        assertEquals(listOf("ana", "maria", "o", "neil"), T9.words("  Ana-María  O'Neil "))
        assertEquals(listOf("7", "eleven"), T9.words("7 Eleven"))
        assertEquals(emptyList<String>(), T9.words("   "))
        assertEquals(emptyList<String>(), T9.words("中文"))
    }

    @Test
    fun `digitsOf removes formatting`() {
        assertEquals("34600123456", T9.digitsOf("+34 600-123 456"))
        assertEquals("911", T9.digitsOf("(9) 1.1"))
        assertEquals("", T9.digitsOf("abc"))
    }
}
