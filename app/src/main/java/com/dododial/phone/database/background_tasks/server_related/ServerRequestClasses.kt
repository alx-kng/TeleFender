package com.dododial.phone.database.background_tasks.server_related

import com.dododial.phone.database.entities.ChangeLog
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

// parse request - instanceNumber, key, changes: [{rowid, chgid, changeTime, type, cid, name, number}, ...]
//The first request containing only instance field
@JsonClass(generateAdapter = true)
open class DefaultRequest(
    val instanceNumber : String
    ) {
    override fun toString() : String {
        return "REQUEST - instanceNumber: " + this.instanceNumber
    }
}
// The second request to verify the user containing instanceNumber, sessionID, OTP
@JsonClass(generateAdapter = true)
class VerifyRequest(
    instanceNumber : String,
    val sessionID : String,
    val OTP : Int
) : DefaultRequest(instanceNumber) {

    override fun toString() : String {
        return super.toString() + " sessionID: " + this.sessionID + " OTP: " + this.OTP
    }
}

// A standard request parent class containing only instanceNumber and Key, inherited by Upload/Download requests
@JsonClass(generateAdapter = true)
open class KeyRequest(
    instanceNumber : String,
    val key : String,
) : DefaultRequest(instanceNumber) {

    override fun toString() : String {
        return super.toString() + " key: " + this.key
    }
}
// Inherits from KeyRequest, request class for upload
@JsonClass(generateAdapter = true)
class UploadRequest(
    instanceNumber : String,
    key : String,
    val changes : List<ChangeLog>
) : KeyRequest(instanceNumber, key) {

    override fun toString() : String {
        var changeLogString = ""
        for (changeLog in changes) {
            changeLogString += changeLog.toString()
        }
        return super.toString() + " key: " + this.key + " changeLogs: " + changeLogString
    }
}

// Inherits from KeyRequest, request class for download
@JsonClass(generateAdapter = true)
class DownloadRequest(
    instanceNumber : String,
    key : String,
    val lastChangeID : Int?
) : KeyRequest(instanceNumber, key) {

    override fun toString() : String {
        return super.toString() + " key: " + this.key + " lastChangeID: " + this.lastChangeID.toString()
    }
}

@JsonClass(generateAdapter = true)
class TokenRequest(
    instanceNumber : String,
    key : String,
    val token : String
) : KeyRequest(instanceNumber, key) {

    override fun toString() : String {
        return super.toString() + " key: " + this.key + " token: " + this.token
    }
}

object RequestHelpers {

    fun defaultRequestToJson(defaultRequest : DefaultRequest) : String {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<DefaultRequest> = moshi.adapter(DefaultRequest::class.java)

        return adapter.serializeNulls().toJson(defaultRequest)
    }

    fun verifyRequestToJson(verifyRequest : VerifyRequest) : String {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<VerifyRequest> = moshi.adapter(VerifyRequest::class.java)

        return adapter.serializeNulls().toJson(verifyRequest)
    }

    fun keyRequestToJson(keyRequest : KeyRequest) : String {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<KeyRequest> = moshi.adapter(KeyRequest::class.java)

        return adapter.serializeNulls().toJson(keyRequest)
    }
    
    fun uploadRequestToJson(uploadRequest : UploadRequest) : String {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<UploadRequest> = moshi.adapter(UploadRequest::class.java)

        return adapter.serializeNulls().toJson(uploadRequest)
    }

    fun downloadRequestToJson(downloadRequest : DownloadRequest) : String {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<DownloadRequest> = moshi.adapter(DownloadRequest::class.java)

        return adapter.serializeNulls().toJson(downloadRequest)
    }

    fun tokenRequestToJson(tokenRequest : TokenRequest) : String {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<TokenRequest> = moshi.adapter(TokenRequest::class.java)

        return adapter.serializeNulls().toJson(tokenRequest)
    }
}