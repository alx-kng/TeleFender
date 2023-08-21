package com.telefender.phone.gui.adapters.recycler_view_items

import com.telefender.phone.misc_helpers.getUniqueLong
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.gui.adapters.recycler_view_items.common_types.SafetyStatus
import java.util.Comparator


/***************************************************************************************************
 * Mostly for CallHistory Fragment / Adapter. Parts are also used for NotifyList Fragment / Adapter.
 **************************************************************************************************/

sealed class CallHistoryItem(
    val longUUID: Long = getUniqueLong()
)

object CallHistoryItemComparator : Comparator<CallHistoryItem> {
    override fun compare (o1: CallHistoryItem, o2: CallHistoryItem) : Int {
        // CallHistoryHeader always goes first.
        if (o1 is CallHistoryHeader) return -1
        if (o2 is CallHistoryHeader) return 1

        // CallHistorySafetyStatus always goes after CallHistoryHeader
        if (o1 is CallHistorySafetyStatus) return -1
        if (o2 is CallHistorySafetyStatus) return 1

        // CallHistoryBlockedStatus always goes after CallHistoryHeader
        if (o1 is CallHistoryBlockedStatus) return -1
        if (o2 is CallHistoryBlockedStatus) return 1

        // CallHistorySelectTime always goes after Status items
        if (o1 is CallHistorySelectTime) return -1
        if (o2 is CallHistorySelectTime) return 1

        // CallHistoryFooter always goes last.
        if (o1 is CallHistoryFooter) return 1
        if (o2 is CallHistoryFooter) return -1

        /*
        If the code reaches here, then o1 and o2 must be CallHistoryData. In that case, compare the
        two objects by callEpochDate.
         */
        return (o1 as CallHistoryData).callDetail.callEpochDate.compareTo(
            (o2 as CallHistoryData).callDetail.callEpochDate
        )
    }
}

/**
 * Contains the display name (and possibly picture) as well as the allowed actions for the contact
 * (e.g., call, message, mark safe, block, etc.)
 */
data class CallHistoryHeader(
    val associatedNumber: String, // Number actually associated with the call log. Has to exist.
    val defaultCID: String?, // CID of top contact associated with number if it exists
    val displayName: String?, // Display name of contact if it exists
    val primaryEmail: String?, // Primary email of contact if it exists
) : CallHistoryItem()

/**
 * Contains the date of the call history selection.
 */
data class CallHistorySelectTime(
    val date: String
) : CallHistoryItem()

/**
 * Contains the button for blocking / unblocking a CONTACT.
 *
 * NOTE: Since a call history item is either associated with a contact or not associated with a
 * contact, either [CallHistoryBlockedStatus] or [CallHistorySafetyStatus] is in the adapter list.
 * That is, [CallHistoryBlockedStatus] and [CallHistorySafetyStatus] can't both be in the adapter
 * list for the same call history item.
 */
data class CallHistoryBlockedStatus(
    var isBlocked: Boolean
) : CallHistoryItem()

/**
 * Contains the buttons for marking a NON-CONTACT number as safe / default / spam
 *
 * NOTE: Since a call history item is either associated with a contact or not associated with a
 * contact, either [CallHistoryBlockedStatus] or [CallHistorySafetyStatus] is in the adapter list.
 * That is, [CallHistoryBlockedStatus] and [CallHistorySafetyStatus] can't both be in the adapter
 * list for the same call history item.
 */
data class CallHistorySafetyStatus(
    var safetyStatus: SafetyStatus
) : CallHistoryItem()

/**
 * Wrapper for [CallDetail]. Used in the CallHistoryFragment.
 */
data class CallHistoryData(
    val callDetail: CallDetail
) : CallHistoryItem()


/**
 * Inserted at the bottom of the adapter list for some footer space in the RecyclerView.
 */
class CallHistoryFooter : CallHistoryItem() {

    override fun toString(): String {
        return "CallHistoryFooter"
    }

    override fun equals(other: Any?): Boolean {
        return other is CallHistoryFooter
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
