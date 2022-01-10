package com.dododial.phone.database

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.volley.AuthFailureError
import com.android.volley.NetworkResponse
import com.android.volley.Response
import com.android.volley.VolleyLog
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.json.JSONException
import java.io.UnsupportedEncodingException

object UploadHelpers {

    /**
     * Takes ChangeLog object and returns its JSON representation
     */
    fun changelogToJson(changeLog : ChangeLog) : String {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<ChangeLog> = moshi.adapter(ChangeLog::class.java)

        val json = adapter.toJson(changeLog)
        return json
    }

    /**
     * Takes JSON representation of ChangeLog object and returns its corresponding ChangeLog object
     */
    fun jsonToChangeLog(jsonIn : String) : ChangeLog? {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<ChangeLog> = moshi.adapter(ChangeLog::class.java)

        return adapter.fromJson(jsonIn)
    }

    /**
     * Sends post request containing the string requestString to the server at url url.
     * Returns whether or not post was successfull
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun sendPostRequest(requestString: String, url: String, context: Context) {

        try {
            val requestQueue = Volley.newRequestQueue(context)
            val URL = url
            val requestBody = requestString
            val stringRequest: StringRequest = object : StringRequest(
                Method.POST, URL,
                Response.Listener { response -> Log.i("VOLLEY", response!!) },
                Response.ErrorListener { error -> Log.e("VOLLEY", error.toString()) }) {
                override fun getBodyContentType(): String {
                    return "application/json; charset=utf-8"
                }

                @Throws(AuthFailureError::class)
                /**
                 * TODO what does this do
                 */
                override fun getBody(): ByteArray? {
                    try {
                        return if (requestBody == null) null else requestBody.toByteArray(charset("utf-8"))
                    } catch (uee: UnsupportedEncodingException) {
                        VolleyLog.wtf(
                            "Unsupported Encoding while trying to get the bytes of %s using %s",
                            requestBody,
                            "utf-8"
                        )
                        return null
                    }
                }

                /**
                 * TODO what does this do
                 */
                override fun parseNetworkResponse(response: NetworkResponse): Response<String> {
                    var responseString = ""
                    if (response != null) {
                        responseString = response.statusCode.toString()
                        // can get more details such as response.headers
                    }
                    return Response.success(
                        responseString,
                        HttpHeaderParser.parseCacheHeaders(response)
                    )
                }
            }
            requestQueue.add(stringRequest)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }
}