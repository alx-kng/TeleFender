package com.telefender.phone.data.server_related

import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import timber.log.Timber

@JsonClass(generateAdapter = true)
open class DefaultResponse (
    val status : String,
    val error : String?
) {
    override fun toString() : String {
        return "RESPONSE - status: " + this.status + " error: " + this.error
    }
}

@JsonClass(generateAdapter = true)
class SessionResponse(
    status : String,
    error : String?,
    val sessionID : String?
) : DefaultResponse(status, error) {
    override fun toString() : String {
        return super.toString() + " sessionID: " + this.sessionID
    }
}

@JsonClass(generateAdapter = true)
class KeyResponse(
    status: String,
    error: String?,
    val key: String
) : DefaultResponse(status, error)  {
    override fun toString() : String {
        return (super.toString() + " key: " + this.key)
    }
}

@JsonClass(generateAdapter = true) 
class ChangeResponse(
    status : String,
    error : String?,
    @Json(name= "changes")
    val changeLogs : List<ChangeLog>
) : DefaultResponse(status, error) {

    override fun toString() : String {
        var changeLogString = ""
        for (changeLog in changeLogs) {
            changeLogString += changeLog.toString()
        }
        return super.toString() + " changeLogs: " + changeLogString
    }

}

@JsonClass(generateAdapter = true)
class UploadResponse(
    status : String,
    error : String?,
    @Json(name = "rowID")
    val lastUploadRow : Int
) : DefaultResponse(status, error) {

    override fun toString() : String {
        return super.toString() + " lastUploadRow: " + this.lastUploadRow
    }
}


object ResponseHelpers {

    fun jsonToDefaultResponse(jsonIn : String) : DefaultResponse? {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<DefaultResponse> = moshi.adapter(DefaultResponse::class.java)
        Timber.i("default response called")

       return adapter.serializeNulls().fromJson(jsonIn)
    }

    fun jsonToKeyResponse(jsonIn : String) : KeyResponse? {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<KeyResponse> = moshi.adapter(KeyResponse::class.java)

        return adapter.serializeNulls().fromJson(jsonIn)
    }

    fun jsonToChangeResponse(jsonIn : String) : ChangeResponse? {

        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<ChangeResponse> = moshi.adapter(ChangeResponse::class.java)
        Timber.i("change response called")
        return adapter.serializeNulls().fromJson(jsonIn)
    }

    fun jsonToSessionResponse(jsonIn : String) : SessionResponse? {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<SessionResponse> = moshi.adapter(SessionResponse::class.java)

        return adapter.serializeNulls().fromJson(jsonIn)
    }

    fun jsonToUploadResponse(jsonIn : String) : UploadResponse? {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<UploadResponse> = moshi.adapter(UploadResponse::class.java)

        return adapter.serializeNulls().fromJson(jsonIn)
    }
}