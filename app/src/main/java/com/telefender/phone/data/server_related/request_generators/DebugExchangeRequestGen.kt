package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.debug_engine.DebugEngine
import com.telefender.phone.data.server_related.json_classes.*
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber


class DebugExchangeRequestGen(
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
        ) : DebugExchangeRequestGen {

            return DebugExchangeRequestGen(
                method = method,
                url = url,
                listener = debugExchangeResponseHandler(context, repository, scope),
                errorListener = debugExchangeErrorHandler,
                requestJson = requestJson
            )
        }
    }
}

private fun debugExchangeResponseHandler(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope
) : Response.Listener<String> {

    return Response.Listener<String> { response : String->
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DEBUG EXCHANGE RESPONSE %s", response)

        val debugExchangeResponse : DefaultResponse? =
            response.toServerResponse(ServerResponseType.DEBUG_EXCHANGE) ?:
            response.toServerResponse(ServerResponseType.DEFAULT)

        if (debugExchangeResponse != null && debugExchangeResponse.status == "ok" && debugExchangeResponse is DebugExchangeResponse) {

            /*
            Lets DebugEngine handle command, which will execute the command and add the returned
            data to the dataQueue in RemoteDebug. DebugEngine WILL ALSO launch the next
            debugExchangeRequest() once the execution finishes and set the SUCCESS state of
            DEBUG_EXCHANGE_POST if it detects a debug END command.
             */
            scope.launch(Dispatchers.IO) {
                DebugEngine.execute(
                    context = context,
                    repository = repository,
                    scope = scope,
                    commandString = debugExchangeResponse.command
                )
            }
        } else {
            WorkStates.setState(WorkType.DEBUG_EXCHANGE_POST, WorkInfo.State.FAILED)

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN DEBUG EXCHANGE: ${debugExchangeResponse?.error}")
        }
    }
}

private val debugExchangeErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY $error")
        WorkStates.setState(WorkType.DEBUG_EXCHANGE_POST, WorkInfo.State.FAILED)
    }
}