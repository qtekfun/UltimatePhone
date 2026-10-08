package com.qtekfun.ultimatephone.spike

import android.annotation.SuppressLint
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName

/** Test 7 (contacts) and the call-log part of tests 5 and 6. Every line logged uses masked numbers. */
object DeviceProbes {
    private const val TEST_NAME = "UP Spike Test"
    private const val TEST_NUMBER = "+15555550100"
    private const val LAST_CALLS = 10

    @SuppressLint("MissingPermission")
    fun readCallLog(context: Context) {
        val args = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, LAST_CALLS)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        }
        val columns = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_ID,
            CallLog.Calls.BLOCK_REASON
        )
        try {
            context.contentResolver.query(CallLog.Calls.CONTENT_URI, columns, args, null)?.use { cursor ->
                EventLog.add("calllog", "rows=${cursor.count} (last $LAST_CALLS)")
                while (cursor.moveToNext()) {
                    EventLog.add(
                        "calllog",
                        "${PhoneMask.mask(cursor.getString(0))} ${CallNames.callLogType(cursor.getInt(1))} " +
                            "date=${cursor.getLong(2)} dur=${cursor.getInt(3)}s account=${cursor.getString(4)?.takeLast(ACCOUNT_TAIL)} " +
                            "blockReason=${cursor.getInt(5)}"
                    )
                }
            }
        } catch (e: SecurityException) {
            EventLog.add("calllog", "denied: ${e.message}")
        }
    }

    fun countContacts(context: Context) {
        try {
            context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts._ID), null, null, null)
                ?.use { EventLog.add("contacts", "contacts visible to the app: ${it.count}") }
        } catch (e: SecurityException) {
            EventLog.add("contacts", "denied: ${e.message}")
        }
    }

    @Suppress("TooGenericExceptionCaught") // The provider surfaces many failure types; all are test results.
    fun createTestContact(context: Context) {
        val ops = arrayListOf(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, TEST_NAME)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, TEST_NUMBER)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build()
        )
        try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            EventLog.add("contacts", "created '$TEST_NAME' in the local account; check the system Contacts app")
        } catch (e: Exception) {
            EventLog.add("contacts", "create FAILED ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Suppress("TooGenericExceptionCaught") // See createTestContact.
    fun deleteTestContacts(context: Context) {
        try {
            val ids = mutableListOf<Long>()
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.RAW_CONTACT_ID),
                "${ContactsContract.Data.MIMETYPE}=? AND ${StructuredName.DISPLAY_NAME}=?",
                arrayOf(StructuredName.CONTENT_ITEM_TYPE, TEST_NAME),
                null
            )?.use { while (it.moveToNext()) ids += it.getLong(0) }
            ids.forEach { context.contentResolver.delete(ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, it), null, null) }
            EventLog.add("contacts", "deleted ${ids.size} test contact(s)")
        } catch (e: Exception) {
            EventLog.add("contacts", "delete FAILED ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private const val ACCOUNT_TAIL = 6
}
