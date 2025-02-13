package com.telefender.phone.data.server_related.json_classes

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.telefender.phone.data.server_related.RemoteDebug.remoteSessionID
import com.telefender.phone.data.tele_database.entities.*
import com.telefender.phone.data.tele_database.entities.AnalyzedNumber
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.data.tele_database.entities.CallDetail


// TODO: Clean up these classes!!!

@JsonClass(generateAdapter = true)
open class DefaultResponse (
    val status : String?,
    val error : String?
) {
    override fun toString() : String {
        return "RESPONSE - status: $status error: $error"
    }
}

@JsonClass(generateAdapter = true)
class SetupSessionResponse(
    status : String?,
    error : String?,
    val sessionID : String
) : DefaultResponse(status, error) {

    override fun toString() : String {
        return "${super.toString()} sessionID: $sessionID"
    }
}

@JsonClass(generateAdapter = true)
class KeyResponse(
    status: String?,
    error: String?,
    val key: String
) : DefaultResponse(status, error)  {

    override fun toString() : String {
        return "${super.toString()} key: $key"
    }
}

@JsonClass(generateAdapter = true)
class DownloadResponse(
    status: String?,
    error: String?,
    @Json(name= "data")
    val data: List<ServerData>
) : DefaultResponse(status, error) {

    override fun toString(): String {
        return "${super.toString()} numData = ${data.size}" +
            "firstDataReceived: ${data.firstOrNull()?.type}"
    }
}

@JsonClass(generateAdapter = true)
data class ServerData(
    val type: String,
    val serverRowID: Long,
    val changeLog: ChangeLog? = null,
    val analyzedNumber: AnalyzedNumber? = null,
    val callDetail: CallDetail? = null
) {
    /**
     * Returns [type] as a GenericDataType if possible.
     */
    fun getGenericDataType() : GenericDataType? {
        return type.toGenericDataType()
    }
}

@JsonClass(generateAdapter = true)
class UploadResponse(
    status : String?,
    error : String?,
    @Json(name = "rowID")
    val lastUploadedRowID : Long
) : DefaultResponse(status, error) {

    override fun toString() : String {
        return "${super.toString()} lastUploadedRowID: $lastUploadedRowID"
    }
}

@JsonClass(generateAdapter = true)
class SMSVerifyResponse(
    status : String?,
    error : String?,
    val number : String,
    val verified: Boolean,
    val smsSent: Boolean
) : DefaultResponse(status, error) {

    override fun toString() : String {
        return "${super.toString()} number: $number verified: $verified"
    }
}

@JsonClass(generateAdapter = true)
class DebugCheckResponse(
    status : String?,
    error : String?,
    val isEnabled : Boolean
) : DefaultResponse(status, error) {

    override fun toString() : String {
        return "${super.toString()} isEnabled: $isEnabled"
    }
}

@JsonClass(generateAdapter = true)
class DebugSessionResponse(
    status : String?,
    error : String?,
    val remoteSessionID : String,
    @Json(name = "rjsToken")
    val remoteSessionToken: String
) : DefaultResponse(status, error) {

    override fun toString() : String {
        return "${super.toString()} remoteSessionID: $remoteSessionID rjsToken = $remoteSessionToken"
    }
}

@JsonClass(generateAdapter = true)
class DebugExchangeResponse(
    status : String?,
    error : String?,
    val command : String?
) : DefaultResponse(status, error) {

    override fun toString() : String {
        return "${super.toString()} command: $command"
    }
}

enum class ServerResponseType {
    DEFAULT, SETUP_SESSION, KEY, DOWNLOAD, UPLOAD, SMS_VERIFY, DEBUG_CHECK, DEBUG_SESSION,
    DEBUG_EXCHANGE,
}

/**
 * Need to put try-catch around any sort of Moshi string-to-object function.
 */
fun String.toServerResponse(type: ServerResponseType) : DefaultResponse? {
    return try {
        val responseClass = when(type) {
            ServerResponseType.DEFAULT -> DefaultResponse::class.java
            ServerResponseType.SETUP_SESSION -> SetupSessionResponse::class.java
            ServerResponseType.KEY -> KeyResponse::class.java
            ServerResponseType.DOWNLOAD -> DownloadResponse::class.java
            ServerResponseType.UPLOAD -> UploadResponse::class.java
            ServerResponseType.SMS_VERIFY -> SMSVerifyResponse::class.java
            ServerResponseType.DEBUG_CHECK -> DebugCheckResponse::class.java
            ServerResponseType.DEBUG_SESSION -> DebugSessionResponse::class.java
            ServerResponseType.DEBUG_EXCHANGE -> DebugExchangeResponse::class.java
        }
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(responseClass)

        adapter.serializeNulls().fromJson(this)
    } catch (e: Exception) {
        null
    }
}