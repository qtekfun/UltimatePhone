package com.qtekfun.ultimatephone.core.contacts

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardWriterTest {
    private fun write(vararg contacts: VCardContact) = VCardWriter.toString(contacts.toList())

    private val full = VCardContact(
        name = VCardName("García", "Ana", "María", "Dra.", "Jr.", "Ana García"),
        nicknames = listOf("Ani", "Nan"),
        organization = "ACME; Inc, Ltd",
        title = "Boss",
        phones = listOf(
            LabeledValue("+34 600 111 222", LabelTypes.MOBILE),
            LabeledValue("911 222 333", LabelTypes.WORK),
            LabeledValue("911 000 000", LabelTypes.HOME),
            LabeledValue("900 100 100", LabelTypes.MAIN),
            LabeledValue("123", LabelTypes.OTHER),
            LabeledValue("600000000", LabelTypes.CUSTOM, "Grandma's; house")
        ),
        emails = listOf(
            LabeledValue("ana@home.org", LabelTypes.EMAIL_HOME),
            LabeledValue("ana@work.org", LabelTypes.EMAIL_WORK),
            LabeledValue("ana@phone.org", LabelTypes.EMAIL_MOBILE),
            LabeledValue("ana@other.org", LabelTypes.EMAIL_OTHER),
            LabeledValue("ana@club.org", LabelTypes.CUSTOM, "Club")
        ),
        birthday = ContactDate(1990, 5, 17),
        note = "Line one\nLine two, with; special \\ chars",
        photo = VCardPhoto(
            ByteArray(300) { (it * 7).toByte() }.also {
                it[0] = 0xFF.toByte()
                it[1] = 0xD8.toByte()
            }
        ),
        websites = listOf("https://example.org/a?b=c;d"),
        addresses = listOf(VCardAddress("Calle Mayor 1\n28001 Madrid", VCardAddress.ADDRESS_HOME), VCardAddress("Work St 2", VCardAddress.ADDRESS_WORK))
    )

    @Test
    fun `writes the expected 3_0 structure`() {
        val text =
            write(VCardContact(name = VCardName("García", "Ana", formatted = "Ana García"), phones = listOf(LabeledValue("600111222", LabelTypes.MOBILE))))
        assertEquals(
            "BEGIN:VCARD\r\nVERSION:3.0\r\nN:García;Ana;;;\r\nFN:Ana García\r\nTEL;TYPE=CELL:600111222\r\nEND:VCARD\r\n",
            text
        )
    }

    @Test
    fun `escapes special characters`() {
        assertEquals("a\\;b\\,c\\\\d\\ne", VCardWriter.escape("a;b,c\\d\ne"))
        assertEquals("ab", VCardWriter.escape("a\rb"))
    }

    @Test
    fun `every line is at most 75 octets and continuation lines start with a space`() {
        val text = write(full, VCardContact(name = VCardName(formatted = "ñ".repeat(100)), note = "€".repeat(100) + "😀".repeat(40)))
        val lines = text.split("\r\n")
        assertEquals("", lines.last())
        lines.forEach { assertTrue("${it.length}: $it", it.toByteArray(Charsets.UTF_8).size <= 75) }
        assertTrue(lines.any { it.startsWith(" ") })
        // Characters are never split: the text is valid UTF-8 and unfolds to the original.
        val round = VCardParser.parse(text)
        assertEquals("ñ".repeat(100), round.contacts[1].displayName)
        assertEquals("€".repeat(100) + "😀".repeat(40), round.contacts[1].note)
    }

    @Test
    fun `a contact with no name uses another field for FN`() {
        assertTrue(write(VCardContact(phones = listOf(LabeledValue("600111222", LabelTypes.MOBILE)))).contains("FN:600111222"))
        assertTrue(write(VCardContact(organization = "ACME")).contains("FN:ACME"))
    }

    @Test
    fun `round trip keeps every field`() {
        val result = VCardParser.parse(write(full).toByteArray(Charsets.UTF_8))
        assertEquals(0, result.skipped)
        val back = result.contacts.single()
        assertEquals(full.name, back.name)
        assertEquals(full.nicknames, back.nicknames)
        assertEquals("ACME; Inc, Ltd", back.organization)
        assertEquals(full.title, back.title)
        assertEquals(full.phones, back.phones)
        assertEquals(full.emails, back.emails)
        assertEquals(full.birthday, back.birthday)
        assertEquals(full.note, back.note)
        assertEquals(full.photo, back.photo)
        assertEquals(full.websites, back.websites)
        assertEquals(full.addresses.map { it.type }, back.addresses.map { it.type })
        assertEquals("Calle Mayor 1\n28001 Madrid", back.addresses[0].formatted)
    }

    @Test
    fun `round trip without a birth year`() {
        val back = VCardParser.parse(write(VCardContact(name = VCardName(formatted = "A"), birthday = ContactDate(null, 12, 24)))).contacts.single()
        assertEquals(ContactDate(null, 12, 24), back.birthday)
    }

    @Test
    fun `round trip of many contacts keeps order and count`() {
        val random = Random(7)
        val contacts = (1..300).map { i ->
            VCardContact(
                name = VCardName(family = "Fam$i", given = "Giv$i", formatted = "Giv$i Fam$i"),
                phones = listOf(LabeledValue("6${random.nextInt(99999999)}", LabelTypes.MOBILE)),
                photo = if (i % 10 == 0) VCardPhoto(ByteArray(2000) { random.nextInt().toByte() }) else null
            )
        }
        val result = VCardParser.parse(VCardWriter.toString(contacts).toByteArray(Charsets.UTF_8))
        assertEquals(0, result.skipped)
        assertEquals(contacts, result.contacts)
    }

    @Test
    fun `photo types are detected`() {
        val png = VCardPhoto(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2))
        val gif = VCardPhoto(byteArrayOf('G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 1, 2))
        assertTrue(write(VCardContact(name = VCardName(formatted = "A"), photo = png)).contains("PHOTO;ENCODING=b;TYPE=PNG:"))
        assertTrue(write(VCardContact(name = VCardName(formatted = "A"), photo = gif)).contains("TYPE=GIF"))
        assertEquals("image/jpeg", VCardPhoto(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2)).mimeType)
    }

    @Test
    fun `usable cards and duplicate input`() {
        assertTrue(!VCardContact().isUsable)
        val dup = full.toDuplicateContact(3)
        assertEquals(-4L, dup.contactId)
        assertEquals("import:3", dup.lookupKey)
        assertEquals(full.phones.size, dup.phones.size)
    }
}
