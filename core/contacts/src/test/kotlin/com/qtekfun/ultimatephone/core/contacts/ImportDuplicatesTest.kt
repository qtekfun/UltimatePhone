package com.qtekfun.ultimatephone.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportDuplicatesTest {
    private val detector = DuplicateDetector(FakeNormalizer(), "ES")
    private val existing = listOf(
        DuplicateContact(1, "k1", "Ana García", listOf("600111222"), listOf("ana@x.org")),
        DuplicateContact(2, "k2", "Luis Pérez", listOf("611000000"))
    )

    private fun card(name: String, phone: String? = null, email: String? = null) = VCardContact(
        name = VCardName(formatted = name),
        phones = listOfNotNull(phone?.let { LabeledValue(it, LabelTypes.MOBILE) }),
        emails = listOfNotNull(email?.let { LabeledValue(it, LabelTypes.EMAIL_HOME) })
    )

    @Test
    fun `cards matching a stored contact are found by number, email and name`() {
        val imported = listOf(
            card("Somebody", phone = "+34 600 111 222"), // same number
            card("Other", email = "ANA@x.org"), // same email
            card("garcia, ana"), // same name
            card("New Person", phone = "699999999") // nothing
        )
        assertEquals(setOf(0, 1, 2), ImportDuplicates.of(detector, existing, imported))
    }

    @Test
    fun `cards that only resemble each other are not duplicates of the address book`() {
        val imported = listOf(card("Pedro Ruiz", phone = "644000000"), card("Ruiz Pedro", phone = "644000000"))
        assertTrue(ImportDuplicates.of(detector, existing, imported).isEmpty())
    }

    @Test
    fun `nothing stored or nothing imported`() {
        assertTrue(ImportDuplicates.of(detector, emptyList(), listOf(card("A", phone = "600111222"))).isEmpty())
        assertTrue(ImportDuplicates.of(detector, existing, emptyList()).isEmpty())
    }
}
