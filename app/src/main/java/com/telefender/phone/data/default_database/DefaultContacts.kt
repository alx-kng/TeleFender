package com.telefender.phone.data.default_database

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
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber


/***************************************************************************************************
 * TODO: Move these classes to another file.
 *
 * For ContactsFragment
 **************************************************************************************************/
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

object DefaultContacts {

    suspend fun getContactDetails(context: Context) : MutableList<ContactDetail> {
        return withContext(Dispatchers.IO) {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            )

            val cur: Cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                null,
                null,
                Phone.DISPLAY_NAME + " ASC"
            )!!

            val contacts : MutableList<ContactDetail> = mutableListOf()

            while (cur.moveToNext()) {
                val id = cur.getString(0).toInt()
                val name = cur.getString(1)

                val contact = ContactDetail(name, id)
                contacts.add(contact)
            }
            cur.close()

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: CONTACT RETRIEVAL FINISHED")
            return@withContext contacts
        }
    }

    /**
     * TODO: Look into whether using _ID is correct. That is, we don't want the id of a default
     *  contact to change, as our CID depends on it. <-- Think it is correct...
     *
     * Returns a cursor containing all aggregate column rows in Android's Contact table.
     *
     * NOTE: Make sure that calling function closes the cursor.
     */
    fun getContactCursor(contentResolver: ContentResolver): Cursor? {
        val projection = arrayOf(
            ContactsContract.Contacts._ID
        )
        var cur: Cursor? = null
        try {
            cur = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection, null, null, null
            )

            cur!!.moveToFirst()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cur
    }

    /**
     * TODO: Need to retrieve blocked status.
     * TODO: Consider using E164 representation (normalized) for number.
     *
     * Returns a cursor containing all numbers in Android's Phone table.
     * Also contains data_version column for syncing (probably not used anymore).
     *
     * NOTE: Make sure that calling function closes the cursor.
     */
    fun getContactNumberCursor(contentResolver: ContentResolver): Cursor? {
        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.NUMBER,
            Phone.NORMALIZED_NUMBER,
            Phone.DATA_VERSION
        )
        var cur: Cursor? = null
        try {
            cur = contentResolver.query(
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
     * TODO: Double check logic.
     *
     * Check if contact exists in default database given the _ID (our defaultCID).
     */
    fun contactExists(
        contentResolver: ContentResolver,
        defaultCID: String
    ) : Boolean {
        val projection = arrayOf(
            ContactsContract.Contacts._ID
        )

        val selection = "${ContactsContract.Contacts._ID} = ?"

        val curs = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            arrayOf(defaultCID),
            null
        )

        val exists = curs != null && curs.moveToFirst()
        curs?.close()
        return exists
    }

    /**
     *
     * Check if contact exists in default database given the _ID (our defaultCID) and number
     * (our rawNumber).
     */
    fun contactNumberExists(
        contentResolver: ContentResolver,
        defaultCID: String,
        rawNumber: String
    ) : Boolean {
        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.NUMBER,
        )

        val selection =
            "${Phone.CONTACT_ID} = ? AND " +
            "${Phone.NUMBER} = ?"

        val curs = contentResolver.query(
            Phone.CONTENT_URI,
            projection,
            selection,
            arrayOf(defaultCID, rawNumber),
            null
        )

        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: contactNumber: $rawNumber exists = ${curs != null && curs.moveToFirst()}")

        val exists = curs != null && curs.moveToFirst()
        curs?.close()
        return exists
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
        firstName: String?,
        mobileNumber: String?
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