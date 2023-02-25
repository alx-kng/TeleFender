package com.telefender.phone.data.tele_database.entities

import androidx.room.*
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi


/**
 * Everything outside of [changeJson] is an essential field. Essential fields (aside from being
 * used frequently) might also be used as selection criteria for ChangeLog. RowID is PK for
 * various reasons, one important one being the natural ordering of ChangeLogs which can be
 * leveraged as PK of UploadChangeQueue.
 */
@JsonClass(generateAdapter = true)
@Entity(tableName = "change_log",
    indices = [
        Index(value = ["changeID"], unique = true),
    ]
)
data class ChangeLog(
    @PrimaryKey(autoGenerate = true)
    var rowID: Long = 0, // needs to be var so that we can reset downloaded logs' rowID.
    val changeID: String,
    val changeTime: Long,
    val type: String,
    val instanceNumber: String,
    val errorCounter : Int = 0,
    val changeJson: String = "{}"
) : TableEntity() {

    /**
     * Returns [type] as a ChangeType if possible.
     */
    fun getChangeType() : ChangeType? {
        return type.toChangeType()
    }

    /**
     * Returns Change stored in [changeJson] if possible.
     */
    fun getChange() : Change? {
        return changeJson.toChange()
    }

    override fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(ChangeLog::class.java)
        return adapter.serializeNulls().toJson(this)
    }

    override fun toString(): String {
        val changeObj = changeJson.toChange()
        return "CHANGELOG: rowID: $rowID changeID: $changeID changeTime: $changeTime TYPE: $type " +
            "instanceNumber: $instanceNumber CHANGE: $changeObj"
    }

    companion object {

        /**
         * Creates ChangeLog. Also allows you to modify arguments to fit form to database (e.g.,
         * we can accept type as ChangeType here and convert to string for actual ChangeLog).
         */
        fun create(
            rowID: Long = 0,
            changeID: String,
            changeTime: Long,
            type: ChangeType,
            instanceNumber: String,
            errorCounter : Int = 0,
            changeJson: String = "{}"
        ) : ChangeLog {

            return ChangeLog(
                rowID = rowID,
                changeID = changeID,
                changeTime = changeTime,
                type = type.serverString,
                instanceNumber = instanceNumber,
                errorCounter = errorCounter,
                changeJson = changeJson
            )
        }
    }
}

enum class ChangeType(val serverString: String) {
    CONTACT_INSERT("ADDC"),
    CONTACT_UPDATE("UPDC"),
    CONTACT_DELETE("DELC"),
    CONTACT_NUMBER_INSERT("ADDN"),
    CONTACT_NUMBER_UPDATE("UPDN"),
    CONTACT_NUMBER_DELETE("DELN"),
    INSTANCE_INSERT("ADDI"),
    INSTANCE_DELETE("DELI"),
    NON_CONTACT_UPDATE("NONU")
}

/**
 * Converts serverStr to ChangeType if possible.
 */
fun String.toChangeType() : ChangeType? {
    for (changeType in ChangeType.values()) {
        if (this == changeType.serverString) {
            return changeType
        }
    }

    return null
}

/**
 * Used for change fields not used in selection criteria for ChangeLog.
 */
@JsonClass(generateAdapter = true)
data class Change(
    val CID : String? = null,
    val normalizedNumber : String? = null,
    val defaultCID : String? = null,
    val rawNumber : String? = null,
    val blocked : Boolean? = null, // only used for contacts
    val safeAction : String? = null, // only used for non-contacts
    val degree : Int? = null,
    val counterValue : Int? = null,
) {

    fun getSafeAction() : SafeAction? {
        return safeAction?.toSafeAction()
    }

    fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(Change::class.java)
        return adapter.serializeNulls().toJson(this)
    }

    override fun toString(): String {
        return "CID: $CID normalizedNumber: $normalizedNumber blocked: $blocked"
    }

    companion object {

        /**
         * Creates Change object. Also allows you to modify arguments to fit form to database (e.g.,
         * we can accept type as SafeAction here and convert to string for actual Change object).
         */
        fun create(
            CID : String? = null,
            normalizedNumber : String? = null,
            defaultCID : String? = null,
            rawNumber : String? = null,
            blocked : Boolean? = null, // only used for contacts
            safeAction : SafeAction? = null, // only used for non-contacts
            degree : Int? = null,
            counterValue : Int? = null,
        ) : Change {

            return Change(
                CID = CID,
                normalizedNumber = normalizedNumber,
                defaultCID = defaultCID,
                rawNumber = rawNumber,
                blocked = blocked,
                safeAction = safeAction?.serverString,
                degree = degree,
                counterValue = counterValue
            )
        }
    }
}

enum class SafeAction(val serverString: String) {
    SAFE("SAF"), DEFAULT("DEF"), BLOCKED("BLO"), SMS_VERIFY("SMS_VERIFY")
}

/**
 * Converts serverStr to SafeAction if possible.
 */
fun String.toSafeAction() : SafeAction? {
    for (safeAction in SafeAction.values()) {
        if (this == safeAction.serverString) {
            return safeAction
        }
    }

    return null
}

/**
 * Converts JSON string to Change object.
 * Note: Need to put try-catch around any sort of Moshi string-to-object function.
 */
fun String.toChange() : Change? {
    return try {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(Change::class.java)

        adapter.serializeNulls().fromJson(this)
    } catch (e: Exception) {
        null
    }
}


