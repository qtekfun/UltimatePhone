package com.qtekfun.ultimatephone.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionMatcherTest {
    private fun entry(name: String, number: String, key: String = name) = SuggestionEntry(key, name, number, null)

    private fun names(entries: List<SuggestionEntry>, query: String, limit: Int = 8) = SuggestionMatcher.suggest(entries, query, limit).map { it.displayName }

    @Test
    fun `empty or non digit query matches nothing`() {
        val entries = listOf(entry("Ana", "600111222"))
        assertTrue(names(entries, "").isEmpty())
        assertTrue(names(entries, "abc").isEmpty())
        assertTrue(names(entries, "2", limit = 0).isEmpty())
    }

    @Test
    fun `t9 matches the start of the name`() {
        val entries = listOf(entry("Ana", "600111222"), entry("Tom", "700"))
        assertEquals(listOf("Ana"), names(entries, "26"))
        assertEquals(listOf("Ana"), names(entries, "262"))
        assertTrue(names(entries, "2623").isEmpty())
    }

    @Test
    fun `t9 matches the start of any word of the name`() {
        val entries = listOf(entry("Juan Perez", "1"), entry("Maria del Mar", "2"))
        assertEquals(listOf("Juan Perez"), names(entries, "737")) // PER
        assertEquals(listOf("Juan Perez"), names(entries, "58")) // JU
        assertEquals(listOf("Maria del Mar"), names(entries, "335")) // DEL
        assertEquals(listOf("Maria del Mar"), names(entries, "627")) // MAR (Maria first, Mar later; same contact once)
    }

    @Test
    fun `t9 does not match the middle of a word`() {
        val entries = listOf(entry("Anna", "1"))
        assertTrue(names(entries, "66").isEmpty()) // "nn" is inside the word
    }

    @Test
    fun `accents and case do not matter`() {
        val entries = listOf(entry("Émilie Ñandú", "1"))
        assertEquals(listOf("Émilie Ñandú"), names(entries, "3645")) // EMIL
        assertEquals(listOf("Émilie Ñandú"), names(entries, "6263")) // NAND
    }

    @Test
    fun `digits inside names are matched literally`() {
        val entries = listOf(entry("7 Eleven", "999"))
        assertEquals(listOf("7 Eleven"), names(entries, "7"))
        assertEquals(listOf("7 Eleven"), names(entries, "35")) // ELE, the second word
    }

    @Test
    fun `number partials match start and inside and ignore formatting`() {
        val entries = listOf(entry("Ana", "+34 600-123 456"))
        assertEquals(listOf("Ana"), names(entries, "34600"))
        assertEquals(listOf("Ana"), names(entries, "123"))
        assertEquals(listOf("Ana"), names(entries, "456"))
        assertEquals(listOf("Ana"), names(entries, "600 123"))
        assertTrue(names(entries, "999").isEmpty())
    }

    @Test
    fun `national part of an international number counts as its start`() {
        val intl = entry("Intl", "+34 111 000") // 111 starts the national part after the country code
        val inside = entry("Inside", "5 999 111 000") // 111 is merely inside
        assertEquals(listOf("Intl", "Inside"), names(listOf(inside, intl), "111"))
    }

    @Test
    fun `name start and number start rank before contains`() {
        val entries = listOf(
            entry("Zed", "1226999", key = "contains"), // 26 only inside the number
            entry("Carl", "26000", key = "numberstart"), // number starts with 26
            entry("Al Bo", "5", key = "word"), // BO = 26 starts the second word
            entry("Anita", "7", key = "namestart") // ANITA = 26482
        )
        val result = SuggestionMatcher.suggest(entries, "26", 10).map { it.lookupKey }
        assertEquals(listOf("namestart", "numberstart", "word", "contains"), result)
    }

    @Test
    fun `ties keep the input order which is alphabetical`() {
        val entries = listOf(entry("Ana", "1"), entry("Anabel", "2"), entry("Anselmo", "3"))
        assertEquals(listOf("Ana", "Anabel", "Anselmo"), names(entries, "26"))
    }

    @Test
    fun `limit is applied after ranking`() {
        val entries = (1..20).map { entry("Zed $it", "100$it") } + entry("Ann", "5")
        val result = names(entries, "266", limit = 3)
        assertEquals(listOf("Ann"), result)
        val many = names((1..20).map { entry("Ann $it", "$it") }, "266", limit = 5)
        assertEquals(5, many.size)
        assertEquals("Ann 1", many.first())
    }

    @Test
    fun `a contact with several numbers appears once with the number that matched`() {
        val entries = listOf(entry("Ana", "111222", key = "k"), entry("Ana", "999888", key = "k"))
        val byNumber = SuggestionMatcher.suggest(entries, "9998", 5)
        assertEquals(1, byNumber.size)
        assertEquals("999888", byNumber.single().number)
        val byName = SuggestionMatcher.suggest(entries, "26", 5)
        assertEquals(1, byName.size)
        assertEquals("111222", byName.single().number)
    }

    @Test
    fun `a better rank of a later number wins for the same contact`() {
        val entries = listOf(entry("Zed", "5126", key = "k"), entry("Zed", "2699", key = "k"))
        assertEquals("2699", SuggestionMatcher.suggest(entries, "26", 5).single().number)
    }

    @Test
    fun `answers in under 100 ms with 10000 contacts`() {
        val first = listOf("Ana", "Juan", "Maria", "Pedro", "Lucia", "Carlos", "Elena", "Sofia", "Diego", "Marta")
        val last = listOf("Garcia", "Lopez", "Martinez", "Sanchez", "Perez", "Gomez", "Ruiz", "Diaz", "Alvarez", "Romero")
        val entries = (0 until 10_000).map { i ->
            val name = "${first[i % 10]} ${last[(i / 10) % 10]} $i"
            SuggestionEntry("key$i", name, "+34 6${"%08d".format(i * 7919L % 100_000_000)}", null)
        }
        for (query in listOf("2", "26", "6279", "600", "123456", "7377")) {
            val best = (1..3).minOf { measureMillis { SuggestionMatcher.suggest(entries, query, 8) } }
            assertTrue("query $query took $best ms", best < 100)
        }
    }

    private inline fun measureMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
