package com.telefender.phone.data.tele_database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/***************************************************************************************************
 * For RecentsFragment
 **************************************************************************************************/

data class GroupedCallDetail(
    val number: String,
    val callType: String?,
    var callEpochDate: Long,
    var callLocation: String?,
    val callDirection: String?,
    val unallowed: Boolean,
    var amount: Int,
    var firstEpochID: Long) {

    override fun toString() : String {
        return "number: " + number + " callType: " + callType + " callEpochDate: " +
            callEpochDate + " callLocation: " + callLocation + " callDirection: " +
            callDirection + " amount: " + amount + " firstEpochID: " + firstEpochID
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

// TODO actually make UI show from our CallLogs
@Entity(tableName = "call_detail")
data class CallDetail(
    val number: String,
    val callType: String?,
    @PrimaryKey val callEpochDate: Long,
    val callDuration: Long?,
    val callLocation: String?,
    val callDirection: String?,
    val unallowed: Boolean = false
) : CallDetailItem {

    override fun toString() : String {
        return "number: " + this.number + " callType: " + this.callType + " callEpochDate: " +
            this.callEpochDate + " callDuration: " + this.callDuration + " callLocation: " +
            this.callLocation + " callDirection: " + this.callDirection + " unallowed: " + this.unallowed
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

