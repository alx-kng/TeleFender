package com.telefender.phone.data.server_related

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.telefender.phone.data.tele_database.entities.*


// TODO: Clean up these classes!!!

@JsonClass(generateAdapter = true)
open class DefaultResponse (
    val status : String,
    val error : String?
) {
    override fun toString() : String {
        return "RESPONSE - status: $status error: $error"
    }
}

@JsonClass(generateAdapter = true)
class SessionResponse(
    status : String,
    error : String?,
    val sessionID : String?
) : DefaultResponse(status, error) {
    override fun toString() : String {
        return "${super.toString()} sessionID: $sessionID"
    }
}

@JsonClass(generateAdapter = true)
class KeyResponse(
    status: String,
    error: String?,
    val key: String
) : DefaultResponse(status, error)  {
    override fun toString() : String {
        return "${super.toString()} key: $key"
    }
}

@JsonClass(generateAdapter = true)
class DownloadResponse(
    status: String,
    error: String?,
    @Json(name= "data")
    val data: List<GenericData>
) : DefaultResponse(status, error) {

    override fun toString(): String {
        return "${super.toString()} numData = ${data.size}" +
            "firstDataReceived: ${data.firstOrNull()?.type}"
    }
}

@JsonClass(generateAdapter = true)
data class GenericData(
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

enum class GenericDataType(val serverString: String) {
    CHANGE_DATA("CHNG"), ANALYZED_DATA("ANUM"), LOG_DATA("CLOG")
}

/**
 * Converts serverStr to ChangeType if possible.
 */
fun String.toGenericDataType() : GenericDataType? {
    for (genericDataType in GenericDataType.values()) {
        if (this == genericDataType.serverString) {
            return genericDataType
        }
    }

    return null
}

@JsonClass(generateAdapter = true)
class UploadResponse(
    status : String,
    error : String?,
    @Json(name = "rowID")
    val lastUploadedRowID : Long
) : DefaultResponse(status, error) {

    override fun toString() : String {
        return "${super.toString()} lastUploadedRowID: $lastUploadedRowID"
    }
}

enum class ServerResponseType {
    DEFAULT, SESSION, KEY, DOWNLOAD, UPLOAD
}

/**
 * Need to put try-catch around any sort of Moshi string-to-object function.
 */
fun String.toServerResponse(type: ServerResponseType) : DefaultResponse? {
    return try {
        val responseClass = when(type) {
            ServerResponseType.DEFAULT -> DefaultResponse::class.java
            ServerResponseType.SESSION -> SessionResponse::class.java
            ServerResponseType.KEY -> KeyResponse::class.java
            ServerResponseType.DOWNLOAD -> DownloadResponse::class.java
            ServerResponseType.UPLOAD -> UploadResponse::class.java
        }
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(responseClass)

        adapter.serializeNulls().fromJson(this)
    } catch (e: Exception) {
        null
    }
}