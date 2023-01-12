package com.telefender.phone.data.tele_database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import com.telefender.phone.helpers.TeleHelpers


// TODO: Move this stuff to other files and maybe move CallDetail to TeleEntities.

/***************************************************************************************************
 * For RecentsFragment
 **************************************************************************************************/

// TODO: Make this also include normalized number for UI.
data class GroupedCallDetail(
    val rawNumber: String,
    var callEpochDate: Long,
    var callLocation: String?,
    val callDirection: Int,
    val unallowed: Boolean,
    var amount: Int,
    var firstEpochID: Long) {

    override fun toString() : String {
        return "rawNumber: $rawNumber callEpochDate: $callEpochDate" +
            " callLocation: $callLocation callDirection: ${TeleHelpers.getDirectionString(callDirection)}" +
            " unallowed: $unallowed"
    }
}

/***************************************************************************************************
 * For CallHistoryFragment
 **************************************************************************************************/

sealed interface CallDetailItem

object CallHistoryHeader : CallDetailItem

object CallHistoryFooter : CallDetailItem

/***************************************************************************************************
 * Actual database entities.
 **************************************************************************************************/

/**
 * TODO: actually make UI show from our CallLogs <--- Maybe to show blocked status and stuff
 * TODO: Make sure indices are right.
 *
 * [callDuration] is in seconds since Call object's connectTimeMillis.
 * [callEpochDate] is in milliseconds and corresponds to Call object's creationTimeMillis.
 *
 * Although [callType] seems to be redundant with [callDirection], having both has its indirect
 * uses. For example, [callType] is set to null in skeleton log insert (for unallowed calls) and
 * basically indicates whether or not the CallDetail is synced or not.
 */
@JsonClass(generateAdapter = true)
@Entity(tableName = "call_detail",
    indices = [
        Index(value = ["callEpochDate", "instanceNumber"]),
        Index(value = ["instanceNumber"])
    ]
)
data class CallDetail(
    @PrimaryKey(autoGenerate = true)
    val rowID: Long = 0,
    val rawNumber: String,
    val normalizedNumber: String,
    val callType: String?,
    val callEpochDate: Long,
    val callDuration: Long,
    val callLocation: String?,
    val callDirection: Int,
    val instanceNumber: String,
    val unallowed: Boolean = false
) : CallDetailItem {

    override fun toString() : String {
        return "rawNumber: $rawNumber callType: $callType callEpochDate: $callEpochDate callDuration: " +
            "$callDuration callLocation: $callLocation " +
            "callDirection: ${TeleHelpers.getDirectionString(callDirection)} " +
            "unallowed: $unallowed normalizedNumber: $normalizedNumber"
    }

    fun createGroup() : GroupedCallDetail {
        return GroupedCallDetail(
            rawNumber = rawNumber,
            callEpochDate = callEpochDate,
            callLocation = callLocation,
            callDirection = callDirection,
            unallowed = unallowed,
            amount = 1,
            firstEpochID = callEpochDate
        )
    }
}
