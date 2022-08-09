package com.dododial.phone.data.default_database

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.content.OperationApplicationException
import android.database.Cursor
import android.net.Uri
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.provider.ContactsContract.RawContacts
import android.provider.VoicemailContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception
import java.util.ArrayList

//data class Contacts(
//    val defaultCID: String,
//    val name: String
//)
//
//data class ContactNumbers(
//    val CID: String,
//    val number: String
//)

sealed interface ContactItem

object ContactFooter : ContactItem

data class Divider(
    val letter: String
) : ContactItem {
    override fun toString(): String {
        return "Divider: Letter = $letter"
    }

    override fun equals(other: Any?): Boolean {
        return other is Divider && this.letter == other.letter
    }

    override fun hashCode(): Int {
        return letter.hashCode()
    }
}

data class ContactDetail(
    val name : String,
    val id: Int
) : ContactItem {

    override fun toString(): String {
        return "ContactData: Name = $name, ID = $id"
    }

    override fun equals(other: Any?): Boolean {
        return other is ContactDetail && this.name == other.name && this.id == other.id
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + id
        return result
    }
}

object ContactHelper {

    suspend fun getContactDetails(context: Context) : MutableList<ContactDetail> {

        return withContext(Dispatchers.IO) {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            )

            val cur: Cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                null,
                null,
                Phone.DISPLAY_NAME + " ASC"
            )!!

            var contacts : MutableList<ContactDetail> = mutableListOf()

            while (cur.moveToNext()) {
                val id = cur.getString(0).toInt()
                val name = cur.getString(1)

                val contact = ContactDetail(name, id)
                contacts.add(contact)
            }
            cur.close()

            Log.i("DODODEBUG","CONTACT RETRIEVAL FINISHED")
            return@withContext contacts
        }
    }

    /**
     * Returns a cursor containing all numbers in Android's Phone table.
     * Also contains data_version column for syncing
     */
    fun getContactNumberCursor(
        contactHelper: ContentResolver
    ): Cursor? {
        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.NUMBER,
            Phone.DATA_VERSION
        )
        var cur: Cursor? = null
        try {
            cur = contactHelper.query(
                Phone.CONTENT_URI,
                projection, null, null, null
            )
            cur!!.moveToFirst()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cur
    }

    /**
     * Returns a cursor containing all aggregate column rows in Android's Contact table
     */
    fun getContactCursor(
        contactHelper: ContentResolver
    ): Cursor? {
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
        )
        var cur: Cursor? = null
        try {
            cur = contactHelper.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection, null, null, null
            )

            cur!!.moveToFirst()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cur
    }

    private fun getContactID(
        contactHelper: ContentResolver,
        number: String
    ): Long {
        val contactUri = Uri.withAppendedPath(
            PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        val projection = arrayOf(PhoneLookup._ID)
        var cursor: Cursor? = null
        try {
            cursor = contactHelper.query(
                contactUri, projection, null, null,
                null
            )
            if (cursor!!.moveToFirst()) {
                val personID = cursor.getColumnIndex(PhoneLookup._ID)
                return cursor.getLong(personID)
            }
            return -1
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (cursor != null) {
                cursor.close()
                cursor = null
            }
        }
        return -1
    }

    fun insertContact(
        contactAdder: ContentResolver,
        firstName: String?, mobileNumber: String?
    ): Boolean {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation
                .newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, null)
                .withValue(RawContacts.ACCOUNT_NAME, null)
                .build()
        )
        ops.add(
            ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                )
                .withValue(
                    ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                    firstName
                ).build()
        )
        ops.add(
            ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    Phone.CONTENT_ITEM_TYPE
                )
                .withValue(
                    Phone.NUMBER,
                    mobileNumber
                )
                .withValue(
                    Phone.TYPE,
                    Phone.TYPE_MOBILE
                ).build()
        )
        try {
            contactAdder.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            return false
        }
        return true
    }

    fun deleteContact(
        contactHelper: ContentResolver,
        number: String
    ) {
        val ops = ArrayList<ContentProviderOperation>()
        val args = arrayOf(
            getContactID(
                contactHelper, number
            ).toString()
        )
        ops.add(
            ContentProviderOperation.newDelete(RawContacts.CONTENT_URI)
                .withSelection(RawContacts.CONTACT_ID + "=?", args).build()
        )
        try {
            contactHelper.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: RemoteException) {
            e.printStackTrace()
        } catch (e: OperationApplicationException) {
            e.printStackTrace()
        }
    }
}