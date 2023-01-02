package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.DefaultResponse
import com.telefender.phone.data.server_related.ResponseHelpers
import com.telefender.phone.data.server_related.SessionResponse
import com.telefender.phone.data.server_related.UserSetup
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkerStates
import com.telefender.phone.data.tele_database.background_tasks.WorkerType
import com.telefender.phone.helpers.MiscHelpers
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
            repository: ClientRepository,
            scope: CoroutineScope
        ) : InitialRequestGen {

            return InitialRequestGen(
                method,
                url,
                initialPostResponseHandler(context, repository, scope),
                initialPostErrorHandler,
                requestJson
            )
        }
    }
}

private fun initialPostResponseHandler(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope
) : Response.Listener<String> {

    return Response.Listener<String> { response : String->
        Timber.i("VOLLEY: FIRST RESPONSE %s", response)

        /**
         * TODO: Can we just check for null instead of exception? If not, then
         *  replace with old code from previous commit.
         *
         * Basically, if a normal session response is returned, then we will be
         * able to convert JSON into SessionResponse. Otherwise, the response is
         * probably an error JSON, in which we can convert to DefaultResponse.
         */
        val sessionResponse : DefaultResponse? =
            ResponseHelpers.jsonToSessionResponse(response) ?:
            ResponseHelpers.jsonToDefaultResponse(response)

        if (sessionResponse != null && sessionResponse.status == "ok" && sessionResponse is SessionResponse) {
            /**
             * Create StoredMap row containing instance number and sessionID
             */
            scope.launch(Dispatchers.IO) {
                val instanceNumber = repository.getInstanceNumber()!!
                repository.updateStoredMap(instanceNumber, sessionID = sessionResponse.sessionID)

                val sessionID = repository.getSessionID(instanceNumber)
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: sessionID = $sessionID")

                UserSetup.verifyPostRequest(context, repository, scope)
            }
        } else {
            WorkerStates.setState(WorkerType.SETUP, WorkInfo.State.FAILED)

            if (sessionResponse != null) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN REQUEST INSTALLATION: %s", sessionResponse.error)
            } else {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN REQUEST INSTALLATION: SESSION RESPONSE IS NULL")
            }
        }
    }
}

private val initialPostErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("VOLLEY %s", error.toString())
        WorkerStates.setState(WorkerType.SETUP, WorkInfo.State.FAILED)
    }
}
