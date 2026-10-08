package com.qtekfun.ultimatephone.core.contacts

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardDataRowsTest {
    @Test
    fun `an imported card becomes the matching data rows`() {
        val photo = byteArrayOf(1, 2, 3)
        val rows = VCardDataRows.of(
            VCardContact(
                name = VCardName(family = "García", given = "Ana", formatted = "Ana García"),
                organization = "ACME",
                title = "Boss",
                phones = listOf(LabeledValue("600111222", LabelTypes.MOBILE), LabeledValue("911", LabelTypes.CUSTOM, "Gran")),
                emails = listOf(LabeledValue("a@x.org", LabelTypes.EMAIL_WORK)),
                birthday = ContactDate(1990, 5, 17),
                note = "hi",
                photo = VCardPhoto(photo)
            )
        )
        val name = rows.single { it.mimeType == StructuredName.CONTENT_ITEM_TYPE }.values
        assertEquals("Ana García", name[StructuredName.DISPLAY_NAME])
        assertEquals("Ana", name[StructuredName.GIVEN_NAME])
        assertEquals("García", name[StructuredName.FAMILY_NAME])
        val phones = rows.filter { it.mimeType == Phone.CONTENT_ITEM_TYPE }
        assertEquals(2, phones.size)
        assertEquals("Gran", phones[1].values[Phone.LABEL])
        assertEquals(LabelTypes.EMAIL_WORK, rows.single { it.mimeType == Email.CONTENT_ITEM_TYPE }.values[Email.TYPE])
        val event = rows.single { it.mimeType == Event.CONTENT_ITEM_TYPE }.values
        assertEquals("1990-05-17", event[Event.START_DATE])
        assertEquals("hi", rows.single { it.mimeType == Note.CONTENT_ITEM_TYPE }.values[Note.NOTE])
        assertArrayEquals(photo, rows.single { it.mimeType == Photo.CONTENT_ITEM_TYPE }.values[Photo.PHOTO] as ByteArray)
    }

    @Test
    fun `empty parts produce no rows`() {
        assertTrue(VCardDataRows.of(VCardContact()).isEmpty())
    }
}
