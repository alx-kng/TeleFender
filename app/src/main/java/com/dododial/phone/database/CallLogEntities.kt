package com.dododial.phone.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_log")
data class CallLog(
    val number: String,
    val callType: String,
    val callEpochDate: String,
    val callDuration: String,
    val callLocation: String?,
    val callDirection: String?) {
    @PrimaryKey(autoGenerate = true)
    var logID: Int = 0
}