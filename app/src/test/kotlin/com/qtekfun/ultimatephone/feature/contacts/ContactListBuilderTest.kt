package com.qtekfun.ultimatephone.feature.contacts

import com.qtekfun.ultimatephone.core.contacts.ContactSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactListBuilderTest {
    private fun contact(name: String, starred: Boolean = false, key: String = name) = ContactSummary(key, 1, name, starred, null)

    private fun labels(content: ContactListContent) = content.rows.map {
        when (it) {
            ListRow.FavoritesHeader -> "*"
            is ListRow.LetterHeader -> "[${it.letter}]"
            is ListRow.Item -> it.contact.displayName
        }
    }

    @Test
    fun `letters ignore case and accents and other names go to hash`() {
        assertEquals("A", ContactListBuilder.letterOf("ana"))
        assertEquals("E", ContactListBuilder.letterOf("Émilie"))
        assertEquals("N", ContactListBuilder.letterOf("Ñandú"))
        assertEquals("#", ContactListBuilder.letterOf("7 Eleven"))
        assertEquals("#", ContactListBuilder.letterOf("+34 600"))
        assertEquals("#", ContactListBuilder.letterOf(""))
        assertEquals("A", ContactListBuilder.letterOf("  Ana"))
    }

    @Test
    fun `contacts are grouped by letter with hash last`() {
        val content = ContactListBuilder.build(listOf(contact("Ana"), contact("Álvaro"), contact("Bea"), contact("123"), contact("Zoe")), "")
        assertEquals(listOf("[A]", "Ana", "Álvaro", "[B]", "Bea", "[Z]", "Zoe", "[#]", "123"), labels(content))
        assertEquals(listOf("A", "B", "Z", "#"), content.indexLetters)
        assertEquals(mapOf("A" to 0, "B" to 3, "Z" to 5, "#" to 7), content.positions)
    }

    @Test
    fun `favorites come first and also stay in their letter`() {
        val content = ContactListBuilder.build(listOf(contact("Ana"), contact("Bea", starred = true)), "")
        assertEquals(listOf("*", "Bea", "[A]", "Ana", "[B]", "Bea"), labels(content))
        assertEquals(listOf(ContactListContent.FAVORITES_INDEX, "A", "B"), content.indexLetters)
        assertEquals(0, content.positions[ContactListContent.FAVORITES_INDEX])
        val keys = content.rows.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `searching hides favorites section and filters by name`() {
        val contacts = listOf(contact("Ana Lopez", starred = true), contact("Juan Perez"), contact("Anabel"))
        val content = ContactListBuilder.build(contacts, "ana")
        assertEquals(listOf("[A]", "Ana Lopez", "Anabel"), labels(content))
    }

    @Test
    fun `search matches every word in any order and ignores accents`() {
        val contacts = listOf(contact("María José Pérez"), contact("José Luis"))
        assertEquals(listOf("[M]", "María José Pérez"), labels(ContactListBuilder.build(contacts, "perez jose")))
        assertEquals(listOf("[J]", "José Luis", "[M]", "María José Pérez"), labels(ContactListBuilder.build(contacts, "JOSE")))
        assertTrue(ContactListBuilder.build(contacts, "xyz").isEmpty)
    }

    @Test
    fun `number matches are included even when the name does not match`() {
        val contacts = listOf(contact("Ana"), contact("Bea", key = "bea-key"))
        val content = ContactListBuilder.build(contacts, "600", numberMatches = setOf("bea-key"))
        assertEquals(listOf("[B]", "Bea"), labels(content))
    }

    @Test
    fun `no contacts gives an empty list`() {
        assertTrue(ContactListBuilder.build(emptyList(), "").isEmpty)
    }
}
