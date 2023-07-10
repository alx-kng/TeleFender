package com.telefender.phone.data.default_database

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.telefender.phone.misc_helpers.DBL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object TestContacts {

    suspend fun printContactsTable(context: Context) {
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.LOOKUP_KEY,
            )

            try {
                val cur: Cursor = context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    projection,
                    null,
                    null,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
                )!!

                while (cur.moveToNext()) {
                    val id = cur.getString(0).toInt()
                    val name = cur.getString(1)
                    val key = cur.getString(2)

                    Timber.i("$DBL: TEST CONTACTS - " +
                        "id = $id | name = $name | key = $key")
                }

                cur.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Timber.i("$DBL: CONTACT RETRIEVAL FINISHED")
        }
    }

    suspend fun printRawContactsTable(context: Context) {
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.CONTACT_ID,
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.DELETED
            )

            try {
                val cur: Cursor = context.contentResolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    projection,
                    null,
                    null,
                    ContactsContract.RawContacts.ACCOUNT_NAME + " ASC"
                )!!

                while (cur.moveToNext()) {

                    Timber.i("$DBL: TEST RAW CONTACTS - " +
                        "id = ${cur.getString(0)} | contact_id = ${cur.getString(1)} | " +
                        "account_name = ${cur.getString(2)} | account_type = ${cur.getString(3)} | " +
                        "deleted = ${cur.getString(4)}")
                }

                cur.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun printContactDataTable(context: Context, contentResolver: ContentResolver) {
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.RAW_CONTACT_ID,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.DATA5,
            ContactsContract.Data.DATA6,
            ContactsContract.Data.DATA7,
            ContactsContract.Data.DATA8,
            ContactsContract.Data.DATA9,
            ContactsContract.Data.DATA10,
            ContactsContract.Data.DATA11,
            ContactsContract.Data.DATA12,
            ContactsContract.Data.DATA13,
            ContactsContract.Data.DATA14,
            ContactsContract.Data.DATA15,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
        )

        try {
            val cur: Cursor = contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.Data.MIMETYPE + " ASC"
            )!!

            while (cur.moveToNext()) {
                Timber.i("$DBL: TEST DATA - " +
                    "id = ${cur.getString(0)} | mime = ${cur.getString(1)} | " +
                    "raw_contact_ID = ${cur.getString(2)} | data1 = ${cur.getString(3)} | " +
                    "data2 = ${cur.getString(4)} | data3 = ${cur.getString(5)} | " +
                    "data4 = ${cur.getString(6)} | data5 = ${cur.getString(7)} | " +
                    "data6 = ${cur.getString(8)} | data7 = ${cur.getString(9)} | " +
                    "data8 = ${cur.getString(10)} | data9 = ${cur.getString(11)} | " +
                    "data10 = ${cur.getString(12)} | data11 = ${cur.getString(13)} | " +
                    "data12 = ${cur.getString(14)} | data13 = ${cur.getString(15)} | " +
                    "data14 = ${cur.getString(16)} | data15 = ${cur.getString(17)}")
            }

            cur.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}