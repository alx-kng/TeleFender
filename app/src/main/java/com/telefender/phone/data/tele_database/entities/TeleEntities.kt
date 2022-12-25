package com.telefender.phone.data.tele_database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


// TODO: Probably store current block mode in StoredMap
@Entity(tableName = "stored_map")
data class StoredMap(
    @PrimaryKey val userNumber: String,
    val sessionID: String? = null,
    val clientKey: String? = null,
    val fireBaseToken: String? = null,
    val databaseInitialized: Boolean = false,
    val lastSyncTime: Long = 0
) {

    override fun toString() : String {
        return "STORED MAP - number" + this.userNumber + " sessionID: " + this.sessionID + " clientKey: " + this.clientKey + " fireBaseToken: " + this.fireBaseToken
    }
}

@Entity(tableName = "instance")
data class Instance(
    @PrimaryKey val number: String) {

    override fun toString() : String {
        return "INSTANCE - number: " + this.number
    }
}

// TODO: Make sure that nothing uses the Cascade delete in its calculations.
// TODO: Should we add defaultCID column to Contact table?
@Entity(tableName = "contact",
    foreignKeys = [ForeignKey(
        entity = Instance::class,
        parentColumns = arrayOf("number"),
        childColumns = arrayOf("instanceNumber"),
        onDelete = ForeignKey.NO_ACTION
    )],
    indices = [Index(value = ["instanceNumber"])]
)
data class Contact(
    @PrimaryKey val CID: String,
    val instanceNumber : String,
    val blocked: Boolean = false) {

    override fun toString() : String {
        return "CONTACT -  CID: $CID instanceNumber: $instanceNumber blocked: $blocked"
    }
}

/*
TODO: Finish adding instance number column.
TODO: Implement changes with cleanNumber and rawNumber. cleanNumber used for AnalyzedNumber (that
 way the tree numbers are assimilated in the same row).
 */
@Entity(tableName = "contact_number",
    primaryKeys = ["CID", "cleanNumber"],
    foreignKeys = [
        ForeignKey(
            entity = Contact::class,
            parentColumns = arrayOf("CID"),
            childColumns = arrayOf("CID"),
            onDelete = ForeignKey.NO_ACTION
            ),
        ForeignKey(
            entity = Instance::class,
            parentColumns = arrayOf("number"),
            childColumns = arrayOf("instanceNumber"),
            onDelete = ForeignKey.NO_ACTION
        )],
    indices = [Index(value = ["cleanNumber"]), Index(value = ["rawNumber"]), Index(value = ["instanceNumber"])]
)
data class ContactNumber(
    val CID: String,
    val cleanNumber: String,
    val defaultCID: String,
    val rawNumber: String,
    val instanceNumber: String,
    val versionNumber: Int = 0,
    val degree: Int
) {
    override fun equals(other: Any?): Boolean {
        return if (other is ContactNumber) {
            (this.CID == other.CID && this.cleanNumber == other.cleanNumber)
        } else {
            false
        }
    }
    override fun toString() : String {
        return "CONTACT NUMBER -  CID: " + this.CID  +  " number: " + this.cleanNumber +
            " versionNumber: " + this.versionNumber
    }

    override fun hashCode(): Int {
        var result = CID.hashCode()
        result = 31 * result + cleanNumber.hashCode()
        result = 31 * result + versionNumber
        return result
    }
}

/**
 * Currently not using [algoAllowed]. Instead, we will calculate on the spot.
 */
@Entity(tableName = "analyzed_number")
data class AnalyzedNumber(
    @PrimaryKey val number: String, // should use cleaned number
    val algoAllowed: Boolean? = null, // currently not using this value.
    val notifyGate: Int? = null,
    val lastCallTime: Long? = null,
    val numIncoming: Int? = null,
    val numOutgoing: Int? = null,
    val maxDuration: Long? = null,
    val avgDuration: Long? = null,
    val smsVerified: Boolean? = null,
    val markedSafe: Boolean? = null,
    val isBlocked: Boolean? = null,
    val numMarkedBlocked: Int? = null,
    val numSharedContacts: Int? = null,
    val isOrganization: Boolean? = null,
    val minDegree: Int? = null,
    val numTreeContacts: Int? = null,
    val degreeString: String? = null
) {

    override fun toString() : String {
        return "ANALYZED NUMBER: number: $number algoAllowed: $algoAllowed notifyGate: $notifyGate" +
            " lastCallTime: $lastCallTime numIncoming: $numIncoming numOutgoing: $numOutgoing" +
            " maxDuration: $maxDuration avgDuration: $avgDuration smsVerified: $smsVerified" +
            " markedSafe: $markedSafe isBlocked: $isBlocked numMarkedBlocked: $numMarkedBlocked" +
            " numSharedContacts: $numSharedContacts isOrganization: $isOrganization" +
            " minDegree: $minDegree numTreeContacts: $numTreeContacts degreeString: $degreeString"
    }
}
