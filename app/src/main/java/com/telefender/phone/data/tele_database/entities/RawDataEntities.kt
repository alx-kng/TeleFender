package com.telefender.phone.data.tele_database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.telefender.phone.gui.adapters.CallDetailItem
import com.telefender.phone.gui.model.GroupedCallDetail
import com.telefender.phone.helpers.TeleHelpers


/**
 * TODO: actually make UI show from our CallLogs <--- Maybe to show blocked status and stuff
 * TODO: Make sure indices are right.
 *
 * [callDuration] is in seconds since Call object's connectTimeMillis.
 * [callEpochDate] is in milliseconds and corresponds to Call object's creationTimeMillis.
 *
 * Although [callType] seems to be redundant with [callDirection], having both has its indirect
 * uses. For example, [callType] is set to null in skeleton log insert (for unallowed calls) and
 * basically indicates whether or not the CallDetail is synced or not.
 */
@JsonClass(generateAdapter = true)
@Entity(tableName = "call_detail",
    indices = [
        Index(value = ["callEpochDate", "instanceNumber"]),
        Index(value = ["instanceNumber"])
    ]
)
data class CallDetail(
    @PrimaryKey(autoGenerate = true)
    var rowID: Long = 0, // needs to be var so that we can reset downloaded logs' rowID.
    val rawNumber: String,
    val normalizedNumber: String,
    val callType: String?,
    val callEpochDate: Long,
    val callDuration: Long,
    val callLocation: String?,
    val callDirection: Int,
    val instanceNumber: String,
    val unallowed: Boolean = false
) : CallDetailItem, TableEntity() {

    override fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(CallDetail::class.java)
        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "rawNumber: $rawNumber callType: $callType callEpochDate: $callEpochDate callDuration: " +
            "$callDuration callLocation: $callLocation " +
            "callDirection: ${TeleHelpers.getDirectionString(callDirection)} " +
            "unallowed: $unallowed normalizedNumber: $normalizedNumber"
    }

    fun createGroup() : GroupedCallDetail {
        return GroupedCallDetail(
            rawNumber = rawNumber,
            callEpochDate = callEpochDate,
            callLocation = callLocation,
            callDirection = callDirection,
            unallowed = unallowed,
            amount = 1,
            firstEpochID = callEpochDate
        )
    }
}

@JsonClass(generateAdapter = true)
@Entity(tableName = "instance")
data class Instance(
    @PrimaryKey val number: String
) : TableEntity() {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(Instance::class.java)
        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "INSTANCE - number: " + this.number
    }
}

// TODO: Should we add defaultCID column to Contact table?
@JsonClass(generateAdapter = true)
@Entity(tableName = "contact",
    foreignKeys = [ForeignKey(
        entity = Instance::class,
        parentColumns = arrayOf("number"),
        childColumns = arrayOf("instanceNumber"),
        onDelete = ForeignKey.NO_ACTION
    )],
    indices = [
        // Enforce unique because this is actually "true" PK.
        Index(value = ["CID"], unique = true),
        Index(value = ["instanceNumber"])
    ]
)
data class Contact(
    @PrimaryKey(autoGenerate = true)
    var rowID: Long = 0,
    val CID: String,
    val instanceNumber : String,
    val blocked: Boolean = false
) : TableEntity() {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(Contact::class.java)
        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "CONTACT - instanceNumber: $instanceNumber CID: $CID blocked: $blocked"
    }
}

@JsonClass(generateAdapter = true)
@Entity(tableName = "contact_number",
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
    indices = [
        // Enforce unique because this is actually "true" PK.
        Index(value = ["CID", "normalizedNumber"], unique = true),
        Index(value = ["normalizedNumber"]),
        Index(value = ["rawNumber"]),
        Index(value = ["instanceNumber"]),
    ]
)
data class ContactNumber(
    @PrimaryKey(autoGenerate = true)
    var rowID: Long = 0,
    val CID: String,
    val normalizedNumber: String, // E164 representation
    val defaultCID: String,
    val rawNumber: String,
    val instanceNumber: String,
    val versionNumber: Int = 0,
    val degree: Int
) : TableEntity() {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(ContactNumber::class.java)
        return adapter.serializeNulls().toJson(this)
    }

    /**
     * Returns true if PK of two ContactNumbers are equal.
     */
    override fun equals(other: Any?): Boolean {
        return if (other is ContactNumber) {
            (this.CID == other.CID && this.normalizedNumber == other.normalizedNumber)
        } else {
            false
        }
    }
    override fun toString() : String {
        return "CONTACT NUMBER - instanceNumber: $instanceNumber CID: $CID " +
            "normalizedNumber: $normalizedNumber versionNumber: $versionNumber"
    }

    override fun hashCode(): Int {
        var result = CID.hashCode()
        result = 31 * result + normalizedNumber.hashCode()
        result = 31 * result + versionNumber
        return result
    }
}