    package com.dododial.phone.database.background_tasks.server_related

import com.dododial.phone.database.entities.ChangeLog
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

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

//class SessionJsonAdapter {
//    @FromJson
//    fun sessionFromJson(sessionJson)
//}

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