package com.telefender.phone.data.default_database

import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.provider.ContactsContract.RawContacts
import com.telefender.phone.data.server_related.debug_engine.command_subtypes.NumberUpdate
import com.telefender.phone.gui.adapters.recycler_view_items.*
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber


/**
 * TODO: Consider changing this default database retrieval structure to a ContentProvider, but it
 *  may or may not be necessary seeing as that ContentProvider is often used to expose data to
 *  other applications. --> This prompt might not even make sense. Probably just do as normal.
 */
object DefaultContacts {

    /***********************************************************************************************
     * UI queries. The following queries are mostly used to drive the UI (e.g., ContactsFragment
     * and ChangeContactFragment).
     **********************************************************************************************/

    /**
     * Returns a list of all aggregate contacts. Read Android Learning Path for more on aggregate
     * contacts.
     */
    suspend fun getAggregateContacts(context: Context) : MutableList<AggregateContact> {
        return withContext(Dispatchers.IO) {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            )

            val contacts : MutableList<AggregateContact> = mutableListOf()

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

                    val contact = AggregateContact(name, id)
                    contacts.add(contact)
                }

                cur.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Timber.i("$DBL: CONTACT RETRIEVAL FINISHED")
            return@withContext contacts
        }
    }

    /**
     * TODO: Check if we should change coroutine context for safety (like in getAggregateContacts())
     *
     * Returns two lists of all Data rows under the given [contactID] or [rawContactID] in the form
     * of [ContactData] depending on whether [rawContactID] is non-null. The first list is the
     * updated data list (formatted for UI) and the second list is the original data list
     * (formatted for comparison).
     *
     * NOTE: The updated / original data lists are explained in more detail in Android - Default
     * Contact Change Protocol.
     *
     * NOTE: The updated data list is first organized by MIME_TYPE and then ordered by Data rowID.
     * This way, the list is formatted enough to be submitted to the RecyclerView adapter.
     *
     * NOTE: In ALL mode (querying by [contactID]), some data, like the name, is retrieved in a
     * special manner, as we only want to retrieve the primary (displayed) name data row.
     */
    suspend fun getContactData(
        contentResolver: ContentResolver,
        contactID: String,
        rawContactID: String? = null
    ) : Pair<MutableList<ContactData>, MutableList<ContactData>> {

        val shouldUseRawCID = rawContactID != null

        val updatedDataList = mutableListOf<ContactData>()
        val originalDataList = mutableListOf<ContactData>()

        // Adds primary name data to the lists if not querying under RawContactID.
        if (!shouldUseRawCID) {
            getContactPrimaryNameData(contentResolver, contactID)?.let {
                updatedDataList.add(it)
                originalDataList.add(it)
            }
        }

        /*
        DATA1 - usually stores the main value for each Data row
        DATA2 - usually stores the type of the main value (DATA1) if it exists (e.g., phone type
         MOBILE, WORK, HOME, etc.)
         */
        val projection = arrayOf(
            ContactsContract.Data.RAW_CONTACT_ID,
            ContactsContract.Data._ID,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2
        )

        /*
        If we're using the rawContactID, then we just query the Data rows with the given RawContact
        ID. If we're using the contactID, then we just query the Data rows with the given Contact
        ID; however, since we already retrieved the primary name Data row, we do not need to
        query the other name Data rows under the same Contact ID.
         */
        val selection = if (shouldUseRawCID) {
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? "
        } else {
            "${ContactsContract.Data.CONTACT_ID} = ? " +
                "${ContactsContract.Data.MIMETYPE} != ? "
        }

        val selectionArgs = if (shouldUseRawCID) {
            arrayOf(rawContactID)
        } else {
            arrayOf(
                contactID,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            )
        }

        try {
            val cur = contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                ContactsContract.Data._ID + " ASC"
            )!!

            while(cur.moveToNext()) {
                val rawCID = cur.getString(0).toInt()
                val dataID = cur.getString(1).toInt()
                val mimeType = cur.getString(2).toContactDataMimeType()
                val value = cur.getString(3)
                val valueType = cur.getString(4).toIntOrNull()?.toContactDataValueType(mimeType)

                /**
                 * For now, we are only dealing with the Data rows that have a MIME_TYPE listed in
                 * [ContactDataMimeType]. The others aren't added to the list so that they won't be
                 * displayed in the UI.
                 */
                if (mimeType == null) continue

                updatedDataList.addToContactDataList(
                    compactRowInfo = CompactRowInfo(
                        pairID = Pair(rawCID, dataID),
                        valueType = valueType
                    ),
                    mimeType = mimeType,
                    value = value
                )

                originalDataList.addToContactDataList(
                    forOriginalDataList = true,
                    compactRowInfo = CompactRowInfo(
                        pairID = Pair(rawCID, dataID),
                        valueType = valueType
                    ),
                    mimeType = mimeType,
                    value = value
                )
            }

            cur.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        /*
        Sorts updated data list by MIME type and then by Data rowID. This way, the list is in
        order to be displayed on the UI.

        NOTE: We don't need to sort the original data list since it's not used for the UI.
         */
        updatedDataList.sortWith(ContactDataComparator)

        return Pair(updatedDataList, originalDataList)
    }

    /**
     * Returns the primary name data row. Name data is retrieved in a special manner, since we
     * only want to retrieve the primary (displayed) name data row.
     */
    private suspend fun getContactPrimaryNameData(
        contentResolver: ContentResolver,
        contactID: String
    ) : ContactData? {

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID,
            ContactsContract.CommonDataKinds.StructuredName._ID,
            ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME
        )

        /**
         * In order for a name Data row to be the primary display row, the Data row itself must
         * be primary (IS_PRIMARY) within its RawContact, and its RawContact must be the source
         * of the primary display (RAW_CONTACT_ID must match NAME_RAW_CONTACT_ID in Contact row).
         */
        val selection =
            "${ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID} = ? AND " +
                "${ContactsContract.CommonDataKinds.StructuredName.IS_PRIMARY} = ? AND " +
                "${ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID} = ?"

        try {
            val cur = contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                arrayOf(
                    contactID,
                    selection,
                    ContactsContract.CommonDataKinds.StructuredName.NAME_RAW_CONTACT_ID
                ),
                null
            )

            cur?.moveToFirst()
            val contactData = cur?.let {
                ContactData(
                    compactRowInfoList = mutableListOf(
                        CompactRowInfo(
                            pairID = Pair(
                                first = it.getString(0).toInt(),
                                second = it.getString(1).toInt()
                            ),
                            valueType = null
                        )
                    ),
                    mimeType = ContactDataMimeType.NAME,
                    value = it.getString(2), // Actual name
                )
            }
            cur?.close()
            return contactData
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * Adds singular unwrapped ContactData to receiver list using the updated list procedure OR
     * the original list procedure depending on [forOriginalDataList] (read more in Android -
     * Default Contact Change Protocol). To be more specific, there are 3 cases where you would use
     * this function (pre-conditions are listed):
     *
     * - Adding to original data list on creation
     *      - Means that there are NO ContactData with null pairID
     *      - Duplicate ContactData (same value and RawCID) is allowed BUT must be in different item
     *      - [compactRowInfo] param CANNOT have null pairID
     *
     * - Adding to updated data list on creation
     *      - Means that there are NO ContactData with null pairID
     *      - Duplicate ContactData (same value and RawCID) is NOT allowed
     *      - [compactRowInfo] param CANNOT have null pairID
     *
     * - Adding to updated data list on clean
     *      - Means that there CAN BE ContactData with null pairID
     *      - Duplicate ContactData (same value and RawCID) is NOT allowed
     *      - [compactRowInfo] param CAN have null pairID
     *
     *
     * NOTE: If you're using this for the updated data list cleaning process. MAKE SURE THE RECEIVER
     * LIST IS THE NEW CLEAN LIST. This means that there cannot be any two ContactData items with
     * the same value (e.g., two different ContactData items with null pairIDs).
     *
     * NOTE: This should not be used when adding ContactData in the UI, as we don't allow duplicates
     * to both have null-pairs (for the purposes of cleaning). Instead, you should directly modify
     * the updated data list when making changes in the UI.
     *
     * NOTE: Singular ContactData means that there is only one compactRowInfo in compactRowInfoList.
     * This reflects not only adding Data row details when the updated data list is first created,
     * but also iterating by individual CompactRowInfos through the updated data list during the
     * cleaning process.
     */
    suspend fun MutableList<ContactData>.addToContactDataList(
        forOriginalDataList: Boolean = false,
        compactRowInfo: CompactRowInfo,
        mimeType: ContactDataMimeType,
        value: String,
    ) {
        // New ContactData item with params. May or may not be added depending existing data.
        val newContactData = ContactData(
            compactRowInfoList = mutableListOf(compactRowInfo),
            mimeType = mimeType,
            value = value,
        )

        // All ContactData with same data (compactRowInfoList will be different though).
        val existingContactDataList = this.filter {
            it.mimeType == mimeType && it.value == value
        }

        /*
        We need to find the lowest existing ContactData (by lowestDataID()) with the same MIME type
        and value, as the lowest existing ContactData is the one that will be displayed on the UI.
         */
        val lowestExistingContactData = existingContactDataList.minWithOrNull(ContactDataComparator)

        /*
        If no similar ContactData exists, append contactData param as new ContactData to list.

        NOTE: Checking if lowestExistingGroup is null is equivalent to checking if the
        existingGroups is empty, as there is no min. This null check statically useful to later code.
         */
        if (lowestExistingContactData == null) {
            this.add(newContactData)
            return
        }

        /*******************************************************************************************
         * This section is only for the updated data list cleaning process. The updated / original
         * data list creation processes will not be affected by this block since they won't have
         * ContactData with null pairIDs by pre-condition.
         ******************************************************************************************/

        /*
        If the similar ContactData group HAS null pairID (meaning that group was added in the UI by
        user) AND pairID is not null in compactRowInfo param, then replace the original null pairID
        ContactData with the contactData param.

        NOTE: This check will only ever be true during the cleaning process, and the pre-conditions
        of the receiver list are that duplicate ContactData (same value and RawCID) are NOT allowed
        whatsoever. Consequently, if there is a similar (same value) Contact group, then it is the
        ONLY similar Contact group.

        NOTE: We can just clear the original null pairID ContactData's compactRowInfoList before
        adding the compactRowInfo param, as null pairID ContactData only store one CompactRowInfo.
         */
        if (lowestExistingContactData.hasNullPairID() && compactRowInfo.pairID != null) {
            lowestExistingContactData.compactRowInfoList.clear()
            lowestExistingContactData.compactRowInfoList.add(compactRowInfo)
            return
        }

        /*
        If there is a similar ContactData group AND pairID is null in compactRowInfo param, then
        don't add the contactData param.

        NOTE: Even if the similar ContactData group has null pairID, we shouldn't replace with / add
        a ContactData that also has null pairID.
         */
        if (compactRowInfo.pairID == null) {
            return
        }

        /******************************************************************************************/

        /*
        Finds CompactRowInfo (from the lowest existing ContactData) with same the RawContact ID as
        contactRowInfo param if possible.

        NOTE: If the CompactRowInfo exists, then its pairID is definitely non-null. This is because
        the pre-condition for original / updated data list creation is that the receiver list cannot
        have ContactData with null pairID. Moreover, when cleaning an updated data list, we already
        check for null pairID in the previous two if checks.

        NOTE: Due to the pre-conditions of the receiver list, there can only be one existing
        CompactRowInfo (if any) with the same RawContact ID.
         */
        val existingCompactRowInfo = lowestExistingContactData.compactRowInfoList.find {
            it.getRawCID() == compactRowInfo.getRawCID()
        }

        if (existingCompactRowInfo == null) {
            /*
            If no CompactRowInfo with same RawContact ID, merge the data from contactData param into
            lowest existing ContactData.

            NOTE: Merging data just means adding the compactRowInfo param to the compactRowInfoList.
             */
            lowestExistingContactData.compactRowInfoList.add(compactRowInfo)
        } else if (existingCompactRowInfo.getDataID()!! < compactRowInfo.getDataID()!!) {
            /*
            If CompactRowInfo with same RawContact ID has LOWER Data ID AND we're adding to the
            original data list (forOriginalDataList = true), append contactData param as new
            ContactData to end of list. Otherwise, don't add the new ContactData since we're dealing
            with the updated data list (remember, no duplicates).
             */
            if (forOriginalDataList) {
                this.add(newContactData)
            }
        } else {
            /*
            If CompactRowInfo with same RawContact ID has HIGHER Data ID AND we're adding to the
            original data list (forOriginalDataList = true), replace the data in the lowest existing
            ContactData with the data from the contactData param and append a new ContactData with
            the OLD replaced data. Otherwise, just replace the data and don't add back the old data
            since we're dealing with the updated data list (remember, no duplicates).

            NOTE: Replacing data really just means replacing the old CompactRowInfo in the
            compactRowInfoList with the compactRowInfo param.
             */
            val replaceIndex = lowestExistingContactData.compactRowInfoList.indexOf(existingCompactRowInfo)
            lowestExistingContactData.compactRowInfoList[replaceIndex] = compactRowInfo
            if (forOriginalDataList) {
                this.add(
                    ContactData(
                        compactRowInfoList = mutableListOf(existingCompactRowInfo),
                        mimeType = mimeType,
                        value = value,
                    )
                )
            }
        }
    }

    fun cleanUpdatedDataList() : Unit {
        // TODO: Stub!
    }

    /***********************************************************************************************
     * Cursor retrievers. The following functions retrieve a cursor to a query result rather than
     * returning the entire query result as a list. This is helpful when iterating through a table,
     * like we do in the table synchronization process.
     **********************************************************************************************/

    /**
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
                projection,
                null,
                null,
                null
            )

            cur!!.moveToFirst()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cur
    }

    /**
     * TODO: We switched to using RAW_CONTACT_ID instead of CONTACT_ID since CONTACT_ID might
     *  change during aggregation. Double check if this is correct. -> It's not switch back to
     *  CONTACT_ID
     *
     * TODO: Need to retrieve blocked status.
     *
     * TODO: Need to check permissions here?
     *
     * Returns a cursor containing all numbers in Android's ContactsContract.Data table.
     * Also contains data_version column for syncing (probably not used anymore).
     *
     * NOTE: Make sure that calling function closes the cursor.
     */
    fun getContactNumberCursor(context: Context, contentResolver: ContentResolver): Cursor? {
        if (!TeleHelpers.hasValidStatus(context, setupRequired = false, contactRequired = true)) {
            Timber.e("$DBL: " +
                "No contact permissions in getContactNumberCursors()")

            return null
        }

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
                projection,
                null,
                null,
                null
            )
            cur!!.moveToFirst()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cur
    }

    /***********************************************************************************************
     * Existence queries. The following queries check the existence of a row in the database and
     * are mostly used for the table synchronization process.
     **********************************************************************************************/

    /**
     * TODO: Double check logic.
     *
     * Check if raw contact exists in default database given the RawContact _ID (our defaultCID).
     *
     * NOTE: If app doesn't have contact permissions, this code will throw an exception.
     */
    fun rawContactExists(
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
     * Check if contact exists in default database given the RawContact _ID (our defaultCID) and
     * number (our rawNumber). Under the cover, it checks for existence of row in Data table.
     *
     * NOTE: If app doesn't have contact permissions, this code will throw an exception.
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

        Timber.e("$DBL: contactNumber: $rawNumber exists = ${curs != null && curs.moveToFirst()}")

        val exists = curs != null && curs.moveToFirst()
        curs?.close()
        return exists
    }

    /***********************************************************************************************
     * Regular queries. The following queries are mostly used by the normal contact modification
     * process, where the user drives the changes.
     **********************************************************************************************/

    /**
     * TODO: PREDICT CHANGES AND START MINI TABLE SYNC FOR THOSE ROWS
     *
     * Inserts a contact with the given fields. Returns the rowID of the newly inserted RawContact
     * row if successful. At least one of the fields must be a non-empty string.
     */
    fun insertContact(
        contentResolver: ContentResolver,
        name: String,
        number: String,
        email: String,
        address: String
    ) : Long? {
        val operations = ArrayList<ContentProviderOperation>()

        operations.add(
            ContentProviderOperation
                .newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, null)
                .withValue(RawContacts.ACCOUNT_NAME, null)
                .build()
        )

        if (name != "") {
            operations.add(
                getNameDataInsertCPO(name = name)
            )
        }

        if (number != "") {
            operations.add(
                getNumberDataInsertCPO(
                    number = number,
                    type = Phone.TYPE_MOBILE
                )
            )
        }

        if (email != "") {
            operations.add(
                getEmailDataInsertCPO(
                    email = email,
                    type = ContactsContract.CommonDataKinds.Email.TYPE_MOBILE
                )
            )
        }

        if (email != "") {
            operations.add(
                getAddressDataInsertCPO(address = address)
            )
        }

        return try {
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            // Get the uri of the inserted raw contact
            val uri = results[0].uri
            // Get the id from the uri
            uri?.let { ContentUris.parseId(it) }
        } catch (e: Exception) {
            Timber.e("$DBL: insertContact() failed!")
            null
        }
    }

    /***********************************************************************************************
     * Debug console queries. The following queries are mostly used by the debug console to verify
     * that our own Tele database responds correctly to changes in the system.
     **********************************************************************************************/

    /**
     * TODO: PREDICT CHANGES AND START MINI TABLE SYNC FOR THOSE ROWS
     *
     * Inserts a contact with the given fields. Returns the rowID of the newly inserted RawContact
     * row if successful. At least one of the fields must be a non-empty string.
     *
     * NOTE: This is mostly used for the debug console.
     */
    fun debugInsertContact(
        contentResolver: ContentResolver,
        name: String?,
        numbers: List<String>?
    ) : Long? {
        val noNumbers = numbers == null || numbers.isEmpty()
        if (name == null && noNumbers) {
            return null
        }

        val operations = ArrayList<ContentProviderOperation>()

        operations.add(
            ContentProviderOperation
                .newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, null)
                .withValue(RawContacts.ACCOUNT_NAME, null)
                .build()
        )

        if (name != null && name != "") {
            operations.add(
                getNameDataInsertCPO(name = name)
            )
        }

        if (numbers != null) {
            for (number in numbers) {
                if (number != "") {
                    operations.add(
                        getNumberDataInsertCPO(
                            number = number,
                            type = Phone.TYPE_MOBILE
                        )
                    )
                }
            }
        }

        return try {
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            // Get the uri of the inserted raw contact
            val uri = results[0].uri
            // Get the id from the uri
            uri?.let { ContentUris.parseId(it) }
        } catch (e: Exception) {
            Timber.e("$DBL: debugInsertContact() failed!")
            null
        }
    }

    /**
     * TODO: PREDICT CHANGES AND START MINI TABLE SYNC FOR THOSE ROWS
     *
     * Inserts a number with the given fields into a RawContact specified by [rawContactID].
     * Returns whether the query was successful. If [numbers] is null or empty, we just return false.
     *
     * NOTE: This is mostly used for the debug console.
     */
    fun debugInsertNumber(
        contentResolver: ContentResolver,
        rawContactID: String,
        numbers: List<String>?
    ) : Boolean {
        if (numbers == null || numbers.isEmpty()) {
            return false
        }

        val operations = ArrayList<ContentProviderOperation>()

        for (number in numbers) {
            if (number != "") {
                operations.add(
                    getNumberDataInsertCPO(
                        number = number,
                        type = Phone.TYPE_MOBILE,
                        rawContactID = rawContactID
                    )
                )
            }
        }

        return try {
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            // Get the uri of the inserted raw contact
            val uri = results[0].uri
            // If uri is not null, then raw contact was successfully inserted.
            uri != null
        } catch (e: Exception) {
            Timber.e("$DBL: debugInsertNumber() failed!")
            false
        }
    }

    /**
     * TODO: PREDICT CHANGES AND START MINI TABLE SYNC FOR THOSE ROWS
     *
     * Updates numbers specified by [updates] in the RawContact with rowID = [rawContactID].
     * Returns whether the query was successful. If [updates] is null or empty, we just return false.
     *
     * NOTE: This is mostly used for the debug console.
     */
    fun debugUpdateNumber(
        contentResolver: ContentResolver,
        rawContactID: String,
        updates: List<NumberUpdate>?
    ) : Boolean {
        if (updates == null || updates.isEmpty()) {
            return false
        }

        val operations = ArrayList<ContentProviderOperation>()

        for (update in updates) {
            if (update.oldNumber != "" && update.newNumber != "") {
                operations.add(
                    getNumberDataUpdateCPO_RawCID(
                        oldNumber = update.oldNumber,
                        newNumber = update.newNumber,
                        rawContactID = rawContactID
                    )
                )
            }
        }

        return try {
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            // Get the number of data rows updated.
            val rowsUpdated = results[0].count
            /*
            If no rows were updated or the result is null, then either no updates were passed in or
            the update failed.
             */
            rowsUpdated?.let { it > 0 } ?: false
        } catch (e: Exception) {
            Timber.e("$DBL: debugUpdateNumber() failed!")
            false
        }
    }

    /**
     * TODO: PREDICT CHANGES AND START MINI TABLE SYNC FOR THOSE ROWS
     *
     * Deletes all of the Data rows with number in [numbers] that are linked to the RawContact
     * specified by [rawContactID]. Returns whether the query was successful.
     *
     * NOTE: This is mostly used for the debug console.
     */
    fun debugDeleteNumber(
        contentResolver: ContentResolver,
        rawContactID: String,
        numbers: List<String>?
    ) : Boolean {
        if (numbers == null || numbers.isEmpty()) {
            return false
        }

        val operations = ArrayList<ContentProviderOperation>()

        for (number in numbers) {
            if (number != "") {
                operations.add(
                    getDataDeleteCPO_Number(
                        rawContactID = rawContactID,
                        number = number
                    )
                )
            }
        }

        return try {
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            // Get the number of data rows affected (deleted).
            val rowsDeleted = results[0].count
            /*
            If no rows were deleted or the result is null, then the delete failed.
             */
            rowsDeleted?.let { it > 0 } ?: false
        } catch (e: Exception) {
            Timber.e("$DBL: debugDeleteNumber() failed!")
            false
        }
    }

    /**
     * TODO: PREDICT CHANGES AND START MINI TABLE SYNC FOR THOSE ROWS
     *
     * Deletes RawContact specified by [rawContactID]. Returns whether the query was successful.
     *
     * NOTE: This is mostly used for the debug console.
     */
    fun debugDeleteContact(
        contentResolver: ContentResolver,
        rawContactID: String
    ) : Boolean {
        val operations = ArrayList<ContentProviderOperation>()

        operations.add(
            getRawContactDeleteCPO(rawContactID = rawContactID)
        )

        return try {
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            // Get the number of data rows affected (deleted).
            val rowsDeleted = results[0].count
            /*
            If no rows were deleted or the result is null, then the delete failed.
             */
            rowsDeleted?.let { it > 0 } ?: false
        } catch (e: Exception) {
            Timber.e("$DBL: debugDeleteContact() failed!")
            false
        }
    }

    /***********************************************************************************************
     * ContentProviderOperation builders. Each returned ContentProviderOperation is the equivalent
     * of one row operation, whether it be an insert, update, or delete.
     **********************************************************************************************/

    /**
     * Deletes a row from the RawContacts table given the [rawContactID].
     *
     * NOTE: Deleting a RawContact automatically deletes the Data rows linked to it. Moreover,
     * once all of the RawContacts under an aggregated Contact are deleted, the aggregated Contact
     * is also deleted.
     */
    private fun getRawContactDeleteCPO(rawContactID: String) : ContentProviderOperation {
        return ContentProviderOperation
            .newDelete(RawContacts.CONTENT_URI)
            .withSelection(
                "${RawContacts._ID} = ?",
                arrayOf(rawContactID)
            )
            .build()
    }

    /**
     * Deletes a row from the Data table given the [dataID].
     */
    private fun getDataDeleteCPO_DataID(dataID: String) : ContentProviderOperation {
        return ContentProviderOperation
            .newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data._ID} = ?",
                arrayOf(dataID)
            )
            .build()
    }

    /**
     * Deletes all of the Data rows with number = [number] that are linked to the RawContact
     * specified by [rawContactID]. Should pass in non-empty string for [number].
     *
     * NOTE: This is mostly used for the debug console, as the normal contact delete process uses
     * specific Data rowIDs.
     */
    private fun getDataDeleteCPO_Number(
        rawContactID: String,
        number: String,
    ) : ContentProviderOperation {
        val selection =
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND " +
                "${Phone.NUMBER} = ? "

        return ContentProviderOperation
            .newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                selection,
                arrayOf(rawContactID, number)
            )
            .build()
    }

    /**
     * Returns ContentProviderOperation for name Data row insert. Should pass in non-empty string.
     * If no explicit RawContact rowID is given, we assume that the CPO is being added to a batch
     * with a RawContact insert as the FIRST CPO in the batch, so we back reference the
     * RAW_CONTACT_ID column value to the first CPO result.
     */
    private fun getNameDataInsertCPO(
        name: String,
        rawContactID: String? = null
    ) : ContentProviderOperation {
        val cpo = ContentProviderOperation
            .newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            )
            .withValue(
                ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                name
            )

        if (rawContactID != null) {
            cpo.withValue(
                ContactsContract.Data.RAW_CONTACT_ID,
                rawContactID
            )
        } else {
            cpo.withValueBackReference(
                ContactsContract.Data.RAW_CONTACT_ID,
                0
            )
        }

        return cpo.build()
    }

    /**
     * Returns ContentProviderOperation for number Data row update. Should pass in non-empty string
     * for [newName]. Requires Data rowID to update.
     *
     * NOTE: This only updates the specific Data row specified by [dataID].
     */
    private fun getNameDataUpdateCPO_DataID(
        newName: String,
        dataID: String
    ) : ContentProviderOperation {
        return ContentProviderOperation
            .newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data._ID} = ?",
                arrayOf(dataID)
            )
            .withValue(
                ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                newName
            )
            .build()
    }

    /**
     * Returns ContentProviderOperation for number Data row insert. Should pass in non-empty string.
     * If no explicit RawContact rowID is given, we assume that the CPO is being added to a batch
     * with a RawContact insert as the FIRST CPO in the batch, so we back reference the
     * RAW_CONTACT_ID column value to the first CPO result.
     */
    private fun getNumberDataInsertCPO(
        number: String,
        type: Int,
        rawContactID: String? = null
    ) : ContentProviderOperation {
        val cpo = ContentProviderOperation
            .newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(
                ContactsContract.Data.MIMETYPE,
                Phone.CONTENT_ITEM_TYPE
            )
            .withValue(
                Phone.NUMBER,
                number
            )
            .withValue(
                Phone.TYPE,
                type
            )

        if (rawContactID != null) {
            cpo.withValue(
                ContactsContract.Data.RAW_CONTACT_ID,
                rawContactID
            )
        } else {
            cpo.withValueBackReference(
                ContactsContract.Data.RAW_CONTACT_ID,
                0
            )
        }

        return cpo.build()
    }

    /**
     * Returns ContentProviderOperation for number Data row update. Should pass in non-empty string
     * for [newNumber] OR a valid type int for [newType]. Requires Data rowID to update.
     *
     * NOTE: This only updates the specific Data row specified by [dataID].
     */
    private fun getNumberDataUpdateCPO_DataID(
        newNumber: String? = null,
        newType: Int? = null,
        dataID: String
    ) : ContentProviderOperation? {
        if (newNumber == null && newType == null) {
            return null
        }

        val cpo = ContentProviderOperation
            .newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data._ID} = ?",
                arrayOf(dataID)
            )

        if (newNumber != null) {
            cpo.withValue(
                Phone.NUMBER,
                newNumber
            )
        }

        if (newType != null) {
            cpo.withValue(
                Phone.TYPE,
                newType
            )
        }

        return cpo.build()
    }

    /**
     * TODO: From to
     *
     * Returns ContentProviderOperation for number Data row update. Should pass in non-empty string
     * for [oldNumber] AND [newNumber]. Requires RawContact rowID to update.
     *
     * NOTE: This selects all of the Data rows under the RawContact specified by [rawContactID]
     * that have number = [oldNumber] and updates them to [newNumber].
     *
     * NOTE: This is mostly used for the debug console, as the normal contact update process uses
     * specific Data rowIDs.
     */
    private fun getNumberDataUpdateCPO_RawCID(
        oldNumber: String,
        newNumber: String,
        rawContactID: String
    ) : ContentProviderOperation {
        val selection =
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND " +
                "${Phone.NUMBER} = ? "

        val cpo = ContentProviderOperation
            .newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                selection,
                arrayOf(rawContactID, oldNumber)
            )
            .withValue(
                Phone.NUMBER,
                newNumber
            )

        return cpo.build()
    }

    /**
     * TODO: Maybe should make type optional.
     *
     * Returns ContentProviderOperation for email Data row insert. Should pass in non-empty string.
     * If no explicit RawContact rowID is given, we assume that the CPO is being added to a batch
     * with a RawContact insert as the FIRST CPO in the batch, so we back reference the
     * RAW_CONTACT_ID column value to the first CPO result.
     */
    private fun getEmailDataInsertCPO(
        email: String,
        type: Int,
        rawContactID: String? = null
    ) : ContentProviderOperation {
        val cpo = ContentProviderOperation
            .newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
            )
            .withValue(
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                email
            )
            .withValue(
                ContactsContract.CommonDataKinds.Email.TYPE,
                type
            )

        if (rawContactID != null) {
            cpo.withValue(
                ContactsContract.Data.RAW_CONTACT_ID,
                rawContactID
            )
        } else {
            cpo.withValueBackReference(
                ContactsContract.Data.RAW_CONTACT_ID,
                0
            )
        }

        return cpo.build()
    }

    /**
     * Returns ContentProviderOperation for number Data row update. Should pass in non-empty string
     * for [newEmail] OR a valid type int for [newType]. Requires Data rowID to update.
     *
     * NOTE: This only updates the specific Data row specified by [dataID].
     */
    private fun getEmailDataUpdateCPO_DataID(
        newEmail: String? = null,
        newType: Int? = null,
        dataID: String
    ) : ContentProviderOperation? {
        if (newEmail == null && newType == null) {
            return null
        }

        val cpo = ContentProviderOperation
            .newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data._ID} = ?",
                arrayOf(dataID)
            )

        if (newEmail != null) {
            cpo.withValue(
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                newEmail
            )
        }

        if (newType != null) {
            cpo.withValue(
                ContactsContract.CommonDataKinds.Email.TYPE,
                newType
            )
        }

        return cpo.build()
    }

    /**
     * Returns ContentProviderOperation for address Data row insert. Should pass in non-empty string.
     * If no explicit RawContact rowID is given, we assume that the CPO is being added to a batch
     * with a RawContact insert as the FIRST CPO in the batch, so we back reference the
     * RAW_CONTACT_ID column value to the first CPO result.
     */
    private fun getAddressDataInsertCPO(
        address: String,
        rawContactID: String? = null
    ) : ContentProviderOperation {
        val cpo = ContentProviderOperation
            .newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE
            )
            .withValue(
                ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                address
            )

        if (rawContactID != null) {
            cpo.withValue(
                ContactsContract.Data.RAW_CONTACT_ID,
                rawContactID
            )
        } else {
            cpo.withValueBackReference(
                ContactsContract.Data.RAW_CONTACT_ID,
                0
            )
        }

        return cpo.build()
    }

    /**
     * Returns ContentProviderOperation for number Data row update. Should pass in non-empty string
     * for [newAddress]. Requires Data rowID to update.
     *
     * NOTE: This only updates the specific Data row specified by [dataID].
     */
    private fun getAddressDataUpdateCPO_DataID(
        newAddress: String,
        dataID: String
    ) : ContentProviderOperation {
        return ContentProviderOperation
            .newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data._ID} = ?",
                arrayOf(dataID)
            )
            .withValue(
                ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                newAddress
            )
            .build()
    }

    /***********************************************************************************************
     * TODO: Old / unused functions. Once new infrastructure is established, remove these.
     **********************************************************************************************/

    /**
     * TODO: Make this cleaner and add more capability please...
     *
     * Inserts contact into default database.
     */
    fun insertContactOld(
        contactAdder: ContentResolver,
        firstName: String? = null,
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
                )
                .build()
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
                )
                .build()
        )
        try {
            contactAdder.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            return false
        }
        return true
    }

    /**
     * TODO: Make this cleaner and add more capability please... Currently, this delete function
     *  is like a sledge hammer doing an eye surgery (shouldn't delete all contacts by number).
     *
     * Deletes contact from default database.
     */
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

    /**
     * TODO: Not even sure what this is for? Please look into this...
     */
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
}