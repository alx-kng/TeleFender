package com.telefender.phone.gui.adapters.recycler_view_items

import android.provider.ContactsContract


/***************************************************************************************************
 * For ChangeContactFragment / ChangeContactAdapter
 **************************************************************************************************/

sealed interface ChangeContactItem

/**
 * Used to separate the different types of Data in adapter list.
 */
data class SectionHeader(val mimeType: String)

/**
 * Data rows from the database are converted into ContactData items, which are used by our contact
 * update procedure (read the Google Docs) to both drive the RecyclerView UI and find the required
 * database change operations.
 *
 * [compactRowInfoList] - List of [CompactRowInfo] associated with [value] (represents each actual row).
 * [mimeType] - MIME_TYPE of [value]
 * [value] - Actual value associated with Data row (e.g., number as 716-570-0686)
 *
 * NOTE: [compactRowInfoList] technically should never be empty. Moreover, [compactRowInfoList]
 */
data class ContactData(
    val compactRowInfoList: MutableList<CompactRowInfo>,
    val mimeType: ContactDataMimeType,
    var value: String,
) : ChangeContactItem {

    /**
     * Returns the lowest Data rowID from [compactRowInfoList] if possible.
     */
    fun lowestDataID() : Int? {
        return compactRowInfoList.minWithOrNull(CompactRowInfoComparator)?.getDataID()
    }

    /**
     * Returns whether [compactRowInfoList] contains a CompactRowInfo with a null pairID. If true,
     * this indicates that the entire ContactData was added by the user in the UI.
     */
    fun hasNullPairID() : Boolean {
        return compactRowInfoList.find { it.pairID == null } != null
    }

    /**
     * Returns the [CompactRowInfo] with the lowest Data rowID in [compactRowInfoList].
     * The returned [CompactRowInfo] is called primary because it will be used when displaying the
     * [ContactData] in the UI (particularly in ALL mode).
     *
     * NOTE: Although the return type is technically nullable, [primaryCompactInfo] should always
     * return a non-null [CompactRowInfo], as [compactRowInfoList] should never be empty (as
     * mentioned earlier).
     */
    fun primaryCompactInfo() : CompactRowInfo? {
        return compactRowInfoList.minWithOrNull(CompactRowInfoComparator)
    }

    override fun toString(): String {
        return "ContactData - { compactRowInfoList = $compactRowInfoList, mimeType = $mimeType, " +
            "value = $value }"
    }
}


/**
 * Auxiliary info from a Data row associated with a value (stored in ContactData). [CompactRowInfo]'s
 * purpose is to serve as a compact way to store row data that can easily be stored in a list
 * (like compactRowInfoList in ContactData).
 *
 * [pairID] - Pair of (RawContact rowID, Data rowID) associated with the value stored in ContactData.
 * [valueType] - Type of value stored in ContactData (e.g., MOBILE, HOME, or WORK type).
 *
 * NOTE: [pairID] is usually only set to null when the ContactData is created from the user adding
 * an item to the contact edit UI.
 */
data class CompactRowInfo(
    val pairID: Pair<Int, Int>?,
    val valueType: ContactDataValueType?
) {
    fun getRawCID() = pairID?.first
    fun getDataID() = pairID?.second

    override fun toString(): String {
        return if (pairID != null) {
            "{ (rawCID = ${pairID.first}, dataID = ${pairID.second}), valueType = $valueType }"
        } else {
            "{ null, valueType = $valueType } "
        }
    }
}

object CompactRowInfoComparator : Comparator<CompactRowInfo> {
    override fun compare (o1: CompactRowInfo, o2: CompactRowInfo) : Int {
        val o1DataID = o1.getDataID()
        val o2DataID = o2.getDataID()

        return if (o1DataID != null && o2DataID != null) {
            // Lower Data rowID goes first.
            o1DataID - o2DataID
        } else if (o1DataID == null && o2DataID == null) {
            // No preference over which goes first.
            0
        } else if (o1DataID == null){
            // Means that o2DataID != null, so o2 goes first.
            1
        } else {
            // Means that o1DataID != null, so o1 goes first.
            -1
        }
    }
}

/**
 * Comparator used for ordering ContactData first by MIME type (e.g., NAME, PHONE) and
 * then by pairIDs. The order of comparisons by MIME type is given by the top to bottom listing of
 * the enum class (implicitly given by the ordinal). The order of comparisons by pairIDs is given
 * by the lowest Data rowID within pairIDs.
 */
object ContactDataComparator : Comparator<ContactData> {
    override fun compare(o1: ContactData, o2: ContactData) : Int {
        // Only compare by lowestDataID() when MIME types are the same.
        if (o1.mimeType.ordinal != o2.mimeType.ordinal) {
            return o1.mimeType.ordinal - o2.mimeType.ordinal
        } else {
            val o1Lowest = o1.lowestDataID()
            val o2Lowest = o2.lowestDataID()

            return if (o1Lowest != null && o2Lowest != null) {
                // Lower Data rowID goes first.
                o1Lowest - o2Lowest
            } else if (o1Lowest == null && o2Lowest == null) {
                // No preference over which goes first.
                0
            } else if (o1Lowest == null){
                // Means that o2Lowest != null, so o2 goes first.
                1
            } else {
                // Means that o1Lowest != null, so o1 goes first.
                -1
            }
        }
    }
}

enum class ContactDataMimeType(val typeString: String) {
    NAME(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
    PHONE(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
    EMAIL(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE),
    ADDRESS(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
}

/**
 * Converts string to ContactDataMimeType if possible.
 */
fun String.toContactDataMimeType() : ContactDataMimeType? {
    for (mimeType in ContactDataMimeType.values()) {
        if (this == mimeType.typeString) {
            return mimeType
        }
    }

    return null
}

enum class ContactDataValueType(val mimeType: ContactDataMimeType, val typeInt: Int) {
    PHONE_TYPE_HOME(
        ContactDataMimeType.PHONE,
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME
    ),
    PHONE_TYPE_MOBILE(
        ContactDataMimeType.PHONE,
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
    ),
    PHONE_TYPE_WORK(
        ContactDataMimeType.PHONE,
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK
    ),
    PHONE_TYPE_OTHER(
        ContactDataMimeType.PHONE,
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
    ),
    EMAIL_TYPE_HOME(
        ContactDataMimeType.EMAIL,
        ContactsContract.CommonDataKinds.Email.TYPE_HOME
    ),
    EMAIL_TYPE_WORK(
        ContactDataMimeType.EMAIL,
        ContactsContract.CommonDataKinds.Email.TYPE_WORK
    ),
    EMAIL_TYPE_OTHER(
        ContactDataMimeType.EMAIL,
        ContactsContract.CommonDataKinds.Email.TYPE_OTHER
    ),
    EMAIL_TYPE_MOBILE(
        ContactDataMimeType.EMAIL,
        ContactsContract.CommonDataKinds.Email.TYPE_MOBILE
    ),
    ADDRESS_TYPE_HOME(
        ContactDataMimeType.ADDRESS,
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME
    ),
    ADDRESS_TYPE_WORK(
        ContactDataMimeType.ADDRESS,
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK
    ),
    ADDRESS_TYPE_OTHER(
        ContactDataMimeType.ADDRESS,
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER
    ),
}

/**
 * Converts int (given the MIME type) to ContactDataValueType if possible. Unlike other value to
 * enum converters, we still return an OTHER ContactDataValueType (specific to the MIME type) even
 * if the int doesn't match one of the typeInts listed in our enum class. This is for convenience,
 * as we currently don't want to add all of the other types from the default database.
 *
 * NOTE: Using one of the following MIME types will result in a null ContactDataValueType:
 *  - NAME
 */
fun Int.toContactDataValueType(mimeType: ContactDataMimeType?) : ContactDataValueType? {
    if (mimeType == null) return null

    for (valueType in ContactDataValueType.values()) {
        if (this == valueType.typeInt && mimeType == valueType.mimeType) {
            return valueType
        }
    }

    return when (mimeType) {
        ContactDataMimeType.PHONE -> ContactDataValueType.PHONE_TYPE_OTHER
        ContactDataMimeType.EMAIL -> ContactDataValueType.EMAIL_TYPE_OTHER
        ContactDataMimeType.ADDRESS -> ContactDataValueType.ADDRESS_TYPE_OTHER
        else -> null
    }
}