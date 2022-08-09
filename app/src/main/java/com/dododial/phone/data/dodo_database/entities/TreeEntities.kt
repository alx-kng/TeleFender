package com.dododial.phone.data.dodo_database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey


@Entity(tableName = "key_storage")
data class KeyStorage(
    @PrimaryKey val number: String,
    val sessionID: String,
    val clientKey: String?,
    val fireBaseToken: String?
) {

    override fun toString() : String {
        return "KEYSTORAGE - number" + this.number + " sessionID: " + this.sessionID + " clientKey: " + this.clientKey + " fireBaseToken: " + this.fireBaseToken
    }
}

@Entity(tableName = "instance")
data class Instance(
    @PrimaryKey val number: String,
    val databaseInitialized: Boolean = false) {

    override fun toString() : String {
        return "INSTANCE - number: " + this.number + " databaseInitialized: " + this.databaseInitialized
    }
}

@Entity(tableName = "contact",
    foreignKeys = [ForeignKey(
        entity = Instance::class,
        parentColumns = arrayOf("number"),
        childColumns = arrayOf("parentNumber"),
        onDelete = ForeignKey.CASCADE
    )])
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
            ),
    ])
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

