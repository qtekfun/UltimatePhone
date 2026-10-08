package com.qtekfun.ultimatephone.core.contacts

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data

/** One row of the ContactsContract data table, as column name to value. */
data class DataRow(val mimeType: String, val values: Map<String, Any>)

/** Turns a validated draft into the data rows that describe it; kept free of ContentResolver so it can be unit tested. */
object ContactDataRows {
    /** The mime types a draft owns: when updating, all existing rows of these types are replaced. Other kinds (photo, address ...) are kept. */
    val EDITABLE_MIME_TYPES = listOf(
        StructuredName.CONTENT_ITEM_TYPE,
        Phone.CONTENT_ITEM_TYPE,
        Email.CONTENT_ITEM_TYPE,
        Organization.CONTENT_ITEM_TYPE,
        Note.CONTENT_ITEM_TYPE
    )

    fun of(draft: ContactDraft): List<DataRow> = buildList {
        if (draft.name.isNotBlank()) add(DataRow(StructuredName.CONTENT_ITEM_TYPE, mapOf(StructuredName.DISPLAY_NAME to draft.name)))
        draft.phones.forEach { add(DataRow(Phone.CONTENT_ITEM_TYPE, labeled(Phone.NUMBER, Phone.TYPE, Phone.LABEL, it))) }
        draft.emails.forEach { add(DataRow(Email.CONTENT_ITEM_TYPE, labeled(Email.ADDRESS, Email.TYPE, Email.LABEL, it))) }
        if (draft.organization.isNotBlank()) add(DataRow(Organization.CONTENT_ITEM_TYPE, mapOf(Organization.COMPANY to draft.organization)))
        if (draft.notes.isNotBlank()) add(DataRow(Note.CONTENT_ITEM_TYPE, mapOf(Note.NOTE to draft.notes)))
        draft.birthday?.let {
            add(DataRow(Event.CONTENT_ITEM_TYPE, mapOf(Event.START_DATE to it.format(), Event.TYPE to Event.TYPE_BIRTHDAY)))
        }
    }

    private fun labeled(valueColumn: String, typeColumn: String, labelColumn: String, item: LabeledValue): Map<String, Any> = buildMap {
        put(valueColumn, item.value)
        put(typeColumn, item.type)
        if (item.type == LabelTypes.CUSTOM && item.customLabel != null) put(labelColumn, item.customLabel)
    }

    /** Selection (with its `?` placeholders count) matching the rows of a raw contact that [of] regenerates. */
    fun replaceSelection(): String {
        val marks = EDITABLE_MIME_TYPES.joinToString(",") { "?" }
        return "${Data.RAW_CONTACT_ID}=? AND (${Data.MIMETYPE} IN ($marks) OR (${Data.MIMETYPE}=? AND ${Data.DATA2}=?))"
    }

    fun replaceSelectionArgs(rawContactId: Long): Array<String> =
        (listOf(rawContactId.toString()) + EDITABLE_MIME_TYPES + Event.CONTENT_ITEM_TYPE + Event.TYPE_BIRTHDAY.toString()).toTypedArray()
}
