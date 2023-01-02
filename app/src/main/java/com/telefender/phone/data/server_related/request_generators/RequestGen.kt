package com.telefender.phone.data.server_related.request_generators

import android.annotation.SuppressLint
import com.android.volley.AuthFailureError
import com.android.volley.Response
import com.android.volley.VolleyLog
import com.android.volley.toolbox.StringRequest
import java.io.UnsupportedEncodingException

abstract class RequestGen(
    method: Int,
    url: String,
    listener: Response.Listener<String>,
    errorListener: Response.ErrorListener,
    private val requestJson: String,
) : StringRequest(method, url, listener, errorListener) {

    @SuppressLint("HardwareIds")
    @Throws(AuthFailureError::class)
    override fun getBody(): ByteArray? {
        return try {
            requestJson.toByteArray(charset("utf-8"))
        } catch (uee: UnsupportedEncodingException) {
            VolleyLog.wtf(
                "Unsupported Encoding while trying to get the bytes of %s using %s",
                requestJson,
                "utf-8"
            )
            null
        }
    }

    override fun getBodyContentType(): String {
        return "application/json; charset=utf-8"
    }
}
