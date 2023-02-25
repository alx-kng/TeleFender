package com.telefender.phone.data.server_related.json_classes

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.telefender.phone.data.tele_database.entities.AnalyzedNumber
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.data.tele_database.entities.ErrorQueue


// parse request - instanceNumber, key, changes: [{rowid, chgid, changeTime, type, cid, name, number}, ...]
//The first request containing only instance field
@JsonClass(generateAdapter = true)
open class DefaultRequest(
    val instanceNumber : String
) {

    open fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(DefaultRequest::class.java)

        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "REQUEST - instanceNumber: $instanceNumber"
    }
}

/**
 * The second request to verify the user containing instanceNumber, sessionID, OTP
 */
@JsonClass(generateAdapter = true)
class VerifyRequest(
    instanceNumber : String,
    val sessionID : String,
    val OTP : Int
) : DefaultRequest(instanceNumber) {

    override fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(VerifyRequest::class.java)

        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "sessionID: $sessionID OTP: $OTP"
    }
}

/**
 * A standard request parent class containing only instanceNumber and Key, inherited by Upload/Download requests
 */
@JsonClass(generateAdapter = true)
open class KeyRequest(
    instanceNumber : String,
    val key : String,
) : DefaultRequest(instanceNumber) {

    override fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(KeyRequest::class.java)

        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "${super.toString()} key: $key"
    }
}

/**
 * Inherits from KeyRequest, request class for download
 */
@JsonClass(generateAdapter = true)
class DownloadRequest(
    instanceNumber : String,
    key : String,
    val lastServerRowID : Long?
) : KeyRequest(instanceNumber, key) {

    override fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(DownloadRequest::class.java)

        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "${super.toString()} lastServerRowID: $lastServerRowID"
    }
}

/**
 * Inherits from KeyRequest, request class for uploading ChangeLogs.
 */
@JsonClass(generateAdapter = true)
class UploadChangeRequest(
    instanceNumber : String,
    key : String,
    val changes : List<ChangeLog>
) : KeyRequest(instanceNumber, key) {

    override fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(UploadChangeRequest::class.java)

        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "${super.toString()} numChanges = ${changes.size}" +
            "firstChangeSent: ${changes.firstOrNull()?.changeID}"
    }
}

/**
 * Inherits from KeyRequest, request class for uploading AnalyzedNumbers.
 */
@JsonClass(generateAdapter = true)
class UploadAnalyzedRequest(
    instanceNumber : String,
    key : String,
    @Json(name = "changes")
    val analyzedNumbers : List<AnalyzedNumber>
) : KeyRequest(instanceNumber, key) {

    override fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(UploadAnalyzedRequest::class.java)

        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "${super.toString()} numAnalyzedNumbers = ${analyzedNumbers.size}" +
            "firstAnalyzedSent: ${analyzedNumbers.firstOrNull()?.normalizedNumber}"
    }
}

/**
 * Inherits from KeyRequest, request class for uploading CallDetails.
 */
@JsonClass(generateAdapter = true)
class UploadLogsRequest(
    instanceNumber : String,
    key : String,
    @Json(name = "changes")
    val logs : List<CallDetail>
) : KeyRequest(instanceNumber, key) {

    override fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(UploadLogsRequest::class.java)

        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "${super.toString()} numLogs = ${logs.size}" +
            "firstLogSent: ${logs.firstOrNull()?.normalizedNumber}"
    }
}

/**
 * Inherits from KeyRequest, request class for uploading CallDetails.
 */
@JsonClass(generateAdapter = true)
class UploadErrorRequest(
    instanceNumber : String,
    key : String,
    @Json(name = "changes")
    val errorLogs : List<ErrorQueue>
) : KeyRequest(instanceNumber, key) {

    override fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(UploadErrorRequest::class.java)

        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "${super.toString()} numErrors = ${errorLogs.size}" +
            "firstErrorSent: ${errorLogs.firstOrNull()?.serverRowID}"
    }
}

/**
 * Inherits from KeyRequest, request class for SMS_VERIFY verification.
 */
@JsonClass(generateAdapter = true)
class SMSVerifyRequest(
    instanceNumber : String,
    key : String,
    val number : String
) : KeyRequest(instanceNumber, key) {

    override fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(SMSVerifyRequest::class.java)

        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "${super.toString()} number: $number"
    }
}

@JsonClass(generateAdapter = true)
class DebugExchangeRequest(
    instanceNumber : String,
    key : String,
    val remoteSessionID : String,
    val data: List<String>,
    val commandComplete: Boolean,
    val error: String? = null
) : KeyRequest(instanceNumber, key) {

    override fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(DebugExchangeRequest::class.java)

        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return "${super.toString()} remoteSessionID: $remoteSessionID data: $data " +
            "commandComplete: $commandComplete"
    }
}

@JsonClass(generateAdapter = true)
class TokenRequest(
    instanceNumber : String,
    key : String,
    val token : String
) : KeyRequest(instanceNumber, key) {

    override fun toJson() : String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(TokenRequest::class.java)

        return adapter.serializeNulls().toJson(this)
    }

    override fun toString() : String {
        return super.toString() + " key: " + this.key + " token: " + this.token
    }
}