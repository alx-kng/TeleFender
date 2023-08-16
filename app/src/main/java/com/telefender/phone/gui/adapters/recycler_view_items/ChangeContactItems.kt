package com.telefender.phone.gui.adapters.recycler_view_items

import android.provider.ContactsContract
import com.telefender.phone.misc_helpers.getUniqueLong
import java.util.*


/***************************************************************************************************
 * For ChangeContactFragment / ChangeContactAdapter
 **************************************************************************************************/

class PackagedDataLists(
    val originalUpdatedDataList: MutableList<ContactData>,
    val updatedDataList: MutableList<ChangeContactItem>,
    val originalDataList: MutableList<ContactData>,
    val nonContactDataList: List<ChangeContactItem>,
    val viewFormattedList: MutableList<ViewContactItem>
)

sealed class ChangeContactItem(
    open val mimeType: ContactDataMimeType,
    val longUUID: Long = getUniqueLong()
)

object ChangeContactItemComparator : Comparator<ChangeContactItem> {
    override fun compare (o1: ChangeContactItem, o2: ChangeContactItem) : Int {
        // Only compare by ChangeContactItem or finer when MIME types are the same.
        if (o1.mimeType.ordinal != o2.mimeType.ordinal) {
            return o1.mimeType.ordinal - o2.mimeType.ordinal
        } else {
            // ChangeContactHeader always goes first.
            if (o1 is ChangeContactHeader) return -1
            if (o2 is ChangeContactHeader) return 1

            // ChangeContactDeleter always goes last.
            if (o1 is ChangeContactDeleter) return 1
            if (o2 is ChangeContactDeleter) return -1

            // ChangeContactFooter always goes last, after the ChangeContactDeleter.
            if (o1 is ChangeContactFooter) return 1
            if (o2 is ChangeContactFooter) return -1

            // If not against a ChangeContactHeader or ChangeContactFooter, ChangeContactAdder always goes last.
            if (o1 is ChangeContactAdder) return 1
            if (o2 is ChangeContactAdder) return -1

            // If the code reaches here, then o1 and o2 must be ContactData
            return ContactDataComparator.compare(o1 as ContactData, o2 as ContactData)
        }
    }
}

/**
 * Used to separate the different types of Data in adapter list.
 */
data class ChangeContactHeader(
    override val mimeType: ContactDataMimeType,
) : ChangeContactItem(mimeType)

/**
 * Inserted at the bottom of the adapter list for some footer space in the RecyclerView.
 */
data class ChangeContactFooter(
    override val mimeType: ContactDataMimeType = lastContactDataMimeType(),
) : ChangeContactItem(mimeType)

/**
 * Inserted near the bottom of the adapter list to delete existing contacts.
 */
data class ChangeContactDeleter(
    override val mimeType: ContactDataMimeType = lastContactDataMimeType(),
) : ChangeContactItem(mimeType)

/**
 * Used ot add new ContactData in the adapter list
 */
data class ChangeContactAdder(
    override val mimeType: ContactDataMimeType,
) : ChangeContactItem(mimeType)

/**
 * TODO: Check if we need to implement custom equals? Currently think not since it compares by value.
 *
 * Data rows from the database are converted into ContactData items, which are used by our contact
 * update procedure (read the Google Docs) to both drive the RecyclerView UI and find the required
 * database change operations.
 *
 * [compactRowInfoList] - List of [CompactRowInfo] associated with [value] (represents each actual row).
 * [mimeType] - MIME_TYPE of [value]
 * [value] - Actual value associated with Data row (e.g., number as 716-570-0686).
 * [columnInfo] - Specifically for rows like name Data rows, where the first and last name from the
 *  same Data row will be split into two separate ContactData. The first element of the Pair
 *  represents the ordering of the ContactData (e.g., first name ContactData should go before
 *  last name ContactData). The second element is the actual column name string.
 *
 * NOTE: [compactRowInfoList] technically should never be empty. Moreover, [compactRowInfoList]
 */
data class ContactData(
    val compactRowInfoList: MutableList<CompactRowInfo>,
    override val mimeType: ContactDataMimeType,
    var value: String,
    val columnInfo: Pair<Int, String>? = null
) : ChangeContactItem(mimeType) {

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

    fun deepCopy() : ContactData {
        return ContactData(
            compactRowInfoList = compactRowInfoList.map { it.deepCopy() }.toMutableList(),
            mimeType = this.mimeType, // Assuming this is immutable
            value = this.value, // Assuming this is a primitive or immutable object
            columnInfo = this.columnInfo?.let { Pair(it.first, it.second) }
        )
    }

    override fun toString(): String {
        return "ContactData - { compactRowInfoList = $compactRowInfoList, mimeType = $mimeType, " +
            "value = $value, column = ${columnInfo?.second} }"
    }

    companion object {
        fun createNullPairContactData(
            mimeType: ContactDataMimeType,
            columnInfo: Pair<Int, String>? = null
        ) : ContactData {
            return ContactData(
                compactRowInfoList = mutableListOf(
                    CompactRowInfo(
                        pairID = null,
                        valueType = defaultValueType(mimeType)
                    )
                ),
                mimeType = mimeType,
                value = "",
                columnInfo = columnInfo
            )
        }
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

    fun deepCopy(): CompactRowInfo {
        return CompactRowInfo(
            pairID = this.pairID?.let { Pair(it.first, it.second) }, // Creating a new Pair instance
            valueType = this.valueType // Assuming this is immutable
        )
    }

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
                if (o1Lowest - o2Lowest == 0
                    && o1.columnInfo != null
                    && o2.columnInfo != null
                ) {
                    /*
                    If same lowest DataID, then probably from same Data row (e.g., name). In that
                    case, we use the column orderings if they exist.
                     */
                    o1.columnInfo.first - o2.columnInfo.first
                } else {
                    // Lower Data rowID goes first.
                    o1Lowest - o2Lowest
                }
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

enum class ContactDataMimeType(val typeString: String, val singleValue: Boolean = false) {
    NAME(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, true),
    PHONE(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
    EMAIL(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE),
    ADDRESS(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
}

/**
 * Gets the ContactDataMimeType that would be sorted to be last. Currently used to set the
 * ContactDataMimeType of the [ChangeContactFooter].
 */
fun lastContactDataMimeType() : ContactDataMimeType {
    return ContactDataMimeType.values().last()
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

enum class ContactDataValueType(
    val mimeType: ContactDataMimeType,
    val typeInt: Int,
    val displayString: String
    ) {
    PHONE_TYPE_HOME(
        mimeType = ContactDataMimeType.PHONE,
        typeInt = ContactsContract.CommonDataKinds.Phone.TYPE_HOME,
        displayString = "Home"
    ),
    PHONE_TYPE_MOBILE(
        mimeType = ContactDataMimeType.PHONE,
        typeInt = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
        displayString = "Mobile"
    ),
    PHONE_TYPE_WORK(
        mimeType = ContactDataMimeType.PHONE,
        typeInt = ContactsContract.CommonDataKinds.Phone.TYPE_WORK,
        displayString = "Work"
    ),
    PHONE_TYPE_OTHER(
        mimeType = ContactDataMimeType.PHONE,
        typeInt = ContactsContract.CommonDataKinds.Phone.TYPE_OTHER,
        displayString = "Other"
    ),
    EMAIL_TYPE_HOME(
        mimeType = ContactDataMimeType.EMAIL,
        typeInt = ContactsContract.CommonDataKinds.Email.TYPE_HOME,
        displayString = "Home"
    ),
    EMAIL_TYPE_WORK(
        mimeType = ContactDataMimeType.EMAIL,
        typeInt = ContactsContract.CommonDataKinds.Email.TYPE_WORK,
        displayString = "Work"
    ),
    EMAIL_TYPE_OTHER(
        ContactDataMimeType.EMAIL,
        ContactsContract.CommonDataKinds.Email.TYPE_OTHER,
        "Other"
    ),
    EMAIL_TYPE_MOBILE(
        mimeType = ContactDataMimeType.EMAIL,
        typeInt = ContactsContract.CommonDataKinds.Email.TYPE_MOBILE,
        displayString = "Mobile"
    ),
    ADDRESS_TYPE_HOME(
        mimeType = ContactDataMimeType.ADDRESS,
        typeInt = ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME,
        displayString = "Home"
    ),
    ADDRESS_TYPE_WORK(
        mimeType = ContactDataMimeType.ADDRESS,
        typeInt = ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK,
        displayString = "Work"
    ),
    ADDRESS_TYPE_OTHER(
        mimeType = ContactDataMimeType.ADDRESS,
        typeInt = ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER,
        displayString = "Other"
    ),
}

/**
 * Returns the default value type (e.g., MOBILE, HOME, etc) based off the [mimeType].
 */
fun defaultValueType(mimeType: ContactDataMimeType) : ContactDataValueType? {
    return when (mimeType) {
        ContactDataMimeType.NAME -> null
        ContactDataMimeType.PHONE -> ContactDataValueType.PHONE_TYPE_MOBILE
        ContactDataMimeType.EMAIL -> ContactDataValueType.EMAIL_TYPE_HOME
        ContactDataMimeType.ADDRESS -> ContactDataValueType.ADDRESS_TYPE_HOME
    }
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