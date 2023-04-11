package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.RemoteDebug
import com.telefender.phone.data.server_related.json_classes.DebugCheckResponse
import com.telefender.phone.data.server_related.json_classes.DefaultResponse
import com.telefender.phone.data.server_related.json_classes.ServerResponseType
import com.telefender.phone.data.server_related.json_classes.toServerResponse
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber


class DebugCheckRequestGen(
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
        ) : DebugCheckRequestGen {

            return DebugCheckRequestGen(
                method = method,
                url = url,
                listener = debugCheckResponseHandler(context, repository, scope),
                errorListener = debugCheckErrorHandler,
                requestJson = requestJson
            )
        }
    }
}

private fun debugCheckResponseHandler(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope
) : Response.Listener<String> {

    return Response.Listener<String> { response : String->
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DEBUG CHECK RESPONSE %s", response)

        val debugCheckResponse : DefaultResponse? =
            response.toServerResponse(ServerResponseType.DEBUG_CHECK) ?:
            response.toServerResponse(ServerResponseType.DEFAULT)

        if (debugCheckResponse != null && debugCheckResponse.status == "ok" && debugCheckResponse is DebugCheckResponse) {
            // Set RemoteDebug enabled value, which controls whether debugSessionRequest() launches.
            RemoteDebug.isEnabled = debugCheckResponse.isEnabled

            WorkStates.setState(WorkType.DEBUG_CHECK_POST, WorkInfo.State.SUCCEEDED)
        } else {
            WorkStates.setState(WorkType.DEBUG_CHECK_POST, WorkInfo.State.FAILED)

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN DEBUG CHECK: ${debugCheckResponse?.error}")
        }
    }
}

private val debugCheckErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY $error")
        WorkStates.setState(WorkType.DEBUG_CHECK_POST, WorkInfo.State.FAILED)
    }
}