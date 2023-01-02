package com.telefender.phone.data.server_related

import android.annotation.SuppressLint
import android.content.Context
import com.android.volley.Request
import com.telefender.phone.data.server_related.RequestHelpers.defaultRequestToJson
import com.telefender.phone.data.server_related.request_generators.InitialRequestGen
import com.telefender.phone.data.server_related.request_generators.VerifyRequestGen
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.CoroutineScope
import org.json.JSONException
import timber.log.Timber

object UserSetup {

    /**
     * TODO: Do Volley check for not being able to connect to server (e.g., 404)
     * TODO: Set retry amount.
     * TODO finalize url...
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    fun initialPostRequest(context : Context, repository: ClientRepository, scope: CoroutineScope) {
        val instanceNumber = MiscHelpers.getInstanceNumber(context)

        val requestJson : String = defaultRequestToJson(DefaultRequest(instanceNumber))
        val url = "https://dev.scribblychat.com/callbook/requestInstallation"

        try {
            val stringRequest = InitialRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = requestJson,
                context = context,
                repository = repository,
                scope = scope
            )

            // Adds entire string request to request queue
            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * TODO integrate OTP with notifications, for now, it's hardcoded.
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    suspend fun verifyPostRequest(context : Context, repository: ClientRepository, scope: CoroutineScope) {
        val instanceNumber = MiscHelpers.getInstanceNumber(context)

        val otp = 111111
        val sessionID = repository.getSessionID(instanceNumber)
        val verifyRequest = VerifyRequest(instanceNumber, sessionID!!, otp)
        val verifyRequestJson : String = RequestHelpers.verifyRequestToJson(verifyRequest)

        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VerifyRequestJson = $verifyRequestJson")

        val url = "https://dev.scribblychat.com/callbook/verifyInstallation"

        try {
            val stringRequest = VerifyRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = verifyRequestJson,
                repository = repository,
                scope = scope
            )

            // Adds entire string request to request queue
            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }
}


