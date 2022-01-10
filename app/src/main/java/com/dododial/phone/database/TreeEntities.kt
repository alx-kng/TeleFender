package com.dododial.phone.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey


@Entity(tableName = "key_storage")
data class KeyStorage(
    @PrimaryKey val number: String,
    val clientKey: String)

@Entity(tableName = "instance")
data class Instance(
    @PrimaryKey val number: String)

@Entity(tableName = "contact",
    foreignKeys = [ForeignKey(
        entity = Instance::class,
        parentColumns = arrayOf("number"),
        childColumns = arrayOf("parentNumber"),
        onDelete = ForeignKey.CASCADE
    )])
data class Contact(
    @PrimaryKey val CID: String,
    val parentNumber : String,
    val name : String?) {
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
)

@Entity(tableName = "trusted_numbers")
data class TrustedNumbers(
    @PrimaryKey val number: String,
    val counter: Int = 1) {
}

@Entity(tableName = "organizations")
data class Organizations(
    @PrimaryKey val number: String,
    val counter: Int = 1) {
}

@Entity(tableName = "miscellaneous")
data class Miscellaneous(
    @PrimaryKey val number: String,
    val counter: Int = 1,
    val trustability: Int = 1) {
}

