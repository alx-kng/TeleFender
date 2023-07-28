package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.RemoteDebug
import com.telefender.phone.data.server_related.json_classes.*
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.ExperimentalWorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber


class DebugSessionRequestGen(
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
        ) : DebugSessionRequestGen {

            return DebugSessionRequestGen(
                method = method,
                url = url,
                listener = debugSessionResponseHandler(context, repository, scope),
                errorListener = debugSessionErrorHandler(scope),
                requestJson = requestJson
            )
        }
    }
}

private fun debugSessionResponseHandler(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope
) : Response.Listener<String> {

    return Response.Listener<String> { response : String->
        Timber.i("$DBL: DEBUG SESSION RESPONSE %s", response)

        val debugSessionResponse : DefaultResponse? =
            response.toServerResponse(ServerResponseType.DEBUG_SESSION) ?:
            response.toServerResponse(ServerResponseType.DEFAULT)

        if (debugSessionResponse != null && debugSessionResponse.status == "ok" && debugSessionResponse is DebugSessionResponse) {
            // Set RemoteDebug remoteSessionID and remoteSessionToken (used in debugExchangeRequest())
            RemoteDebug.remoteSessionID = debugSessionResponse.remoteSessionID
            RemoteDebug.remoteSessionToken = debugSessionResponse.remoteSessionToken

            scope.launch(Dispatchers.IO) {
                ExperimentalWorkStates.generalizedSetState(WorkType.DEBUG_SESSION_POST, null)
            }
        } else {
            scope.launch(Dispatchers.IO) {
                ExperimentalWorkStates.generalizedSetState(WorkType.DEBUG_SESSION_POST, WorkInfo.State.FAILED)

                Timber.i("$DBL: VOLLEY: ERROR WHEN GET DEBUG SESSION: ${debugSessionResponse?.error}")
            }
        }
    }
}

private fun debugSessionErrorHandler(scope: CoroutineScope) = Response.ErrorListener { error ->
    scope.launch(Dispatchers.IO) {
        if (error.toString() != "null") {
            Timber.e("$DBL: VOLLEY $error")
            ExperimentalWorkStates.generalizedSetState(WorkType.DEBUG_SESSION_POST, WorkInfo.State.FAILED)
        }
    }
}