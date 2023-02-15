package com.telefender.phone.data.tele_database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass


// TODO: Probably store current block mode in StoredMap
@Entity(tableName = "stored_map")
data class StoredMap(
    @PrimaryKey val userNumber: String,
    val sessionID: String? = null,
    val clientKey: String? = null, // UUID key to push and pull changes to / from server
    val firebaseToken: String? = null,
    val lastLogSyncTime: Long = 0,
    val lastLogFullSyncTime: Long = 0, // First time the log sync process fully completes.
    val lastContactFullSyncTime: Long = 0, // First time the contact sync process fully completes.
    val lastServerRowID: Long? = null,
) {

    override fun toString() : String {
        return "STORED MAP - number: $userNumber sessionID: $sessionID clientKey: $clientKey" +
            " firebaseToken: $firebaseToken lastLogSyncTime: $lastLogSyncTime" +
            " lastServerRowID: $lastServerRowID"
    }
}

@JsonClass(generateAdapter = true)
@Entity(tableName = "parameters")
data class Parameters(
    @PrimaryKey val userNumber: String,
    val shouldUploadAnalyzed: Boolean,
    val shouldUploadLogs: Boolean,
    val initialNotifyGate: Int,
    val verifiedSpamNotifyGate: Int,
    val superSpamNotifyGate: Int,
    val incomingGate: Int, // inclusive seconds in order to let through
    val outgoingGate: Int, // inclusive seconds in order to let through
    val smsImmediateWaitTime: Long, // milliseconds before force move on to allow / unallow
    val smsDeferredWaitTime: Int, // seconds before sending another SMS request (if no earlier result)
)