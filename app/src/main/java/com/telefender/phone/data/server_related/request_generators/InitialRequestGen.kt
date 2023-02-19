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
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
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
                method = method,
                url = url,
                listener = initialPostResponseHandler(context, repository, scope),
                errorListener = initialPostErrorHandler,
                requestJson = requestJson
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
         * Basically, if a normal session response is returned, then we will be able to convert
         * JSON into SetupSessionResponse. Otherwise, the response is probably an error JSON, in
         * which we can convert to DefaultResponse.
         */
        val setupSessionResponse : DefaultResponse? =
            response.toServerResponse(ServerResponseType.SETUP_SESSION) ?:
            response.toServerResponse(ServerResponseType.DEFAULT)

        if (setupSessionResponse != null && setupSessionResponse.status == "ok" && setupSessionResponse is SetupSessionResponse) {
            /**
             * Create StoredMap row containing instance number and sessionID
             */
            scope.launch(Dispatchers.IO) {
                repository.updateStoredMap(sessionID = setupSessionResponse.sessionID)

                val sessionID = repository.getSessionID()
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: sessionID = $sessionID")

                UserSetup.verifyPostRequest(context, repository, scope)
            }
        } else {
            WorkStates.setState(WorkType.SETUP, WorkInfo.State.FAILED)

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN REQUEST INSTALLATION: ${setupSessionResponse?.error}")
        }
    }
}

private val initialPostErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY $error")
        WorkStates.setState(WorkType.SETUP, WorkInfo.State.FAILED)
    }
}
