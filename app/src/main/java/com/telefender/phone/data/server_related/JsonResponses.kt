package com.telefender.phone.data.server_related

import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi


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

/**
 * TODO: Modify into generic Download response to handle ChangeLogs, AnalyzedNumbers, and CallDetails.
 */
@JsonClass(generateAdapter = true) 
class ChangeResponse(
    status : String,
    error : String?,
    @Json(name= "changes")
    val changes : List<ChangeLog>
) : DefaultResponse(status, error) {

    override fun toString() : String {
        return "${super.toString()} numChanges = ${changes.size}" +
            "firstChangeReceived: ${changes.firstOrNull()?.changeID}"
    }

}

@JsonClass(generateAdapter = true)
class UploadResponse(
    status : String,
    error : String?,
    @Json(name = "rowID")
    val lastUploadedRowID : Int
) : DefaultResponse(status, error) {

    override fun toString() : String {
        return "${super.toString()} lastUploadedRowID: $lastUploadedRowID"
    }
}

enum class ServerResponseType {
    DEFAULT, SESSION, KEY, CHANGE, UPLOAD
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
            ServerResponseType.CHANGE -> ChangeResponse::class.java
            ServerResponseType.UPLOAD -> UploadResponse::class.java

        }
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(responseClass)

        adapter.serializeNulls().fromJson(this)
    } catch (e: Exception) {
        null
    }
}