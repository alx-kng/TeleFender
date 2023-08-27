package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.*
import com.telefender.phone.data.server_related.json_classes.DefaultResponse
import com.telefender.phone.data.server_related.json_classes.ServerResponseType
import com.telefender.phone.data.server_related.json_classes.SetupSessionResponse
import com.telefender.phone.data.server_related.json_classes.toServerResponse
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.ExperimentalWorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.SharedPreferenceHelpers
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber


class InitialRequestGen(
    method: Int,
    url: String,
    listener: Response.Listener<String>,
    errorListener: Response.ErrorListener,
    requestJson: String?,
) : RequestGen(method, url, listener, errorListener, requestJson) {

    companion object {
        fun create(
            method: Int,
            url: String,
            requestJson: String?,
            context: Context,
            scope: CoroutineScope
        ) : InitialRequestGen {

            return InitialRequestGen(
                method = method,
                url = url,
                listener = initialPostResponseHandler(context, scope),
                errorListener = initialPostErrorHandler(scope),
                requestJson = requestJson
            )
        }
    }
}

private fun initialPostResponseHandler(
    context: Context,
    scope: CoroutineScope
) : Response.Listener<String> {

    return Response.Listener<String> { response : String->
        Timber.i("VOLLEY: FIRST RESPONSE %s", response)

        /**
         * Basically, if a normal session response is returned, then we will be able to convert
         * JSON into SetupSessionResponse. Otherwise, the response is probably an error JSON, in
         * which we can convert to DefaultResponse.
         */
        val setupSessionResponse : DefaultResponse? =
            response.toServerResponse(ServerResponseType.SETUP_SESSION) ?:
            response.toServerResponse(ServerResponseType.DEFAULT)

        if (setupSessionResponse != null && setupSessionResponse.status == "ok" && setupSessionResponse is SetupSessionResponse) {
            /**
             * Store sessionID in SharedPreferences. Used for verify post request.
             */
            scope.launch(Dispatchers.IO) {
                SharedPreferenceHelpers.setSessionID(context, setupSessionResponse.sessionID)

                val sessionID = SharedPreferenceHelpers.getSessionID(context)
                Timber.i("$DBL: sessionID = $sessionID")

                ExperimentalWorkStates.generalizedSetState(WorkType.INITIAL_POST, null)
            }
        } else {
            scope.launch(Dispatchers.IO) {
                ExperimentalWorkStates.generalizedSetState(WorkType.INITIAL_POST, WorkInfo.State.FAILED)

                Timber.i("$DBL: VOLLEY: ERROR WHEN REQUEST INSTALLATION: ${setupSessionResponse?.error}")
            }
        }
    }
}

private fun initialPostErrorHandler(scope: CoroutineScope) = Response.ErrorListener { error ->
    scope.launch(Dispatchers.IO) {
        if (error.toString() != "null") {
            Timber.e("$DBL: VOLLEY $error")
            ExperimentalWorkStates.generalizedSetState(WorkType.INITIAL_POST, WorkInfo.State.FAILED)
        }
    }
}
