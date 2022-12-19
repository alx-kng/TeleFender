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
        return "STOREDMAP - number" + this.userNumber + " sessionID: " + this.sessionID + " clientKey: " + this.clientKey + " fireBaseToken: " + this.fireBaseToken
    }
}

@Entity(tableName = "instance")
data class Instance(
    @PrimaryKey val number: String) {

    override fun toString() : String {
        return "INSTANCE - number: " + this.number
    }
}

@Entity(tableName = "contact",
    foreignKeys = [ForeignKey(
        entity = Instance::class,
        parentColumns = arrayOf("number"),
        childColumns = arrayOf("parentNumber"),
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["parentNumber"])]
)
data class Contact(
    @PrimaryKey val CID: String,
    val parentNumber : String) {

    override fun toString() : String {
        return "CONTACT -  CID: " + this.CID  +  " parentNumber: " + this.parentNumber
    }
}

@Entity(tableName = "contact_numbers",
    primaryKeys = ["CID", "number"],
    foreignKeys = [ForeignKey(
            entity = Contact::class,
            parentColumns = arrayOf("CID"),
            childColumns = arrayOf("CID"),
            onDelete = ForeignKey.CASCADE 
            )],
    indices = [Index(value = ["number"])]
)
data class ContactNumbers(
    val CID: String,
    val number : String,
    val versionNumber: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (other is ContactNumbers) {
            return (this.CID == other.CID && this.number == other.number)
        } else {
            return false
        }
    }
    override fun toString() : String {
        return "CONTACTNUMBER -  CID: " + this.CID  +  " number: " + this.number +
            " versionNumber: " + this.versionNumber
    }

    override fun hashCode(): Int {
        var result = CID.hashCode()
        result = 31 * result + number.hashCode()
        result = 31 * result + versionNumber
        return result
    }
}

@Entity(tableName = "trusted_numbers")
data class TrustedNumbers(
    @PrimaryKey val number: String,
    val counter: Int = 1) {

    override fun toString() : String {
        return "TRUSTEDNUMBER - number : " + this.number + " counter: " + this.counter
    }
}

@Entity(tableName = "organizations")
data class Organizations(
    @PrimaryKey val number: String,
    val counter: Int = 1) {

    override fun toString() : String {
        return "ORGANIZATIONS - number : " + this.number + " counter: " + this.counter
    }
}

@Entity(tableName = "miscellaneous")
data class Miscellaneous(
    @PrimaryKey val number: String,
    val counter: Int = 1,
    val trustability: Int = 1) {

    override fun toString() : String {
        return "MISCELLANEOUS - number : " + this.number + " counter: " + this.counter  + " trustability: " + this.trustability
    }
}

