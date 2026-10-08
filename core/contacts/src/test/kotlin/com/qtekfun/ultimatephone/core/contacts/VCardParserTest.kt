package com.qtekfun.ultimatephone.core.contacts

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardParserTest {
    private fun parse(vararg lines: String) = VCardParser.parse(lines.joinToString("\r\n") + "\r\n")

    private fun one(vararg lines: String): VCardContact {
        val result = parse(*lines)
        assertEquals(0, result.skipped)
        assertEquals(1, result.contacts.size)
        return result.contacts[0]
    }

    @Test
    fun `reads a plain 3_0 card`() {
        val c = one(
            "BEGIN:VCARD",
            "VERSION:3.0",
            "N:García;Ana;María;Dra.;Jr.",
            "FN:Ana García",
            "ORG:ACME;Sales",
            "TITLE:Boss",
            "TEL;TYPE=CELL:+34 600 111 222",
            "TEL;TYPE=WORK,VOICE:911 222 333",
            "TEL;TYPE=HOME:911 000 000",
            "EMAIL;TYPE=INTERNET,HOME:ana@home.org",
            "EMAIL;TYPE=WORK:ana@work.org",
            "BDAY:1990-05-17",
            "NOTE:Line one\\nLine two\\, with comma",
            "URL:https://example.org",
            "END:VCARD"
        )
        assertEquals(VCardName("García", "Ana", "María", "Dra.", "Jr.", "Ana García"), c.name)
        assertEquals("ACME, Sales", c.organization)
        assertEquals("Boss", c.title)
        assertEquals(
            listOf(
                LabeledValue("+34 600 111 222", LabelTypes.MOBILE),
                LabeledValue("911 222 333", LabelTypes.WORK),
                LabeledValue("911 000 000", LabelTypes.HOME)
            ),
            c.phones
        )
        assertEquals(listOf(LabeledValue("ana@home.org", LabelTypes.EMAIL_HOME), LabeledValue("ana@work.org", LabelTypes.EMAIL_WORK)), c.emails)
        assertEquals(ContactDate(1990, 5, 17), c.birthday)
        assertEquals("Line one\nLine two, with comma", c.note)
        assertEquals(listOf("https://example.org"), c.websites)
    }

    @Test
    fun `reads a 4_0 card with uri values and quoted type lists`() {
        val c = one(
            "BEGIN:VCARD",
            "VERSION:4.0",
            "FN:Bob Builder",
            "N:Builder;Bob;;;",
            "TEL;VALUE=uri;TYPE=\"cell,voice\";PREF=1:tel:+34600111222",
            "EMAIL;TYPE=work:bob@work.org",
            "BDAY:--0517",
            "END:VCARD"
        )
        assertEquals("tel number", "+34600111222", c.phones.single().value)
        assertEquals(LabelTypes.MOBILE, c.phones.single().type)
        assertEquals(LabelTypes.EMAIL_WORK, c.emails.single().type)
        assertEquals(ContactDate(null, 5, 17), c.birthday)
    }

    @Test
    fun `reads 2_1 bare types and quoted printable with charset`() {
        val c = one(
            "BEGIN:VCARD",
            "VERSION:2.1",
            "N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Mu=C3=B1oz;Jos=C3=A9;;;",
            "FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Jos=C3=A9 Mu=C3=B1oz",
            "TEL;CELL;VOICE:600111222",
            "TEL;HOME:911000000",
            "NOTE;QUOTED-PRINTABLE;CHARSET=ISO-8859-1:Caf=E9",
            "END:VCARD"
        )
        assertEquals("Muñoz", c.name.family)
        assertEquals("José", c.name.given)
        assertEquals("José Muñoz", c.displayName)
        assertEquals(LabelTypes.MOBILE, c.phones[0].type)
        assertEquals(LabelTypes.HOME, c.phones[1].type)
        assertEquals("Café", c.note)
    }

    @Test
    fun `quoted printable soft line breaks are joined`() {
        val c = one(
            "BEGIN:VCARD",
            "VERSION:2.1",
            "FN:X",
            "NOTE;ENCODING=QUOTED-PRINTABLE:first =",
            "second=0Athird",
            "END:VCARD"
        )
        assertEquals("first second\nthird", c.note)
    }

    @Test
    fun `folded lines are unfolded`() {
        val c = one(
            "BEGIN:VCARD",
            "VERSION:3.0",
            "FN:A very long na",
            " me that was folded",
            "NOTE:one\\n",
            "\ttwo",
            "TEL:600111222",
            "END:VCARD"
        )
        assertEquals("A very long name that was folded", c.displayName)
        assertEquals("one\ntwo", c.note)
    }

    @Test
    fun `reads photos as base64 in 3_0 and as data uri in 4_0`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val b64 = Base64.getEncoder().encodeToString(bytes)
        val v3 = one("BEGIN:VCARD", "VERSION:3.0", "FN:A", "PHOTO;ENCODING=b;TYPE=JPEG:" + b64.take(8), " " + b64.drop(8), "END:VCARD")
        assertArrayEquals(bytes, v3.photo?.bytes)
        val v4 = one("BEGIN:VCARD", "VERSION:4.0", "FN:A", "PHOTO:data:image/jpeg;base64,$b64", "END:VCARD")
        assertArrayEquals(bytes, v4.photo?.bytes)
        val v21 = one("BEGIN:VCARD", "VERSION:2.1", "FN:A", "PHOTO;JPEG;BASE64:", "  $b64", "", "END:VCARD")
        assertArrayEquals(bytes, v21.photo?.bytes)
    }

    @Test
    fun `a photo that is a web address or broken base64 is ignored but the card is kept`() {
        val web = one("BEGIN:VCARD", "VERSION:4.0", "FN:A", "PHOTO;VALUE=uri:https://example.org/a.jpg", "END:VCARD")
        assertNull(web.photo)
        val broken = one("BEGIN:VCARD", "VERSION:3.0", "FN:A", "PHOTO;ENCODING=b:!!!not base64!!!", "END:VCARD")
        assertNull(broken.photo)
    }

    @Test
    fun `unknown and x properties are ignored`() {
        val c = one(
            "BEGIN:VCARD",
            "VERSION:3.0",
            "PRODID:-//x//",
            "X-ANDROID-CUSTOM:vnd.android.cursor.item/nickname;Nick;1;;;;;;;;;;;;;",
            "X-SOCIALPROFILE;type=twitter:https://t.example/a",
            "REV:2020-01-01T00:00:00Z",
            "FN:Ana",
            "TEL:600111222",
            "END:VCARD"
        )
        assertEquals("Ana", c.displayName)
        assertEquals(1, c.phones.size)
    }

    @Test
    fun `property groups carry custom and standard labels`() {
        val c = one(
            "BEGIN:VCARD",
            "VERSION:3.0",
            "FN:Ana",
            "item1.TEL:600111222",
            "item1.X-ABLabel:Grandma's",
            "item2.TEL:611222333",
            "item2.X-ABLabel:_\$!<Mobile>!\$_",
            "item3.EMAIL;type=INTERNET:a@x.org",
            "item3.X-ABLabel:Club",
            "END:VCARD"
        )
        assertEquals(LabeledValue("600111222", LabelTypes.CUSTOM, "Grandma's"), c.phones[0])
        assertEquals(LabeledValue("611222333", LabelTypes.MOBILE), c.phones[1])
        assertEquals(LabeledValue("a@x.org", LabelTypes.CUSTOM, "Club"), c.emails[0])
    }

    @Test
    fun `bday formats`() {
        fun bday(value: String) = one("BEGIN:VCARD", "VERSION:3.0", "FN:A", "BDAY:$value", "END:VCARD").birthday
        assertEquals(ContactDate(1990, 5, 17), bday("19900517"))
        assertEquals(ContactDate(1990, 5, 17), bday("1990-05-17T10:00:00Z"))
        assertEquals(ContactDate(null, 5, 17), bday("--05-17"))
        assertEquals(ContactDate(null, 5, 17), bday("--0517"))
        assertEquals(ContactDate(null, 5, 17), bday("1604-05-17"))
        assertNull(bday("not a date"))
        assertNull(bday("1990-13-40"))
    }

    @Test
    fun `bad cards are skipped and counted, the others load`() {
        val result = parse(
            "BEGIN:VCARD",
            "VERSION:3.0",
            "FN:Good One",
            "END:VCARD",
            "BEGIN:VCARD", // never closed: the next BEGIN starts over
            "VERSION:3.0",
            "FN:Broken",
            "BEGIN:VCARD",
            "VERSION:3.0",
            "FN:Good Two",
            "END:VCARD",
            "BEGIN:VCARD", // nothing usable inside
            "VERSION:3.0",
            "REV:2020",
            "END:VCARD",
            "garbage line outside a card",
            "BEGIN:VCARD", // truncated file
            "FN:Cut off"
        )
        assertEquals(listOf("Good One", "Good Two"), result.contacts.map { it.displayName })
        assertEquals(3, result.skipped)
    }

    @Test
    fun `a card with only a phone or only an organisation is usable`() {
        assertEquals(1, parse("BEGIN:VCARD", "VERSION:3.0", "TEL:600111222", "END:VCARD").contacts.size)
        assertEquals(1, parse("BEGIN:VCARD", "VERSION:3.0", "ORG:ACME", "END:VCARD").contacts.size)
    }

    @Test
    fun `empty and non vcard input`() {
        assertEquals(0, VCardParser.parse("").contacts.size)
        assertEquals(0, VCardParser.parse("hello\nworld").skipped)
    }

    @Test
    fun `accepts lower case keywords, lf endings and a bom`() {
        val text = "﻿begin:vcard\nversion:3.0\nfn:Ana\ntel;type=cell:600111222\nend:vcard\n"
        val c = VCardParser.parse(text.toByteArray(Charsets.UTF_8)).contacts.single()
        assertEquals("Ana", c.displayName)
        assertEquals(LabelTypes.MOBILE, c.phones.single().type)
    }

    @Test
    fun `decodes non utf8 files as windows 1252`() {
        val bytes = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:José Muñoz\r\nEND:VCARD\r\n".toByteArray(Charsets.ISO_8859_1)
        assertEquals("José Muñoz", VCardParser.parse(bytes).contacts.single().displayName)
    }

    @Test
    fun `quoted parameter values may contain colons and semicolons`() {
        val c = one("BEGIN:VCARD", "VERSION:3.0", "FN:A", "TEL;X-LABEL=\"a:b;c\";TYPE=CELL:600111222", "END:VCARD")
        assertEquals("600111222", c.phones.single().value)
        assertEquals(LabelTypes.MOBILE, c.phones.single().type)
    }

    @Test
    fun `multiple notes nicknames and addresses`() {
        val c = one(
            "BEGIN:VCARD",
            "VERSION:3.0",
            "FN:A",
            "NICKNAME:Ani,Nan",
            "NOTE:one",
            "NOTE:two",
            "ADR;TYPE=HOME:;;Calle Mayor 1;Madrid;;28001;Spain",
            "END:VCARD"
        )
        assertEquals(listOf("Ani", "Nan"), c.nicknames)
        assertEquals("one\ntwo", c.note)
        assertEquals(VCardAddress("Calle Mayor 1, Madrid, 28001, Spain", VCardAddress.ADDRESS_HOME), c.addresses.single())
    }

    @Test
    fun `fn only cards keep the display name`() {
        val c = one("BEGIN:VCARD", "VERSION:3.0", "FN:Madonna", "END:VCARD")
        assertEquals("Madonna", c.displayName)
        assertNotNull(c.name)
        assertTrue(!c.name.hasParts)
    }
}
