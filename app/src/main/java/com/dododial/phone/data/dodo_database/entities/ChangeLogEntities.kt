package com.dododial.phone.data.dodo_database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "change_log",
    indices = [Index(value = ["changeID"], unique = true)],

)
data class ChangeLog(

    val changeID: String, 
    val instanceNumber: String?,
    val changeTime: Long,
    val type: String,
    val CID : String?,
    val oldNumber : String?,
    val number : String?,
    val parentNumber : String?,
    val trustability : Int?,
    val counterValue : Int?,
    val errorCounter : Int = 0,
    val serverChangeID : Int? = null,
    @PrimaryKey(autoGenerate = true)
    val rowID : Int = 0

) {

    override fun toString(): String {
        return ("CHANGELOG: rowID: " + this.rowID + " changeID: " + this.changeID + " TYPE: " + this.type + " instanceNumber: " + this.instanceNumber +
            " changeTime: " + this.changeTime + " CID: " + this.CID +
            " number: " + this.number + " parentNumber: " + this.parentNumber)
    }
}

@Entity(tableName = "queue_to_upload",
    foreignKeys = [
        ForeignKey(
            entity = ChangeLog::class,
            parentColumns = arrayOf("changeID"),
            childColumns = arrayOf("changeID"),
            onDelete = ForeignKey.CASCADE),
    ])
data class QueueToUpload(
    @PrimaryKey val changeID: String,
    val createTime: Long,
    val rowID: Int,
    val errorCounter: Int = 0

    ) {
    override fun toString() : String {
        return ("UPLOADLOG: changeID: " + this.changeID + " createTime: " + this.createTime + " errorCounter: " + this.errorCounter)
    }
}

@Entity(tableName = "queue_to_execute",
   foreignKeys = [ForeignKey(
            entity = ChangeLog::class,
            parentColumns = arrayOf("changeID"),
            childColumns = arrayOf("changeID"),
            onDelete = ForeignKey.CASCADE
       )])
data class QueueToExecute(
    @PrimaryKey val changeID: String,
    val createTime : Long,
    val errorCounter : Int = 0
) {
    override fun toString() : String {
        return ("EXECUTELOG: changeID: " + this.changeID + " createTime: " + this.createTime.toString() + " errorCounter: " + this.errorCounter)
    }
}