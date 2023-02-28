package com.telefender.phone.data.tele_database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi


// TODO: Probably store current block mode in StoredMap
@JsonClass(generateAdapter = true)
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
) : TableEntity() {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(StoredMap::class.java)
        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "STORED MAP - number: $userNumber sessionID: $sessionID clientKey: $clientKey" +
            " firebaseToken: $firebaseToken lastLogSyncTime: $lastLogSyncTime" +
            " lastServerRowID: $lastServerRowID"
    }
}

@JsonClass(generateAdapter = true)
@Entity(tableName = "parameters")
data class ParametersWrapper(
    @PrimaryKey val userNumber: String,
    val parametersJson: String,
) : TableEntity() {

    /**
     * Retrieves Parameters object version of parametersJson, but SHOULD ONLY BE USED for
     * ParametersWrapper retrieved from the database, and NOT for freshly initialized objects.
     * (We're assuming analyzedJson is a valid Json string due to the initialization process).
     */
    fun getParameters() : Parameters {
        return parametersJson.toParameters()!!
    }

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(ParametersWrapper::class.java)
        return adapter.serializeNulls().toJson(this)
    }
}

/**
 * Used to store most Parameters fields and is stored in ParametersWrapper as JSON.
 */
@JsonClass(generateAdapter = true)
data class Parameters(
    val shouldUploadAnalyzed: Boolean,
    val shouldUploadLogs: Boolean,

    val initialNotifyGate: Int,
    val verifiedSpamNotifyGate: Int,
    val superSpamNotifyGate: Int,
    val seenGateIncrease: Int, // How much the notify gate increases by when user sees notify item.

    val notifyWindowSize: Int, // Size of notify window in days (controls notify list qualification).
    val initialLastCallDropWindow: Int, // Initial # of days after last call to auto-remove notify item.
    val seenWindowDecrease: Int, // How much lastCallDropWindow decreases each time the item is seen.
    val qualifiedDropWindow: Int,
    val seenDropWindow: Int,

    val incomingGate: Int, // inclusive seconds in order to let through
    val outgoingGate: Int, // inclusive seconds in order to let through

    val smsImmediateWaitTime: Long, // milliseconds before force move on to allow / unallow
    val smsDeferredWaitTime: Int, // seconds before sending another SMS request (if no earlier result)
) {

    fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(Parameters::class.java)
        return adapter.serializeNulls().toJson(this)
    }
}

/**
 * Converts JSON string to Parameters object.
 * Note: Need to put try-catch around any sort of Moshi string-to-object function.
 */
fun String.toParameters() : Parameters? {
    return try {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(Parameters::class.java)

        adapter.serializeNulls().fromJson(this)
    } catch (e: Exception) {
        null
    }
}

/**
 * Returns whether a string is a valid Parameters JSON string.
 */
fun String.isValidParameters() : Boolean {
    return this.toParameters() != null
}