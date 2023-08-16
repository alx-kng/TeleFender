package com.telefender.phone.data.default_database

import android.content.*
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.RawContacts
import androidx.core.database.getStringOrNull
import androidx.work.WorkInfo
import com.telefender.phone.App
import com.telefender.phone.data.server_related.debug_engine.command_subtypes.NumberUpdate
import com.telefender.phone.data.tele_database.background_tasks.ExperimentalWorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.data.tele_database.entities.Change
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.data.tele_database.entities.ChangeType
import com.telefender.phone.data.tele_database.entities.ContactNumber
import com.telefender.phone.gui.adapters.recycler_view_items.*
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList


/**
 * TODO: Switch cursors to use instead of close?
 *
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
     * TODO: Maybe don't do UI formatting here. This way, it can be used for both the View and
     *  Edit screens. OR, add a fifth list to [PackagedDataLists] so that we don't need to call this
     *  twice.
     *
     * TODO: Should we use the default value type in the rare case that the value type is null for
     *  Data that should have value type (e.g., number, email, address)?
     *
     * TODO: Check if we should change coroutine context for safety (like in getAggregateContacts())
     *
     * Returns two lists of all Data rows under the given [contactID] or [rawContactID] in the form
     * of [ContactData] depending on whether [rawContactID] is non-null. The first list is the
     * updated data list (almost formatted for UI) and the second list is the original data list
     * (formatted for comparison). Also returns a third list containing references to the
     * non-ContactData ChangeContactItems and a fourth list containing the fully UI formatted
     * updated data list.
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
    ) : PackagedDataLists {

        val shouldUseRawCID = rawContactID != null

        val updatedDataList = mutableListOf<ContactData>()
        val originalDataList = mutableListOf<ContactData>()

        // Adds primary name data to the lists if not querying under RawContactID.
        if (!shouldUseRawCID) {
            getContactPrimaryNameData(contentResolver, contactID)?.let {
                updatedDataList.add(it.first)
                updatedDataList.add(it.second)
                originalDataList.add(it.first)
                originalDataList.add(it.second)
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
            "${ContactsContract.Data.CONTACT_ID} = ? AND " +
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
            )

            while(cur?.moveToNext() == true) {
                val rawCID = cur.getString(0).toInt()
                val dataID = cur.getString(1).toInt()
                val mimeType = cur.getString(2).toContactDataMimeType()
                val value = cur.getStringOrNull(3) ?: ""

                /*
                Don't use default value type even if type happens to be null for some reason.
                Unexpected null value types will be handled in the adapter and in calculateCPOList().
                 */
                val valueType = cur.getStringOrNull(4)
                    ?.toIntOrNull()
                    ?.toContactDataValueType(mimeType)

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
                    value = value,
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

            cur?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        /*
        For ViewContactFragment / Adapter

        1. Converts updatedDataList into part of the viewFormattedList by filtering out it's name
         ContactData (which is currently segmented into first and last name if it exists) and
         converting the ContactData to ViewContactData.
        2. Adds non-ViewContactData items (like the headers and footers).
        3. Sorts view formatted list by ViewContactItem type, and then by MIME type, and then by
         Data rowID (slightly different from the way we sort the updated data list).
         */
        val viewFormattedList : MutableList<ViewContactItem> = updatedDataList
            .filter { it.mimeType != ContactDataMimeType.NAME }
            .map { ViewContactData(contactData = it) }
            .toMutableList()

        // ViewContactItems associated with the non-data parts of the UI.
        val nonViewContactDataItems = mutableListOf(
            ViewContactHeader(
                displayName = getContactDisplayName(
                    contentResolver = contentResolver,
                    contactID = contactID
                ),
                primaryNumber = updatedDataList
                    .find { it.mimeType == ContactDataMimeType.PHONE }
                    ?.value,
                primaryEmail = updatedDataList
                    .find { it.mimeType == ContactDataMimeType.EMAIL }
                    ?.value
            ),
            ViewContactFooter()
        )

        viewFormattedList.addAll(nonViewContactDataItems)
        viewFormattedList.sortWith(ViewContactItemComparator)

        /*
        For ChangeContactFragment / Adapter

        1. Casts ContactData list to ChangeContactItem list.
        2. Adds non-ContactData items (like the headers and blank edits).
        3. Sorts updated data list by MIME type, and then by ChangeContactItem type, and then by
         Data rowID if the item is a ContactData. This way, the list is in order to be displayed on
         the UI.

        NOTE: We don't need to sort the original data list since it's not used for the UI.
         */
        val castedUpdatedDataList : MutableList<ChangeContactItem> = updatedDataList
            .map { it.deepCopy() }
            .toMutableList()

        // ChangeContactItems associated with the non-data parts of the UI.
        val nonContactDataItems = mutableListOf(
            ChangeContactHeader(ContactDataMimeType.NAME),
            ChangeContactHeader(ContactDataMimeType.PHONE),
            ChangeContactAdder(ContactDataMimeType.PHONE),
            ChangeContactHeader(ContactDataMimeType.EMAIL),
            ChangeContactAdder(ContactDataMimeType.EMAIL),
            ChangeContactHeader(ContactDataMimeType.ADDRESS),
            ChangeContactAdder(ContactDataMimeType.ADDRESS),
            ChangeContactFooter(),
            ChangeContactDeleter()
        )

        castedUpdatedDataList.addAll(nonContactDataItems)

        val singleValueMimeTypes = ContactDataMimeType.values().filter { it.singleValue }
        for (mimeType in singleValueMimeTypes) {
            /*
            If there is already an existing ContactData with the single value mimeType, then don't
            add a new null-pair ContactData.
             */
            if (!castedUpdatedDataList.any { it.mimeType == mimeType && it is ContactData }) {
                when (mimeType) {
                    ContactDataMimeType.NAME -> {
                        castedUpdatedDataList.add(
                            ContactData.createNullPairContactData(
                                mimeType = mimeType,
                                columnInfo = Pair(1, ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)
                            )
                        )

                        castedUpdatedDataList.add(
                            ContactData.createNullPairContactData(
                                mimeType = mimeType,
                                columnInfo = Pair(2, ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)
                            )
                        )
                    }
                    else -> {
                        castedUpdatedDataList.add(
                            ContactData.createNullPairContactData(mimeType = mimeType)
                        )
                    }
                }
            }
        }

        castedUpdatedDataList.sortWith(ChangeContactItemComparator)

        return PackagedDataLists(
            originalUpdatedDataList = updatedDataList,
            updatedDataList = castedUpdatedDataList,
            originalDataList = originalDataList,
            nonContactDataList = nonContactDataItems,
            viewFormattedList = viewFormattedList
        )
    }

    /**
     * Returns the display name given the [contactID]. Note that the display name is not necessarily
     * associated with a Data row with name mime type. For example, if there is no name Data row,
     * but there is a phone Data row, then the phone number will be used as the display name.
     */
    private suspend fun getContactDisplayName(
        contentResolver: ContentResolver,
        contactID: String
    ) : String {
        val projection = arrayOf(
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        )

        val selection = "${ContactsContract.Contacts._ID} = ? "

        try {
            val cur = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                selection,
                arrayOf(contactID),
                null
            )
            if (cur?.moveToFirst() == true) {
                val displayName = cur.getStringOrNull(0) ?: ""
                cur.close()
                return displayName
            }

            cur?.close()
        } catch (e: Exception) {
            Timber.e("$DBL: getContactDisplayName() Error! ${e.message}")
            e.printStackTrace()
        }

        return ""
    }

    /**
     * Returns the primary name data row as two ContactData. One for the first name and another for
     * the last name. Name data is retrieved in a special manner, since we only want to retrieve the
     * primary (displayed) name data row.
     */
    private suspend fun getContactPrimaryNameData(
        contentResolver: ContentResolver,
        contactID: String
    ) : Pair<ContactData, ContactData>? {

        val primaryNameRawCID = getPrimaryNameRawCID(
            contentResolver = contentResolver,
            contactID = contactID
        ) ?: return null

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID,
            ContactsContract.CommonDataKinds.StructuredName._ID,
            ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
            ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
        )

        /**
         * In order for a name Data row to be the primary display row, the Data row itself must
         * be primary (IS_PRIMARY) within its RawContact, and its RawContact must be the source
         * of the primary display (RAW_CONTACT_ID must match NAME_RAW_CONTACT_ID in Contact row).
         */
        val selection =
            "${ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID} = ? AND " +
                "${ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID} = ? AND " +
                "${ContactsContract.Data.MIMETYPE} = ? "

        try {
            val cur = contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                arrayOf(
                    contactID,
                    primaryNameRawCID,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                ),
                null
            )

            if (cur?.moveToFirst() == true) {
                val sharedRowInfo = CompactRowInfo(
                    pairID = Pair(
                        first = cur.getString(0).toInt(),
                        second = cur.getString(1).toInt()
                    ),
                    valueType = null
                )

                val firstName = cur.getStringOrNull(2) ?: ""
                val lastName = cur.getStringOrNull(3) ?: ""

                val firstNameContactData = ContactData(
                    compactRowInfoList = mutableListOf(sharedRowInfo),
                    mimeType = ContactDataMimeType.NAME,
                    value = firstName,
                    columnInfo = Pair(1, ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)
                )

                val lastNameContactData = ContactData(
                    compactRowInfoList = mutableListOf(sharedRowInfo),
                    mimeType = ContactDataMimeType.NAME,
                    value = lastName,
                    columnInfo = Pair(2, ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)
                )

                cur.close()
                return Pair(firstNameContactData, lastNameContactData)
            }

            cur?.close()
        } catch (e: Exception) {
            Timber.e("$DBL: getContactPrimaryNameData() Error! ${e.message}")
            e.printStackTrace()
        }

        return null
    }

    private suspend fun getPrimaryNameRawCID(
        contentResolver: ContentResolver,
        contactID: String
    ) : String? {
        val projection = arrayOf(
            ContactsContract.Contacts.NAME_RAW_CONTACT_ID
        )

        val selection = "${ContactsContract.Contacts._ID} = ?"

        return contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            arrayOf(contactID),
            null
        )?.use {
            if (it.moveToFirst()) {
                it.getString(0)
            } else {
                null
            }
        }
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
     *      - [compactRowInfo] param CAN have null pairID (kinda same as bullet 1)
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
        columnInfo: Pair<Int, String>? = null
    ) {
        // New ContactData item with params. May or may not be added depending existing data.
        val newContactData = ContactData(
            compactRowInfoList = mutableListOf(compactRowInfo),
            mimeType = mimeType,
            value = value,
            columnInfo = columnInfo
        )

        // All ContactData with same data (compactRowInfoList will be different though).
        val existingContactDataList = this.filter {
            it.mimeType == mimeType && it.value == value && it.columnInfo == columnInfo
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
                        columnInfo = columnInfo
                    )
                )
            }
        }
    }

    /**
     * TODO: Document
     */
    suspend fun cleanUpdatedDataList(
        uncleanList: MutableList<ContactData>
    ) : List<ContactData> {
        val cleanList = mutableListOf<ContactData>()

        for (contactData in uncleanList) {
            for (compactRowInfo in contactData.compactRowInfoList) {
                cleanList.addToContactDataList(
                    compactRowInfo = compactRowInfo,
                    mimeType = contactData.mimeType,
                    value = contactData.value,
                    columnInfo = contactData.columnInfo
                )
            }
        }

        return cleanList
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
    suspend fun getContactNumberCursor(context: Context, contentResolver: ContentResolver): Cursor? {
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
     * Check if aggregate contact exists in default database given the aggregate Contact _ID (our
     * defaultCID).
     *
     * NOTE: If app doesn't have contact permissions, this code will throw an exception.
     */
    fun aggregateContactExists(
        contentResolver: ContentResolver,
        defaultCID: String
    ) : Boolean {
        val projection = arrayOf(
            ContactsContract.Contacts._ID
        )

        val selection = "${ContactsContract.Contacts._ID} = ?"

        val cur = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            arrayOf(defaultCID),
            null
        )

        val exists = cur != null && cur.moveToFirst()
        cur?.close()
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

        val cur = contentResolver.query(
            Phone.CONTENT_URI,
            projection,
            selection,
            arrayOf(defaultCID, rawNumber),
            null
        )

        Timber.e("$DBL: contactNumber: $rawNumber exists = ${cur != null && cur.moveToFirst()}")

        val exists = cur != null && cur.moveToFirst()
        cur?.close()
        return exists
    }

    /***********************************************************************************************
     * Default change queries. The following queries are used to actually modify the default
     * contacts. They are mostly used by the normal contact modification process, where the user
     * drives the changes.
     **********************************************************************************************/

    suspend fun deleteContact(
        context: Context,
        contentResolver: ContentResolver,
        aggregateCID: String?,
    ) : Boolean {

        val operations = ArrayList<ContentProviderOperation>()
        val underlyingRawCIDs = getRawCIDs(contentResolver, aggregateCID)

        for (rawCID in underlyingRawCIDs) {
            operations.add(
                getRawContactDeleteCPO(
                    rawContactID = rawCID
                )
            )
        }

        try {
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)

            /*
            Out of convenience, we don't do a mini-sync here, since the numbers added under the
            deleted contact are probably safe (spam-wise). Moreover, a full-sync will probably
            happen within the next 15 min. Note that if the number of Data rows affected
            (given by results[0].count) is 0, then the batch query delete failed.
             */
            val rowsAffected = results.getOrNull(0)?.count ?: 0
            if (rowsAffected == 0) {
                throw Exception("Default delete contact failed! No deleted rows!")
            }

            return true
        } catch (e: Exception) {
            Timber.e("$DBL: deleteContact() failed! ${e.message}")
            e.printStackTrace()

            return false
        }
    }

    /**
     * TODO: Should calculation AND execution of CPOs fall under the SYNC_CONTACTS Work? That way,
     *  the calculated CPOs won't have overlapped with any other sync process?
     *
     * 1. Gets the list of CPOs corresponding to the UI changes (given by the difference in
     *  [originalDataList] and [updatedDataList]).
     * 2. Executes the list of CPOs (which are the actual default row operations).
     * 3. Does a specialized mini-sync to update our Tele database with the new changes.
     * 4. Returns a Pair containing whether or not the whole process was successful and the newly
     *  inserted RawContact ID if it exists (for updates we return null).
     *
     * NOTE: If there were no changes in the UI, then don't call [executeChanges]. One way to tell
     * if there were changes is by comparing the original updatedDataList to the final clean
     * updatedDataList.
     *
     * NOTE: Requires that [updatedDataList] is clean.
     */
    suspend fun executeChanges(
        context: Context,
        contentResolver: ContentResolver,
        originalCID: String? = null,
        originalDataList: List<ContactData>,
        updatedDataList: List<ContactData>,
        accountName: String? = null,
        accountType: String? = null
    ) : Pair<Boolean, String?> {
        // Waits for any possible duplicate SYNC_CONTACTS processes to finish before continuing.
        val workInstanceKey = ExperimentalWorkStates.localizedCompete(
            workType = WorkType.SYNC_CONTACTS,
            runningMsg = "SYNC_CONTACTS - executeChanges()",
            newWorkInstance = true
        )

        try {
            /*
            If the originalDataList is empty, then the user must have added a completely new
            contact. This is used so we can retrieve the correct info from the batch results, which
            will be used in the mini-sync.
             */
            val isNewContact = originalDataList.isEmpty()
            val originalRawCIDs = getRawCIDs(contentResolver, originalCID)
            val operations = calculatedCPOList(
                originalDataList = originalDataList,
                updatedDataList = updatedDataList,
                accountName = accountName,
                accountType = accountType,
                originalRawCIDs = originalRawCIDs
            )

            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)

            var newRawCID: String? = null

            /*
            Does the corresponding mini-sync to update our Tele database. Note that even if the
            mini-sync fails, we still return that the whole process was successful, as the periodic
            full-sync will still eventually update our Tele database.
             */
            if (isNewContact) {
                /*
                If the user inserted a completely new contact, then use [insertMiniSync] to update
                our Tele database with the new changes. Note that if the newly created RawContact
                ID doesn't exist, then the batch query insert failed.
                 */
                newRawCID = results.getOrNull(0)?.uri
                    ?.let { ContentUris.parseId(it).toString() }
                    ?: throw Exception("Default insert contact failed! New rowID is null!")

                /*
                Execute the mini-sync, but don't let a sync failure mark the whole process as failed.
                 */
                try {
                    insertMiniSync(context, contentResolver, newRawCID)
                } catch (e: Exception) {
                    Timber.e("$DBL: insertMiniSync() failed! - ${e.stackTrace}")
                }

            } else {
                /*
                If the user edited an existing contact, then use [updateMiniSync] to update our
                Tele database with the new changes. Note that if the number of Data rows affected
                (given by results[0].count) is 0 AND no rows were added, then the batch query update
                failed.
                 */
                val rowAdded = results.getOrNull(0)?.uri
                val rowsAffected = results.getOrNull(0)?.count ?: 0
                if (rowAdded == null && rowsAffected == 0) {
                    throw Exception("Default update contact failed! No added / updated / deleted rows!")
                }

                /*
                Execute the mini-sync, but don't let a sync failure mark the whole process as failed.
                 */
                try {
                    updatedMiniSync(context, contentResolver, originalRawCIDs)
                } catch (e: Exception) {
                    Timber.e("$DBL: updatedMiniSync() failed! - ${e.stackTrace}")
                }
            }

            ExperimentalWorkStates.localizedRemoveState(
                workType = WorkType.SYNC_CONTACTS,
                workInstanceKey = workInstanceKey
            )
            return Pair(true, newRawCID)
        } catch (e: Exception) {
            Timber.e("$DBL: executeChanges() failed! ${e.message}")
            e.printStackTrace()

            ExperimentalWorkStates.localizedSetStateKey(
                workType = WorkType.SYNC_CONTACTS,
                workState = WorkInfo.State.FAILED,
                workInstanceKey = workInstanceKey
            )
            return Pair(false, null)
        }
    }

    /**
     * TODO: Do we even need mutexSync if we're going to use the competing SYNC_CONTACTS work?
     *  Make a decision.
     */
    private suspend fun insertMiniSync(
        context: Context,
        contentResolver: ContentResolver,
        rawContactID: String
    ) {
        val instanceNumber = TeleHelpers.getUserNumberStored(context)!!
        val database = (context.applicationContext as App).database
        val dataRows = getNumberDataUnderRawCID(contentResolver, rawContactID)

        /*
        The defaultCID and corresponding TeleCID should be shared by all Data rows under the same
        RawContact. These are null if there are no phone number Data rows.
         */
        val defaultCID = dataRows.firstOrNull()?.first
        val teleCID = defaultCID?.let {
            TeleHelpers.defaultCIDToTeleCID(it, instanceNumber)
        }

        // Insert one Contact row into our Tele database if phone number Data rows exist.
        if (defaultCID != null) {
            val changeID = UUID.randomUUID().toString()
            val changeTime = Instant.now().toEpochMilli()

            val change = Change.create(
                CID = teleCID
            )

            database.changeAgentDao().changeFromClient(
                ChangeLog.create(
                    changeID = changeID,
                    changeTime = changeTime,
                    type = ChangeType.CONTACT_INSERT,
                    instanceNumber = instanceNumber,
                    changeJson = change.toJson()
                ),
                fromSync = false,
                bubbleError = true
            )
        }

        // Insert ContactNumber row into our Tele database for each corresponding Data row.
        for (dataRow in dataRows) {
            val rawNumber = dataRow.second
            val normalizedNumber = TeleHelpers.normalizedNumber(rawNumber)
                ?: TeleHelpers.bareNumber(rawNumber)
            val versionNumber = dataRow.third

            val changeID = UUID.randomUUID().toString()
            val changeTime = Instant.now().toEpochMilli()

            val change = Change.create(
                CID = teleCID,
                normalizedNumber = normalizedNumber,
                defaultCID = defaultCID,
                rawNumber = rawNumber,
                degree = 0,
                counterValue = versionNumber
            )

            Timber.e("$DBL: insertMiniSync() - Inserting contact number! - $normalizedNumber")

            database.changeAgentDao().changeFromClient(
                ChangeLog.create(
                    changeID = changeID,
                    changeTime = changeTime,
                    type = ChangeType.CONTACT_NUMBER_INSERT,
                    instanceNumber = instanceNumber,
                    changeJson = change.toJson()
                ),
                fromSync = false,
                bubbleError = true
            )
        }
    }

    /**
     * TODO: Do we even need mutexSync if we're going to use the competing SYNC_CONTACTS work?
     *  Make a decision.
     */
    private suspend fun updatedMiniSync(
        context: Context,
        contentResolver: ContentResolver,
        originalRawCIDs: List<String>,
    ) {
        val instanceNumber = TeleHelpers.getUserNumberStored(context)!!
        val database = (context.applicationContext as App).database
        val aggregatedDataRows = mutableListOf<Triple<String, String, Int>>()
        val aggregatedMatchCIDs = mutableSetOf<ContactNumber>()

        /***************************************************************************************
         * Check for inserts
         **************************************************************************************/

        for (rawCID in originalRawCIDs) {
            val dataRows = getNumberDataUnderRawCID(contentResolver, rawCID)
            aggregatedDataRows.addAll(dataRows)

            /*
            The defaultCID and corresponding TeleCID should be shared by all Data rows under the
            same RawContact.
             */
            val defaultCID = getAggregateCIDFromRawCID(contentResolver, rawCID)
            val teleCID = defaultCID?.let {
                TeleHelpers.defaultCIDToTeleCID(it, instanceNumber)
            }

            // Corresponding contact numbers (by CID) in our database
            val matchCID: List<ContactNumber> = teleCID?.let {
                database.contactNumberDao().getContactNumbersByCID(it)
            } ?: listOf()
            aggregatedMatchCIDs.addAll(matchCID)

            /*
            Insert one Contact row into our Tele database if phone number Data rows exist and there
            is no existing Tele CID corresponding to the defaultCID. The only time this would ever
            happen is if the user initiated change somehow caused a split in an aggregate contact.
             */
            if (dataRows.isNotEmpty() && matchCID.isEmpty()) {
                val changeID = UUID.randomUUID().toString()
                val changeTime = Instant.now().toEpochMilli()

                val change = Change.create(
                    CID = teleCID
                )

                database.changeAgentDao().changeFromClient(
                    ChangeLog.create(
                        changeID = changeID,
                        changeTime = changeTime,
                        type = ChangeType.CONTACT_INSERT,
                        instanceNumber = instanceNumber,
                        changeJson = change.toJson()
                    ),
                    fromSync = false,
                    bubbleError = true
                )
            }

            for (dataRow in dataRows) {
                val rawNumber = dataRow.second
                val normalizedNumber = TeleHelpers.normalizedNumber(rawNumber)
                    ?: TeleHelpers.bareNumber(rawNumber)
                val versionNumber = dataRow.third

                // Corresponding contact numbers (by PK) in our database
                val matchPK: ContactNumber? = database.contactNumberDao().getContactNumberRow(
                    teleCID!!, // Non-null since dataRows is non-empty if the code reaches here.
                    normalizedNumber
                )

                /*
                If there already exists a ContactNumber in our database that corresponds to the
                Data row, then don't create a ChangeLog.
                 */
                if (matchPK != null) continue

                val changeID = UUID.randomUUID().toString()
                val changeTime = Instant.now().toEpochMilli()

                val change = Change.create(
                    CID = teleCID,
                    normalizedNumber = normalizedNumber,
                    defaultCID = defaultCID,
                    rawNumber = rawNumber,
                    degree = 0,
                    counterValue = versionNumber
                )

                Timber.e("$DBL: updatedMiniSync() - Adding contact number! - $normalizedNumber")

                database.changeAgentDao().changeFromClient(
                    ChangeLog.create(
                        changeID = changeID,
                        changeTime = changeTime,
                        type = ChangeType.CONTACT_NUMBER_INSERT,
                        instanceNumber = instanceNumber,
                        changeJson = change.toJson()
                    ),
                    fromSync = false,
                    bubbleError = true
                )
            }
        }

        /***************************************************************************************
         * Check for updates / deletes
         *
         * NOTE: We don't do any Contact deletes here, as they are not immediately necessary for the
         * algorithm and are a little cumbersome to implement. Moreover, the Contact deletes will
         * still eventually be taken care of by the periodic full-sync.
         **************************************************************************************/

        for (contactNumber in aggregatedMatchCIDs) {
            val matchDataRow = aggregatedDataRows.find {
                val dataRowNormalizedNum = TeleHelpers.normalizedNumber(it.second)
                    ?: TeleHelpers.bareNumber(it.second)

                it.first == contactNumber.defaultCID
                    && dataRowNormalizedNum == contactNumber.normalizedNumber
            }

            /*
            If no Data row under any of the original RawContacts has the same default CID and
            normalized number as the current contactNumber, then it means that the original Data
            row corresponding to the contactNumber was deleted, so we also delete the contactNumber.
             */
            if (matchDataRow == null) {
                val changeID = UUID.randomUUID().toString()
                val changeTime = Instant.now().toEpochMilli()

                val change = Change.create(
                    CID = contactNumber.CID,
                    normalizedNumber = contactNumber.normalizedNumber,
                    degree = 0
                )

                Timber.e("$DBL: updatedMiniSync() - Deleting contact number - ${contactNumber.normalizedNumber}!")

                database.changeAgentDao().changeFromClient(
                    ChangeLog.create(
                        changeID = changeID,
                        changeTime = changeTime,
                        type = ChangeType.CONTACT_NUMBER_DELETE,
                        instanceNumber = instanceNumber,
                        changeJson = change.toJson()
                    ),
                    fromSync = false,
                    bubbleError = true
                )

                continue
            }

            /*
            If there is a matching Data row, but the rawNumber is different, then update our
            ContactNumber with the new rawNumber and versionNumber.
             */
            if (matchDataRow.second != contactNumber.rawNumber) {
                val changeID = UUID.randomUUID().toString()
                val changeTime = Instant.now().toEpochMilli()

                val change = Change.create(
                    CID = contactNumber.CID,
                    normalizedNumber = contactNumber.normalizedNumber,
                    rawNumber = matchDataRow.second,
                    counterValue = matchDataRow.third
                )

                database.changeAgentDao().changeFromClient(
                    ChangeLog.create(
                        changeID = changeID,
                        changeTime = changeTime,
                        type = ChangeType.CONTACT_NUMBER_UPDATE,
                        instanceNumber = instanceNumber,
                        changeJson = change.toJson()
                    ),
                    fromSync = false,
                    bubbleError = true
                )
            }
        }
    }

    /**
     * Returns the rowIDs of the RawContact rows under the given [aggregateCID].
     */
    private fun getRawCIDs(
        contentResolver: ContentResolver,
        aggregateCID: String?
    ) : List<String> {
        if (aggregateCID == null) {
            return listOf()
        }

        val rawCIDList = mutableListOf<String>()

        val projection = arrayOf(
            RawContacts._ID
        )

        val selection = "${RawContacts.CONTACT_ID} = ? "

        try {
            val cur = contentResolver.query(
                RawContacts.CONTENT_URI,
                projection,
                selection,
                arrayOf(aggregateCID),
                null
            )

            while (cur?.moveToNext() == true) {
                rawCIDList.add(cur.getString(0))
            }

            cur?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return rawCIDList
    }

    fun getAggregateCIDFromRawCID(
        contentResolver: ContentResolver,
        rawContactID: String
    ) : String? {
        val projection = arrayOf(
            RawContacts.CONTACT_ID,
        )

        val selection = "${RawContacts._ID} = ? "

        try {
            val cur = contentResolver.query(
                RawContacts.CONTENT_URI,
                projection,
                selection,
                arrayOf(rawContactID),
                null
            )

            val aggregateCID = if (cur?.moveToFirst() == true) {
                cur.getString(0)
            } else {
                null
            }

            cur?.close()
            return aggregateCID
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * Returns the phone number Data rows under the given [rawContactID]. Return format is a list
     * of triples, where each triple represents a phone number Data row, with the first element as
     * the aggregate CID, the second element as the actual phone number, and the third element as
     * the data version int.
     */
    private fun getNumberDataUnderRawCID(
        contentResolver: ContentResolver,
        rawContactID: String
    ) : List<Triple<String, String, Int>> {
        val numberDataList = mutableListOf<Triple<String, String, Int>>()

        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.NUMBER,
            Phone.DATA_VERSION
        )

        val selection = "${Phone.RAW_CONTACT_ID} = ? "

        try {
            val cur = contentResolver.query(
                Phone.CONTENT_URI,
                projection,
                selection,
                arrayOf(rawContactID),
                null
            )

            while (cur?.moveToNext() == true) {
                numberDataList.add(
                    Triple(
                        first = cur.getString(0), // Aggregate CID
                        second = cur.getString(1), // Raw number
                        third = cur.getString(2).toInt() // Data version
                    )
                )
            }

            cur?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return numberDataList
    }

    /**
     * Gets CPO list from clean updated and original data lists.
     */
    private fun calculatedCPOList(
        originalDataList: List<ContactData>,
        updatedDataList: List<ContactData>,
        accountName: String?,
        accountType: String?,
        originalRawCIDs: List<String>
    ) : ArrayList<ContentProviderOperation> {

        val operations = ArrayList<ContentProviderOperation>()

        // If the originalDataList is empty, then the user must have added a completely new contact.
        val isNewContact = originalDataList.isEmpty()
        if (isNewContact) {
            operations.add(
                getRawContactInsertCPO(
                    accountName = accountName,
                    accountType = accountType
                )
            )
        }

        // Checks for row inserts and row updates.
        for (updatedData in updatedDataList) {
            if (updatedData.hasNullPairID()) {
                /*
                May be MOBILE, HOME, WORK, etc. if it exists (e.g., should be null for name data).
                Note that since the updated data has a CompactRowInfo with a null pairID, the first
                row info should be the only one (null pairID row infos are grouped separately).
                 */
                val valueTypeInt = updatedData.compactRowInfoList.first().valueType?.typeInt

                if (isNewContact) {
                    val cpo = when(updatedData.mimeType) {
                        ContactDataMimeType.NAME -> {
                            /*
                            Since we need to combine the two name ContactData together for the single
                            Data row insert, we combine on the first column ordered name ContactData
                            (corresponding to the GIVEN_NAME column).
                             */
                            if (updatedData.columnInfo?.first == 1) {
                                val otherData = updatedDataList.find {
                                    it.mimeType == ContactDataMimeType.NAME
                                        && it.columnInfo?.first == 2
                                }

                                getNameDataInsertCPO(
                                    firstName = updatedData.value,
                                    lastName = otherData?.value ?: ""
                                )
                            } else {
                                null
                            }
                        }
                        ContactDataMimeType.PHONE -> {
                            getNumberDataInsertCPO(
                                number = updatedData.value,
                                type = valueTypeInt!!
                            )
                        }
                        ContactDataMimeType.EMAIL -> {
                            getEmailDataInsertCPO(
                                email = updatedData.value,
                                type = valueTypeInt!!
                            )
                        }
                        ContactDataMimeType.ADDRESS -> {
                            getAddressDataInsertCPO(
                                address = updatedData.value,
                                type = valueTypeInt!!
                            )
                        }
                    }

                    cpo?.let { operations.add(it) }
                } else {
                    for (rawCID in originalRawCIDs) {
                        val cpo = when(updatedData.mimeType) {
                            ContactDataMimeType.NAME -> {
                                if (updatedData.columnInfo?.first == 1) {
                                    val otherData = updatedDataList.find {
                                        it.mimeType == ContactDataMimeType.NAME
                                            && it.columnInfo?.first == 2
                                    }

                                    getNameDataInsertCPO(
                                        firstName = updatedData.value,
                                        lastName = otherData?.value ?: "",
                                        rawContactID = rawCID
                                    )
                                } else {
                                    null
                                }
                            }
                            ContactDataMimeType.PHONE -> {
                                getNumberDataInsertCPO(
                                    number = updatedData.value,
                                    type = valueTypeInt!!,
                                    rawContactID = rawCID
                                )
                            }
                            ContactDataMimeType.EMAIL -> {
                                getEmailDataInsertCPO(
                                    email = updatedData.value,
                                    type = valueTypeInt!!,
                                    rawContactID = rawCID
                                )
                            }
                            ContactDataMimeType.ADDRESS -> {
                                getAddressDataInsertCPO(
                                    address = updatedData.value,
                                    type = valueTypeInt!!,
                                    rawContactID = rawCID
                                )
                            }
                        }

                        cpo?.let { operations.add(it) }
                    }
                }

                continue
            }

            /*
            If the code reaches here, then the updated data does not have a row info with null
            pairID and therefore only contains row infos for existing rows. This means that all of
            the contained row infos are also in the original data list (just with possibly different
            values and value types).
             */
            for (updatedRowInfo in updatedData.compactRowInfoList) {
                /*
                Finds the ContactData in the original data list that contains [updatedRowInfo] and
                has the same columnInfo. As mentioned above, we know it exists.
                 */
                val originalData = originalDataList.find { data ->
                    data.compactRowInfoList.find { it.pairID == updatedRowInfo.pairID } != null
                        && data.columnInfo == updatedData.columnInfo
                }!!

                val originalRowInfo = originalData.compactRowInfoList
                    .find { it.pairID == updatedRowInfo.pairID }!!

                // If the value or value type was changed, then add a corresponding update cpo.
                if (originalData.value != updatedData.value
                    || originalRowInfo.valueType != updatedRowInfo.valueType
                ) {
                    /*
                    Here's where we fix the row if the value type is null when it shouldn't, as we
                    just use the default value type.
                     */
                    val adjustedType = updatedRowInfo.valueType
                        ?: defaultValueType(updatedData.mimeType)

                    val valueDiff = originalData.value != updatedData.value
                    val typeDiff = originalRowInfo.valueType != adjustedType

                    val cpo = when(updatedData.mimeType) {
                        ContactDataMimeType.NAME -> {
                            getNameDataUpdateCPO_DataID(
                                newNamePart = updatedData.value,
                                column = updatedData.columnInfo?.second!!,
                                dataID = updatedRowInfo.getDataID().toString()
                            )
                        }
                        ContactDataMimeType.PHONE -> {
                            // Note that this should be non-null, as we already know there is a diff.
                            getNumberDataUpdateCPO_DataID(
                                newNumber = if (valueDiff) updatedData.value else null,
                                newType = if (typeDiff) adjustedType?.typeInt else null,
                                dataID = updatedRowInfo.getDataID().toString()
                            )!!
                        }
                        ContactDataMimeType.EMAIL -> {
                            // Note that this should be non-null, as we already know there is a diff.
                            getEmailDataUpdateCPO_DataID(
                                newEmail = if (valueDiff) updatedData.value else null,
                                newType = if (typeDiff) adjustedType?.typeInt else null,
                                dataID = updatedRowInfo.getDataID().toString()
                            )!!
                        }
                        ContactDataMimeType.ADDRESS -> {
                            getAddressDataUpdateCPO_DataID(
                                newAddress = if (valueDiff) updatedData.value else null,
                                newType = if (typeDiff) adjustedType?.typeInt else null,
                                dataID = updatedRowInfo.getDataID().toString()
                            )!!
                        }
                    }

                   operations.add(cpo)
                }
            }
        }

        for (originalData in originalDataList) {
            for (originalRowInfo in originalData.compactRowInfoList) {
                /*
                Finds the ContactData in the updated data list that contains [originalRowInfo] if it
                exists.
                 */
                val updatedData = updatedDataList.find { data ->
                    data.compactRowInfoList.find { it.pairID == originalRowInfo.pairID } != null
                }

                /*
                If there is no ContactData in the updated data list that contains [originalRowInfo],
                then it means that the row corresponding to [originalRowInfo] was deleted, so we
                add a delete CPO to the list of operations.
                 */
                if (updatedData == null) {
                    operations.add(
                        getDataDeleteCPO_DataID(dataID = originalRowInfo.getDataID().toString())
                    )
                }
            }
        }

        return operations
    }

    /**
     * TODO: REMOVE THIS
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
            val parts = name.split(" ", limit = 2)
            val firstName = parts.getOrElse(0) { "" }
            val lastName = parts.getOrElse(1) { "" }

            operations.add(
                getNameDataInsertCPO(
                    firstName = firstName,
                    lastName = lastName
                )
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
                getAddressDataInsertCPO(
                    address = address,
                    type = ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME
                )
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
            val parts = name.split(" ", limit = 2)
            val firstName = parts.getOrElse(0) { "" }
            val lastName = parts.getOrElse(1) { "" }

            operations.add(
                getNameDataInsertCPO(
                    firstName = firstName,
                    lastName = lastName
                )
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
     * Returns a ContentProviderOperation that insert a row into the RawContacts table with the
     * given [accountName] and [accountType].
     *
     * NOTE: [accountName] = null and [accountType] = null corresponds to the default phone account.
     */
    private fun getRawContactInsertCPO(
        accountName: String?,
        accountType: String?
    ) : ContentProviderOperation {
        return ContentProviderOperation
            .newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, accountName)
            .withValue(RawContacts.ACCOUNT_NAME, accountType)
            .build()
    }

    /**
     * Returns a ContentProviderOperation which deletes a row from the RawContacts table given the
     * [rawContactID].
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
     * Returns a ContentProviderOperation which deletes a row from the Data table given the [dataID].
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
     * Return a ContentProviderOperation which deletes all of the Data rows with number = [number]
     * that are linked to the RawContact specified by [rawContactID]. Should pass in non-empty
     * string for [number].
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
     * Returns ContentProviderOperation for name Data row insert.
     * If no explicit RawContact rowID is given, we assume that the CPO is being added to a batch
     * with a RawContact insert as the FIRST CPO in the batch, so we back reference the
     * RAW_CONTACT_ID column value to the first CPO result.
     */
    private fun getNameDataInsertCPO(
        firstName: String,
        lastName: String,
        rawContactID: String? = null
    ) : ContentProviderOperation {
        val cpo = ContentProviderOperation
            .newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            )
            .withValue(
                ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                firstName
            )
            .withValue(
                ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                lastName
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
     * Returns ContentProviderOperation for number Data row update. Requires Data rowID to update.
     *
     * NOTE: This only updates the specific Data row specified by [dataID].
     */
    private fun getNameDataUpdateCPO_DataID(
        newNamePart: String,
        column: String,
        dataID: String
    ) : ContentProviderOperation {
        return ContentProviderOperation
            .newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data._ID} = ?",
                arrayOf(dataID)
            )
            .withValue(
                column,
                newNamePart
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
        type: Int,
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
            .withValue(
                ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
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
     * for [newAddress]. Requires Data rowID to update.
     *
     * NOTE: This only updates the specific Data row specified by [dataID].
     */
    private fun getAddressDataUpdateCPO_DataID(
        newAddress: String? = null,
        newType: Int? = null,
        dataID: String
    ) : ContentProviderOperation? {
        if (newAddress == null && newType == null) {
            return null
        }

        val cpo = ContentProviderOperation
            .newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data._ID} = ?",
                arrayOf(dataID)
            )

        if (newAddress != null) {
            cpo.withValue(
                ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                newAddress
            )
        }

        if (newType != null) {
            cpo.withValue(
                ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                newType
            )
        }

        return cpo.build()
    }
}