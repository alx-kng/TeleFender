package com.telefender.phone.gui.adapters.recycler_view_items

import android.view.View
import com.telefender.phone.misc_helpers.getUniqueLong
import java.util.Comparator


/***************************************************************************************************
 * For ViewContactFragment / ViewContactAdapter
 **************************************************************************************************/

sealed class ViewContactItem(
    val longUUID: Long = getUniqueLong()
)

object ViewContactItemComparator : Comparator<ViewContactItem> {
    override fun compare (o1: ViewContactItem, o2: ViewContactItem) : Int {
        // ViewContactHeader always goes first.
        if (o1 is ViewContactHeader) return -1
        if (o2 is ViewContactHeader) return 1

        // ViewContactFooter always goes last.
        if (o1 is ViewContactFooter) return 1
        if (o2 is ViewContactFooter) return -1

        // If the code reaches here, then o1 and o2 must be ViewContactData
        return ContactDataComparator.compare(
            o1 = (o1 as ViewContactData).contactData,
            o2 = (o2 as ViewContactData).contactData
        )
    }
}

/**
 * Contains the display name (and possibly picture) as well as the allowed actions for the contact
 * (e.g., call, message, etc.)
 */
data class ViewContactHeader(
    val displayName: String?,
    val primaryNumber: String?,
    val primaryEmail: String?
) : ViewContactItem()

/**
 * Wrapper for [ContactData]. Used in the ViewContactFragment.
 */
data class ViewContactData(
    val contactData: ContactData
) : ViewContactItem()

/**
 * Inserted at the bottom of the adapter list for some footer space in the RecyclerView.
 */
class ViewContactFooter : ViewContactItem() {

    override fun toString(): String {
        return "ViewContactFooter"
    }

    override fun equals(other: Any?): Boolean {
        return other is ViewContactFooter
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}