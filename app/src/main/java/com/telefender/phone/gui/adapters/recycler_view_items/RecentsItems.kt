package com.telefender.phone.gui.adapters.recycler_view_items

import com.telefender.phone.misc_helpers.TeleHelpers


/***************************************************************************************************
 * For RecentsFragment / RecentsAdapter
 **************************************************************************************************/

// TODO: Make this also include normalized number for UI.
data class RecentsGroupedCallDetail(
    var name: String,
    val normalizedNumber: String,
    val rawNumber: String,
    var callEpochDate: Long,
    var callLocation: String?,
    val callDirection: Int,
    val unallowed: Boolean,
    var amount: Int,
    var firstEpochID: Long
) {

    override fun toString() : String {
        return "name: $name, normalizedNumber: $normalizedNumber, rawNumber: $rawNumber " +
            "callEpochDate: $callEpochDate callLocation: $callLocation " +
            "callDirection: ${TeleHelpers.getDirectionString(callDirection)} unallowed: $unallowed"
    }
}