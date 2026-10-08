package com.qtekfun.ultimatephone.core.contacts

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactModelsTest {
    private fun phone(value: String, type: Int = LabelTypes.MOBILE, label: String? = null) = LabeledValue(value, type, label)

    private fun valid(draft: ContactDraft) = (draft.validate() as DraftValidation.Valid).draft

    @Test
    fun `a draft needs a name or a phone`() {
        assertEquals(DraftValidation.Invalid(DraftError.NAME_OR_PHONE_REQUIRED), ContactDraft().validate())
        assertEquals(DraftValidation.Invalid(DraftError.NAME_OR_PHONE_REQUIRED), ContactDraft(name = "   ", phones = listOf(phone("  "))).validate())
        assertEquals(DraftValidation.Invalid(DraftError.NAME_OR_PHONE_REQUIRED), ContactDraft(emails = listOf(phone("a@b.c"))).validate())
        assertTrue(ContactDraft(name = "Ana").validate() is DraftValidation.Valid)
        assertTrue(ContactDraft(phones = listOf(phone("600111222"))).validate() is DraftValidation.Valid)
    }

    @Test
    fun `blank phones and emails are dropped and values trimmed`() {
        val result = valid(
            ContactDraft(
                name = "  Ana ",
                phones = listOf(phone(" 600111222 "), phone(""), phone("   ")),
                emails = listOf(phone(" "), phone(" a@b.c ", LabelTypes.EMAIL_WORK)),
                notes = " hi ",
                organization = " Acme "
            )
        )
        assertEquals("Ana", result.name)
        assertEquals(listOf(phone("600111222")), result.phones)
        assertEquals(listOf(phone("a@b.c", LabelTypes.EMAIL_WORK)), result.emails)
        assertEquals("hi", result.notes)
        assertEquals("Acme", result.organization)
    }

    @Test
    fun `custom labels are kept only for custom types and trimmed`() {
        val result = valid(
            ContactDraft(
                phones = listOf(
                    phone("1", LabelTypes.CUSTOM, " Gym "),
                    phone("2", LabelTypes.CUSTOM, "  "),
                    phone("3", LabelTypes.WORK, "stale")
                )
            )
        )
        assertEquals(listOf("Gym", null, null), result.phones.map { it.customLabel })
    }

    @Test
    fun `birthday formats with and without year`() {
        assertEquals("1990-05-17", ContactDate(1990, 5, 17).format())
        assertEquals("--05-07", ContactDate(null, 5, 7).format())
    }

    @Test
    fun `birthday parsing accepts provider formats and rejects junk`() {
        assertEquals(ContactDate(1990, 5, 17), ContactDate.parse("1990-05-17"))
        assertEquals(ContactDate(1990, 5, 17), ContactDate.parse("1990-05-17T00:00:00Z"))
        assertEquals(ContactDate(null, 12, 25), ContactDate.parse("--12-25"))
        assertNull(ContactDate.parse("1990-13-01"))
        assertNull(ContactDate.parse("1990-00-10"))
        assertNull(ContactDate.parse("1990-02-32"))
        assertNull(ContactDate.parse("May 17th"))
        assertNull(ContactDate.parse(""))
        assertNull(ContactDate.parse(null))
    }

    @Test
    fun `parse and format round trip`() {
        listOf(ContactDate(2001, 1, 31), ContactDate(null, 2, 29)).forEach { assertEquals(it, ContactDate.parse(it.format())) }
    }

    @Test
    fun `local account has no name and sync accounts do`() {
        assertTrue(ContactAccount.LOCAL.isLocal)
        assertTrue(!ContactAccount("me@example.org", "bitfire.at.davdroid").isLocal)
    }

    private fun detail(primary: Long?) = ContactDetail(
        lookupKey = "k",
        contactId = 1,
        displayName = "600111222",
        editableName = "",
        organization = "Acme",
        phones = listOf(
            StoredValue(phone("600111222"), "Mobile", 10),
            StoredValue(phone("999", LabelTypes.WORK), "Work", 11)
        ),
        emails = listOf(StoredValue(phone("a@b.c", LabelTypes.EMAIL_HOME), "Home", 11)),
        birthday = ContactDate(null, 1, 2),
        notes = "n",
        photoUri = null,
        starred = true,
        primaryRawContactId = primary,
        account = ContactAccount("me@example.org", "bitfire.at.davdroid")
    )

    @Test
    fun `toDraft keeps only values of the primary raw contact and the stored name`() {
        val draft = detail(10).toDraft()
        assertEquals("k", draft.lookupKey)
        assertEquals("", draft.name)
        assertEquals(listOf(phone("600111222")), draft.phones)
        assertTrue(draft.emails.isEmpty())
        assertEquals("Acme", draft.organization)
        assertEquals(ContactDate(null, 1, 2), draft.birthday)
        assertEquals(ContactAccount("me@example.org", "bitfire.at.davdroid"), draft.account)
    }

    @Test
    fun `data rows describe every editable field`() {
        val rows = ContactDataRows.of(
            ContactDraft(
                name = "Ana Lopez",
                phones = listOf(phone("600111222"), phone("1", LabelTypes.CUSTOM, "Gym")),
                emails = listOf(phone("a@b.c", LabelTypes.EMAIL_WORK)),
                birthday = ContactDate(1990, 5, 17),
                notes = "note",
                organization = "Acme"
            )
        )
        assertEquals(
            listOf(
                StructuredName.CONTENT_ITEM_TYPE,
                Phone.CONTENT_ITEM_TYPE,
                Phone.CONTENT_ITEM_TYPE,
                Email.CONTENT_ITEM_TYPE,
                Organization.CONTENT_ITEM_TYPE,
                Note.CONTENT_ITEM_TYPE,
                Event.CONTENT_ITEM_TYPE
            ),
            rows.map { it.mimeType }
        )
        assertEquals(mapOf(StructuredName.DISPLAY_NAME to "Ana Lopez"), rows[0].values)
        assertEquals(mapOf(Phone.NUMBER to "600111222", Phone.TYPE to LabelTypes.MOBILE), rows[1].values)
        assertEquals(mapOf(Phone.NUMBER to "1", Phone.TYPE to LabelTypes.CUSTOM, Phone.LABEL to "Gym"), rows[2].values)
        assertEquals(mapOf(Event.START_DATE to "1990-05-17", Event.TYPE to Event.TYPE_BIRTHDAY), rows[6].values)
    }

    @Test
    fun `empty optional fields produce no rows`() {
        val rows = ContactDataRows.of(ContactDraft(phones = listOf(phone("600111222"))))
        assertEquals(listOf(Phone.CONTENT_ITEM_TYPE), rows.map { it.mimeType })
    }

    @Test
    fun `replace selection has one placeholder per argument`() {
        val selection = ContactDataRows.replaceSelection()
        val args = ContactDataRows.replaceSelectionArgs(42)
        assertEquals(selection.count { it == '?' }, args.size)
        assertEquals("42", args.first())
    }
}
