package com.dododial.phone.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "change_log")
data class ChangeLog(
    @PrimaryKey val changeID: String,
    val instanceNumber: String?,
    val changeTime: String,
    val type: String,
    val CID : String?,
    val name : String?,
    val oldNumber : String?,
    val number : String?,
    val parentNumber : String?,
    val trustability : Int?,
    val counterValue : Int?,
    val errorCounter : Int = 0
) {

    override fun toString(): String {
        return ("instanceNumber: " + this.instanceNumber +
            " changeTime: " + this.changeTime + " CID: " + this.CID + " name: " +
            this.name + " number: " + this.number + " parentNumber: " + this.parentNumber)           
    }
}

@Entity(tableName = "queue_to_upload",
    foreignKeys = [ForeignKey(
        entity = ChangeLog::class,
        parentColumns = arrayOf("changeID"),
        childColumns = arrayOf("changeID"),
        onDelete = ForeignKey.CASCADE
    )])
data class QueueToUpload(
    @PrimaryKey val changeID: String,
    val createTime: String,
    val errorCounter: Int = 0
)

@Entity(tableName = "queue_to_execute",
   foreignKeys = [ForeignKey(
            entity = ChangeLog::class,
            parentColumns = arrayOf("changeID"),
            childColumns = arrayOf("changeID"),
            onDelete = ForeignKey.CASCADE
       )])
data class QueueToExecute(
    @PrimaryKey val changeID: String,
    val createTime : String,
    val errorCounter : Int = 0
)
