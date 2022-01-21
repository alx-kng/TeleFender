package com.dododial.phone.database.background_tasks.firebase

import com.dododial.phone.database.background_tasks.server_related.DownloadRequest
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
class FirebaseWrappedMessage(
    val message : FirebaseMessage
)

data class FirebaseMessage(
    val token : String,
    val data : Map<String, String>
)
@Deprecated("unnecessary (I think)")
object FirebaseClassHelpers {

    fun jsonToFirebaseMessage(jsonIn : String) : FirebaseMessage { // TODO do we have to deal with the possibility that this is null and !! crashes
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<FirebaseWrappedMessage> = moshi.adapter(FirebaseWrappedMessage::class.java)

        val wrappedMessage : FirebaseWrappedMessage? = adapter.serializeNulls().fromJson(jsonIn)
        return wrappedMessage!!.message
    }
}