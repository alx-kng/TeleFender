package com.telefender.phone.data.tele_database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi


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

@JsonClass(generateAdapter = true)
@Entity(tableName = "parameters")
data class Parameters(
    @PrimaryKey val userNumber: String,
    val initialNotifyGate: Int,
    val verifiedSpamNotifyGate: Int,
    val superSpamNotifyGate: Int,
)

@Entity(tableName = "instance")
data class Instance(
    @PrimaryKey val number: String) {

    override fun toString() : String {
        return "INSTANCE - number: " + this.number
    }
}

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
    val blocked: Boolean = false
) {

    override fun toString() : String {
        return "CONTACT -  CID: $CID instanceNumber: $instanceNumber blocked: $blocked"
    }
}

@Entity(tableName = "contact_number",
    primaryKeys = ["CID", "normalizedNumber"],
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
        Index(value = ["normalizedNumber"]),
        Index(value = ["rawNumber"]),
        Index(value = ["instanceNumber"]),
        Index(value = ["CID"])
    ]
)
data class ContactNumber(
    val CID: String,
    val normalizedNumber: String, // E164 representation
    val defaultCID: String,
    val rawNumber: String,
    val instanceNumber: String,
    val versionNumber: Int = 0,
    val degree: Int
) {
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
        return "CONTACT NUMBER -  CID: " + this.CID  +  " number: " + this.normalizedNumber +
            " versionNumber: " + this.versionNumber
    }

    override fun hashCode(): Int {
        var result = CID.hashCode()
        result = 31 * result + normalizedNumber.hashCode()
        result = 31 * result + versionNumber
        return result
    }
}

/**
 * TODO: Maybe add tele-marketing mode that's an in-between to safe / spam mode for notify list
 *  numbers. That way, users can choose whether or not to be bothered by tele-marketing calls
 *  (that aren't spam) again. Just a thought.
 *
 * Contains all analyzed data.
 */
@JsonClass(generateAdapter = true)
@Entity(tableName = "analyzed_number")
data class AnalyzedNumber(
    @PrimaryKey val normalizedNumber: String, // should use cleaned number
    val analyzedJson: String = "{}"
) {

    /**
     * Retrieves Analyzed object version of analyzedJson, but SHOULD ONLY BE USED for
     * AnalyzedNumbers retrieved from the database, and NOT for freshly created AnalyzedNumbers
     * (We're assuming analyzedJson is a valid Json string due to the initialization process).
     */
    fun getAnalyzed() : Analyzed {
        return analyzedJson.toAnalyzed()!!
    }

    override fun toString() : String {
        val analyzedObj = analyzedJson.toAnalyzed()
        return "ANALYZED NUMBER: number: $normalizedNumber ANALYZED: $analyzedObj"
    }
}

/**
 * TODO: GENERAL ISSUE TO SOLVE - Stop spam numbers from overcrowding notifyList
 *
 * TODO: A good idea might be to increase the notify gate for numbers that are less safe. For
 *  example, if a person calls multiple times but doesn't leave a voicemail, then we have more
 *  reason to suspect that they might be spam. As a result, increasing the notify gate to
 *  something like 3 or 4 might be beneficial in not overcrowding the NotifyList.
 *
 * TODO: Add to AnalyzedNumbers...
 *  GENERAL TYPE:
 *      last call duration
 *      last call type
 *      # total calls
 *      OUTGOING SUBTYPE:
 *          last outgoing time
 *          last outgoing call duration
 *          max outgoing call duration
 *          avg outgoing call duration
 *      INCOMING SUBTYPE:
 *          last incoming time
 *          last incoming call duration
 *          max incoming call duration
 *          avg incoming call duration
 *      VOICEMAIL SUBTYPE:
 *          # voicemail
 *          last voicemail time
 *          last voicemail duration
 *          max voicemail duration
 *          avg voicemail duration
 *      MISSED SUBTYPE:
 *          # missed calls
 *          last missed time
 *      REJECTED SUBTYPE
 *          # rejected calls
 *          last rejected time
 *      BLOCKED SUBTYPE
 *          # blocked calls
 *          last blocked time
 *
 * Used for analyzed fields not used in selection criteria for AnalyzedNumber. Stored as JSON.
 * Currently not using [algoAllowed]. Instead, we will calculate on the spot.
 */
@JsonClass(generateAdapter = true)
data class Analyzed(
    val algoAllowed: Boolean? = null, // currently not using this value.
    val notifyGate: Int,
    val lastCallTime: Long? = null,
    val numIncoming: Int,
    val numOutgoing: Int,
    val maxDuration: Long,
    val avgDuration: Double,
    val smsVerified: Boolean,
    val markedSafe: Boolean,
    val isBlocked: Boolean,
    val numMarkedBlocked: Int, // for contacts only
    val numSharedContacts: Int,
    val isOrganization: Boolean,
    val minDegree: Int? = null,
    val numTreeContacts: Int,
    val degreeString: String
) {

    fun toJson() : String {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<Analyzed> = moshi.adapter(Analyzed::class.java)
        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "algoAllowed: $algoAllowed notifyGate: $notifyGate" +
            " lastCallTime: $lastCallTime numIncoming: $numIncoming numOutgoing: $numOutgoing" +
            " maxDuration: $maxDuration avgDuration: $avgDuration smsVerified: $smsVerified" +
            " markedSafe: $markedSafe isBlocked: $isBlocked numMarkedBlocked: $numMarkedBlocked" +
            " numSharedContacts: $numSharedContacts isOrganization: $isOrganization" +
            " minDegree: $minDegree numTreeContacts: $numTreeContacts degreeString: $degreeString"
    }
}


/**
 * Converts JSON string to Analyzed object.
 * Note: Need to put try-catch around any sort of Moshi string-to-object function.
 */
fun String.toAnalyzed() : Analyzed? {
    return try {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<Analyzed> = moshi.adapter(Analyzed::class.java)

        adapter.serializeNulls().fromJson(this)
    } catch (e: Exception) {
        null
    }
}

/**
 * Returns whether a string is a valid Analyzed JSON string.
 */
fun String.isValidAnalyzed() : Boolean {
    return this.toAnalyzed() != null
}
