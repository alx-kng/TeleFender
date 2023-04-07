package com.telefender.phone.data.tele_database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi


/**
 * TODO: Maybe add tele-marketing mode that's an in-between to safe / spam mode for notify list
 *  numbers. That way, users can choose whether or not to be bothered by tele-marketing calls
 *  (that aren't spam) again. Just a thought.
 *
 * Contains all analyzed data.
 */
@JsonClass(generateAdapter = true)
@Entity(tableName = "analyzed_number",
    indices = [
        Index(value = ["instanceNumber", "normalizedNumber"]),
        Index(value = ["normalizedNumber"])
    ]
)
data class AnalyzedNumber(
    @PrimaryKey(autoGenerate = true)
    var rowID: Long = 0, // needs to be var so that we can reset downloaded analyzed' rowID.
    val normalizedNumber: String, // should use cleaned number
    val instanceNumber: String,
    val numTotalCalls: Int, // includes any type of call
    val analyzedJson: String = "{}"
) : TableEntity() {

    /**
     * Retrieves Analyzed object version of analyzedJson, but SHOULD ONLY BE USED for
     * AnalyzedNumbers retrieved from the database, and NOT for freshly created AnalyzedNumbers
     * (We're assuming analyzedJson is a valid Json string due to the initialization process).
     */
    fun getAnalyzed() : Analyzed {
        return analyzedJson.toAnalyzed()!!
    }

    override fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(AnalyzedNumber::class.java)
        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        val analyzedObj = analyzedJson.toAnalyzed()
        return "ANALYZED NUMBER: rowID: $rowID " +
            "number: $normalizedNumber instanceNumber: $instanceNumber ANALYZED: $analyzedObj"
    }
}

/**
 * TODO: GENERAL ISSUE TO SOLVE - Stop spam numbers from overcrowding notifyList --> look at doc
 *
 * TODO: A good idea might be to increase the notify gate for numbers that are less safe. For
 *  example, if a person calls multiple times but doesn't leave a voicemail, then we have more
 *  reason to suspect that they might be spam. As a result, increasing the notify gate to
 *  something like 3 or 4 might be beneficial in not overcrowding the NotifyItem. --> look at doc.
 *
 * TODO: Add another field for if number also has TeleFender app downloaded -> maybe not.
 *
 * Used for analyzed fields not used in selection criteria for AnalyzedNumber. Stored as JSON.
 * Currently not using [algoAllowed]. Instead, we will calculate on the spot.
 */
@JsonClass(generateAdapter = true)
data class Analyzed(
    val algoAllowed: Boolean? = null, // currently not using this value.
    val notifyGate: Int,
    val notifyWindow: List<Long>,

    // For SMS verification
    val serverSentWindow: List<Long>, // List of times when server sent SMS verify messages.
    val clientSentAfterExpire: Boolean, // True if already sent SMS request to server after link expires.

    // Important actions
    val smsVerified: Boolean,
    val markedSafe: Boolean,
    val isBlocked: Boolean,

    // General type
    val lastCallTime: Long? = null,
    val lastCallDirection: Int? = null,
    val lastCallDuration: Long? = null,
    val maxDuration: Long, // Only considers non-voicemail calls
    val avgDuration: Double, // Only considers non-voicemail calls

    // Incoming subtype
    val numIncoming: Int,
    val lastIncomingTime: Long? = null,
    val lastIncomingDuration: Long? = null,
    val maxIncomingDuration: Long,
    val avgIncomingDuration: Double,

    // Outgoing subtype
    val numOutgoing: Int,
    val lastOutgoingTime: Long? = null,
    val lastFreshOutgoingTime: Long? = null, // Last outgoing time with no prior calls (within long period)
    val lastOutgoingDuration: Long? = null,
    val maxOutgoingDuration: Long,
    val avgOutgoingDuration: Double,

    // Voicemail subtype
    val numVoicemail: Int,
    val lastVoicemailTime: Long? = null,
    val lastVoicemailDuration: Long? = null,
    val maxVoicemailDuration: Long,
    val avgVoicemailDuration: Double,

    // Missed subtype
    val numMissed: Int,
    val lastMissedTime: Long? = null,

    // Rejected subtype
    val numRejected: Int,
    val lastRejectedTime: Long? = null,

    // Blocked subtype
    val numBlocked: Int,
    val lastBlockedTime: Long? = null,

    // Contact / Tree info
    val numMarkedBlocked: Int, // for contacts only
    val numSharedContacts: Int,
    val numTreeContacts: Int,
    val degreeString: String,
    val minDegree: Int? = null,
    val isOrganization: Boolean
) {

    fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(Analyzed::class.java)
        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "algoAllowed: $algoAllowed notifyGate: $notifyGate notifyWindow: $notifyWindow" +
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
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(Analyzed::class.java)

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
