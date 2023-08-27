package com.telefender.phone.data.server_related

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Request
import com.telefender.phone.data.server_related.json_classes.DefaultRequest
import com.telefender.phone.data.server_related.json_classes.VerifyRequest
import com.telefender.phone.data.server_related.request_generators.InitialRequestGen
import com.telefender.phone.data.server_related.request_generators.VerifyRequestGen
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.ExperimentalWorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.SharedPreferenceHelpers
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import org.json.JSONException
import timber.log.Timber

object UserSetup {

    /**
     * TODO: Do Volley check for not being able to connect to server (e.g., 404)
     * TODO: Set retry amount.
     * TODO finalize url...
     */
    fun initialPostRequest(
        context : Context,
        scope: CoroutineScope,
        instanceNumber: String
    ) {
        val url = SharedPreferenceHelpers.getServerModeUrl(
            context = context,
            baseURL = "scribblychat.com/callbook/requestInstallation"
        )

        val requestJson : String = DefaultRequest(instanceNumber).toJson()

        Timber.i("$DBL: initialRequestJson = $requestJson")

        try {
            val stringRequest = InitialRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = requestJson,
                context = context,
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
    suspend fun verifyPostRequest(
        context : Context,
        scope: CoroutineScope,
        otp: Int
    ) {
        val url = SharedPreferenceHelpers.getServerModeUrl(
            context = context,
            baseURL = "scribblychat.com/callbook/verifyInstallation"
        )
        val instanceNumber = SharedPreferenceHelpers.getInstanceNumber(context)
        val sessionID = SharedPreferenceHelpers.getSessionID(context)

        if (instanceNumber == null || sessionID == null) {
            Timber.i("$DBL: " +
                "VOLLEY: ERROR - INSTANCE NUMBER = $instanceNumber | SETUP_SESSION ID = $sessionID")

            ExperimentalWorkStates.generalizedSetState(WorkType.VERIFY_POST, WorkInfo.State.FAILED)
            return
        }

        val verifyRequestJson = VerifyRequest(instanceNumber, sessionID, otp).toJson()

        Timber.i("$DBL: verifyRequestJson = $verifyRequestJson")

        try {
            val stringRequest = VerifyRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = verifyRequestJson,
                context = context,
                scope = scope
            )

            // Adds entire string request to request queue
            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }
}


