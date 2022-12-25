package com.telefender.phone.data.tele_database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass


// TODO: Why is rowID the PK?
@JsonClass(generateAdapter = true)
@Entity(tableName = "change_log",
    indices = [Index(value = ["changeID"], unique = true)],
)
data class ChangeLog(
    val changeID: String,
    val changeTime: Long,
    val type: String,
    val instanceNumber: String,
    val CID : String? = null,
    val cleanNumber : String? = null,
    val defaultCID: String? = null,
    val rawNumber: String? = null,
    val oldNumber : String? = null,
    val blocked : Boolean? = null,
    val degree : Int? = null,
    val counterValue : Int? = null,
    val errorCounter : Int = 0,
    val serverChangeID : Int? = null,
    @PrimaryKey(autoGenerate = true)
    val rowID : Int = 0
) {
    override fun toString(): String {
        return "CHANGELOG: rowID: $rowID changeID: $changeID TYPE: $type " +
            "instanceNumber: $instanceNumber changeTime: $changeTime CID: $CID " +
            "number: $cleanNumber"
    }
}

@Entity(tableName = "upload_queue",
    foreignKeys = [
        ForeignKey(
            entity = ChangeLog::class,
            parentColumns = arrayOf("changeID"),
            childColumns = arrayOf("changeID"),
            onDelete = ForeignKey.CASCADE),
    ])
data class UploadQueue(
    @PrimaryKey val changeID: String,
    val createTime: Long,
    val rowID: Int,
    val errorCounter: Int = 0
    ) {
    override fun toString() : String {
        return ("UPLOAD LOG: changeID: " + this.changeID + " createTime: " + this.createTime + " errorCounter: " + this.errorCounter)
    }
}

@Entity(tableName = "execute_queue",
   foreignKeys = [ForeignKey(
            entity = ChangeLog::class,
            parentColumns = arrayOf("changeID"),
            childColumns = arrayOf("changeID"),
            onDelete = ForeignKey.CASCADE
       )])
data class ExecuteQueue(
    @PrimaryKey val changeID: String,
    val createTime : Long,
    val errorCounter : Int = 0
) {
    override fun toString() : String {
        return ("EXECUTE LOG: changeID: " + this.changeID + " createTime: " + this.createTime.toString() + " errorCounter: " + this.errorCounter)
    }
}