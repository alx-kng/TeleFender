package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.json_classes.DefaultResponse
import com.telefender.phone.data.server_related.json_classes.ServerResponseType
import com.telefender.phone.data.server_related.json_classes.toServerResponse
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.misc_helpers.DBL
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber


class DebugCallStateRequestGen(
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
        ) : DebugCallStateRequestGen {

            return DebugCallStateRequestGen(
                method = method,
                url = url,
                listener = debugCallStateResponseHandler(context, repository, scope),
                errorListener = debugCallStateErrorHandler,
                requestJson = requestJson
            )
        }
    }
}

private fun debugCallStateResponseHandler(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope
) : Response.Listener<String> {

    return Response.Listener<String> { response : String->
        Timber.i("$DBL: DEBUG CALL_STATE RESPONSE %s", response)

        val debugCallStateResponse : DefaultResponse? = response.toServerResponse(ServerResponseType.DEFAULT)

        if (debugCallStateResponse != null && debugCallStateResponse.status == "ok") {
            WorkStates.setState(WorkType.DEBUG_CALL_STATE_POST, WorkInfo.State.SUCCEEDED)
        } else {
            WorkStates.setState(WorkType.DEBUG_CALL_STATE_POST, WorkInfo.State.FAILED)

            Timber.i("$DBL: VOLLEY: ERROR WHEN DEBUG CALL_STATE: ${debugCallStateResponse?.error}")
        }
    }
}

private val debugCallStateErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("$DBL: VOLLEY $error")
        WorkStates.setState(WorkType.DEBUG_CALL_STATE_POST, WorkInfo.State.FAILED)
    }
}