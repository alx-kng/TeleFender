package com.telefender.phone.data.tele_database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi


/**
 * TODO: Why is rowID the PK?
 *
 * Everything outside of [changeJson] is an essential field. Essential fields (aside from being
 * used frequently) might also be used as selection criteria for ChangeLog.
 */
@JsonClass(generateAdapter = true)
@Entity(tableName = "change_log",
    indices = [
        Index(value = ["changeID"], unique = true),
        Index(value = ["serverChangeID"])
    ]
)
data class ChangeLog(
    @PrimaryKey(autoGenerate = true)
    val rowID: Int = 0,
    val changeID: String,
    val changeTime: Long,
    val type: String,
    val instanceNumber: String,
    val serverChangeID: Int? = null,
    val errorCounter : Int = 0,
    val changeJson: String = "{}"
) {

    fun getChange() : Change? {
        return changeJson.toChange()
    }

    override fun toString(): String {
        val changeObj = changeJson.toChange()
        return "CHANGELOG: rowID: $rowID changeID: $changeID changeTime: $changeTime TYPE: $type " +
            "instanceNumber: $instanceNumber CHANGE: $changeObj"
    }
}

/**
 * Used for change fields not used in selection criteria for ChangeLog.
 */
@JsonClass(generateAdapter = true)
data class Change(
    val CID : String? = null,
    val normalizedNumber : String? = null,
    val defaultCID: String? = null,
    val rawNumber: String? = null,
    val blocked : Boolean? = null,
    val degree : Int? = null,
    val counterValue : Int? = null,
) {

    fun toJson() : String {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<Change> = moshi.adapter(Change::class.java)
        return adapter.serializeNulls().toJson(this)
    }

    override fun toString(): String {
        return "CID: $CID normalizedNumber: $normalizedNumber blocked: $blocked"
    }
}

/**
 * Converts JSON string to Change object.
 * Note: Need to put try-catch around any sort of Moshi string-to-object function.
 */
fun String.toChange() : Change? {
    return try {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<Change> = moshi.adapter(Change::class.java)

        adapter.serializeNulls().fromJson(this)
    } catch (e: Exception) {
        null
    }
}

@Entity(tableName = "upload_queue",
    foreignKeys = [
        ForeignKey(
            entity = ChangeLog::class,
            parentColumns = arrayOf("changeID"),
            childColumns = arrayOf("changeID"),
            onDelete = ForeignKey.CASCADE
        )],
    indices = [Index(value = ["createTime"], unique = true)]
)
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
       )],
    indices = [Index(value = ["createTime"], unique = true)]
)
data class ExecuteQueue(
    @PrimaryKey val changeID: String,
    val createTime : Long,
    val errorCounter : Int = 0
) {
    override fun toString() : String {
        return ("EXECUTE LOG: changeID: " + this.changeID + " createTime: " + this.createTime.toString() + " errorCounter: " + this.errorCounter)
    }
}