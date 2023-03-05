package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.RemoteDebug
import com.telefender.phone.data.server_related.json_classes.*
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
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
                errorListener = debugSessionErrorHandler,
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
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DEBUG SESSION RESPONSE %s", response)

        val debugSessionResponse : DefaultResponse? =
            response.toServerResponse(ServerResponseType.DEBUG_SESSION) ?:
            response.toServerResponse(ServerResponseType.DEFAULT)

        if (debugSessionResponse != null && debugSessionResponse.status == "ok" && debugSessionResponse is DebugSessionResponse) {
            // Set RemoteDebug remoteSessionID and remoteSessionToken (used in debugExchangeRequest())
            RemoteDebug.remoteSessionID = debugSessionResponse.remoteSessionID
            RemoteDebug.remoteSessionToken = debugSessionResponse.remoteSessionToken

            WorkStates.setState(WorkType.DEBUG_SESSION_POST, WorkInfo.State.SUCCEEDED)
        } else {
            WorkStates.setState(WorkType.DEBUG_SESSION_POST, WorkInfo.State.FAILED)

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN GET DEBUG SESSION: ${debugSessionResponse?.error}")
        }
    }
}

private val debugSessionErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY $error")
        WorkStates.setState(WorkType.DEBUG_SESSION_POST, WorkInfo.State.FAILED)
    }
}