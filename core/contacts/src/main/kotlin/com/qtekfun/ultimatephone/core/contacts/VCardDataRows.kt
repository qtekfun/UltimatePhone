package com.qtekfun.ultimatephone.core.contacts

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website

/** The data rows that create a contact from an imported vCard. Free of ContentResolver, so it is unit tested. */
object VCardDataRows {
    fun of(contact: VCardContact): List<DataRow> = buildList {
        val name = contact.name
        if (contact.displayName.isNotBlank()) {
            val values = HashMap<String, Any>()
            values[StructuredName.DISPLAY_NAME] = contact.displayName
            putIfNotBlank(values, StructuredName.GIVEN_NAME, name.given)
            putIfNotBlank(values, StructuredName.FAMILY_NAME, name.family)
            putIfNotBlank(values, StructuredName.MIDDLE_NAME, name.middle)
            putIfNotBlank(values, StructuredName.PREFIX, name.prefix)
            putIfNotBlank(values, StructuredName.SUFFIX, name.suffix)
            add(DataRow(StructuredName.CONTENT_ITEM_TYPE, values))
        }
        contact.nicknames.forEach { add(DataRow(Nickname.CONTENT_ITEM_TYPE, mapOf(Nickname.NAME to it, Nickname.TYPE to Nickname.TYPE_DEFAULT))) }
        if (contact.organization.isNotBlank() || contact.title.isNotBlank()) {
            val values = HashMap<String, Any>()
            putIfNotBlank(values, Organization.COMPANY, contact.organization)
            putIfNotBlank(values, Organization.TITLE, contact.title)
            add(DataRow(Organization.CONTENT_ITEM_TYPE, values))
        }
        contact.phones.forEach { add(DataRow(Phone.CONTENT_ITEM_TYPE, labeled(Phone.NUMBER, Phone.TYPE, Phone.LABEL, it))) }
        contact.emails.forEach { add(DataRow(Email.CONTENT_ITEM_TYPE, labeled(Email.ADDRESS, Email.TYPE, Email.LABEL, it))) }
        contact.websites.forEach { add(DataRow(Website.CONTENT_ITEM_TYPE, mapOf(Website.URL to it, Website.TYPE to Website.TYPE_HOMEPAGE))) }
        contact.addresses.forEach {
            add(DataRow(StructuredPostal.CONTENT_ITEM_TYPE, mapOf(StructuredPostal.FORMATTED_ADDRESS to it.formatted, StructuredPostal.TYPE to it.type)))
        }
        contact.birthday?.let {
            add(DataRow(Event.CONTENT_ITEM_TYPE, mapOf(Event.START_DATE to it.format(), Event.TYPE to Event.TYPE_BIRTHDAY)))
        }
        if (contact.note.isNotBlank()) add(DataRow(Note.CONTENT_ITEM_TYPE, mapOf(Note.NOTE to contact.note)))
        contact.photo?.let { add(DataRow(Photo.CONTENT_ITEM_TYPE, mapOf(Photo.PHOTO to it.bytes))) }
    }

    private fun putIfNotBlank(into: MutableMap<String, Any>, column: String, value: String) {
        if (value.isNotBlank()) into[column] = value
    }

    private fun labeled(valueColumn: String, typeColumn: String, labelColumn: String, item: LabeledValue): Map<String, Any> = buildMap {
        put(valueColumn, item.value)
        put(typeColumn, item.type)
        if (item.type == LabelTypes.CUSTOM && item.customLabel != null) put(labelColumn, item.customLabel)
    }
}
