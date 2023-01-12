package com.telefender.phone.data.server_related

import android.annotation.SuppressLint
import android.content.Context
import com.android.volley.Request
import com.telefender.phone.data.server_related.request_generators.InitialRequestGen
import com.telefender.phone.data.server_related.request_generators.VerifyRequestGen
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.helpers.TeleHelpers
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
    suspend fun initialPostRequest(context : Context, repository: ClientRepository, scope: CoroutineScope) {
        val url = "https://dev.scribblychat.com/callbook/requestInstallation"
        val instanceNumber = TeleHelpers.getUserNumberStored(context) ?: return

        val requestJson : String = DefaultRequest(instanceNumber).toJson()

        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: initialRequestJson = $requestJson")

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
        val url = "https://dev.scribblychat.com/callbook/verifyInstallation"
        val instanceNumber = TeleHelpers.getUserNumberStored(context) ?: return

        val otp = 111111
        val sessionID = repository.getSessionID()
        val verifyRequestJson = VerifyRequest(instanceNumber, sessionID!!, otp).toJson()

        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: verifyRequestJson = $verifyRequestJson")

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


