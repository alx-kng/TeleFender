package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.json_classes.DefaultResponse
import com.telefender.phone.data.server_related.json_classes.ServerResponseType
import com.telefender.phone.data.server_related.json_classes.toServerResponse
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.ExperimentalWorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.misc_helpers.DBL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                errorListener = debugCallStateErrorHandler(scope),
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

        scope.launch(Dispatchers.IO) {
            if (debugCallStateResponse != null && debugCallStateResponse.status == "ok") {
                ExperimentalWorkStates.generalizedSetState(WorkType.DEBUG_CALL_STATE_POST, null)
            } else {
                ExperimentalWorkStates.generalizedSetState(WorkType.DEBUG_CALL_STATE_POST, WorkInfo.State.FAILED)

                Timber.i("$DBL: VOLLEY: ERROR WHEN DEBUG CALL_STATE: ${debugCallStateResponse?.error}")
            }
        }
    }
}

private fun debugCallStateErrorHandler(scope: CoroutineScope) = Response.ErrorListener { error ->
    scope.launch(Dispatchers.IO) {
        if (error.toString() != "null") {
            Timber.e("$DBL: VOLLEY $error")
            ExperimentalWorkStates.generalizedSetState(WorkType.DEBUG_CALL_STATE_POST, WorkInfo.State.FAILED)
        }
    }
}