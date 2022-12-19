package com.telefender.phone.data.tele_database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.telefender.phone.helpers.MiscHelpers

/***************************************************************************************************
 * For RecentsFragment
 **************************************************************************************************/

data class GroupedCallDetail(
    val number: String,
    val callType: String?,
    var callEpochDate: Long,
    var callLocation: String?,
    val callDirection: Int?,
    val unallowed: Boolean,
    var amount: Int,
    var firstEpochID: Long) {

    override fun toString() : String {
        return "number: $number callType: $callType callEpochDate: $callEpochDate" +
            " callLocation: $callLocation callDirection: ${MiscHelpers.getDirectionString(callDirection)}" +
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

//

/**
 * TODO: actually make UI show from our CallLogs <--- Maybe to show blocked status and stuff
 *
 * [callDuration] is in seconds since Call object's connectTimeMillis.
 * [callEpochDate] is in milliseconds and corresponds to Call object's creationTimeMillis.
 *
 * Although [callType] seems to be redundant with [callDirection], having both has its indirect
 * uses. For example, [callType] is set to null in skeleton log insert (for unallowed calls) and
 * basically indicates whether or not the CallDetail is synced or not.
 */
@Entity(tableName = "call_detail")
data class CallDetail(
    val number: String,
    val callType: String?,
    @PrimaryKey val callEpochDate: Long,
    val callDuration: Long?,
    val callLocation: String?,
    val callDirection: Int?,
    val unallowed: Boolean = false
) : CallDetailItem {

    override fun toString() : String {
        return "number: $number callType: $callType callEpochDate: $callEpochDate callDuration: " +
            "$callDuration callLocation: $callLocation " +
            "callDirection: ${MiscHelpers.getDirectionString(callDirection)} " +
            "unallowed: $unallowed"
    }

    fun createGroup() : GroupedCallDetail {
        return GroupedCallDetail(
            number,
            callType,
            callEpochDate,
            callLocation,
            callDirection,
            unallowed,
            1,
            callEpochDate
        )
    }
}

@Entity(tableName = "safe_log")
data class SafeLog(
    @PrimaryKey val number: String,
    val callEpochDate: Long
    ) {

    override fun toString(): String {
        return "number: $number callEpochDate: $callEpochDate"
    }
}

