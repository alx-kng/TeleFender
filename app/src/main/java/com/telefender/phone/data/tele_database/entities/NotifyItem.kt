package com.telefender.phone.data.tele_database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi


@JsonClass(generateAdapter = true)
@Entity(tableName = "notify_item",
    indices = [
        Index(value = ["normalizedNumber"], unique = true),
        Index(value = ["lastCallTime"]),
    ]
)
data class NotifyItem(
    @PrimaryKey(autoGenerate = true)
    var rowID: Long = 0,
    val normalizedNumber: String,
    val instanceNumber: String,
    val lastCallTime: Long,
    val lastQualifiedTime: Long,
    val veryFirstSeenTime: Long? = null,
    val seenSinceLastCall: Boolean = false,
    val notifyWindow: List<Long>,
    val currDropWindow: Int,
    val nextDropWindow: Int
) : TableEntity() {

    override fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(NotifyItem::class.java)
        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "normalizedNumber: $normalizedNumber instanceNumber: $instanceNumber " +
            "lastCallTime: $lastCallTime lastQualifiedTime: $lastQualifiedTime " +
            "veryFirstSeenTime: $veryFirstSeenTime seenSinceLastCall: $seenSinceLastCall" +
            "notifyWindow: $notifyWindow currDropWindow: $currDropWindow nextDropWindow: $nextDropWindow"
    }
}