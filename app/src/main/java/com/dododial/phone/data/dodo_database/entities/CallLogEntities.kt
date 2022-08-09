package com.dododial.phone.data.dodo_database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_log")
data class CallLog(
    val number: String,
    val callType: String,
    val callEpochDate: Long,
    val callDuration: String,
    val callLocation: String?,
    val callDirection: String?) {
    @PrimaryKey(autoGenerate = true)
    var logID: Int = 0
    
    override fun toString() : String {
        return "number: " + this.number + " callType: " + this.callType + " callEpochDate: " +
            this.callEpochDate + " callDuration: " + this.callDuration + " callLocation: " +
            this.callLocation + " callDirection: " + this.callDirection + " logID: " + this.logID 
    }
}
