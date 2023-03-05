package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.RemoteDebug
import com.telefender.phone.data.server_related.RequestWrappers
import com.telefender.phone.data.server_related.debug_engine.DebugEngine
import com.telefender.phone.data.server_related.json_classes.DebugExchangeResponse
import com.telefender.phone.data.server_related.json_classes.DefaultResponse
import com.telefender.phone.data.server_related.json_classes.ServerResponseType
import com.telefender.phone.data.server_related.json_classes.toServerResponse
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
            scope: CoroutineScope,
            workerName: String
        ) : DebugExchangeRequestGen {

            return DebugExchangeRequestGen(
                method = method,
                url = url,
                listener = debugExchangeResponseHandler(context, repository, scope, workerName),
                errorListener = debugExchangeErrorHandler,
                requestJson = requestJson,
            )
        }
    }
}

// TODO: Check handling of InvToken error and see if infinite loop is possible.
private fun debugExchangeResponseHandler(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope,
    workerName: String
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
                // Resets error counter. Success means that you get retryAmount more tries.
                RemoteDebug.resetExchangeCounters()

                DebugEngine.execute(
                    context = context,
                    repository = repository,
                    scope = scope,
                    commandString = debugExchangeResponse.command,
                    workerName = workerName
                )
            }
        } else if (debugExchangeResponse != null && debugExchangeResponse.error == "InvToken"){
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: DEBUG EXCHANGE - NEED NEW TOKEN: ${debugExchangeResponse.error}")

            scope.launch(Dispatchers.IO) {
                if (RemoteDebug.invTokenCounter >= RemoteDebug.retryAmount) {
                    Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: %s",
                        "VOLLEY: DEBUG EXCHANGE - STOPPING - TOO MANY INV TOKEN ERRORS!")

                    WorkStates.setState(WorkType.DEBUG_EXCHANGE_POST, WorkInfo.State.FAILED)
                    return@launch
                }

                RemoteDebug.incrementInvTokenCounter()

                // Wait a little before retrying to give server a little time to get back online.
                delay(RequestWrappers.retryDelayTime)

                RequestWrappers.debugSession(
                    context = context,
                    repository = repository,
                    scope = scope,
                    workerName = "$workerName - debugExchangeResponse"
                )

                RemoteDebug.debugExchangeRequest(context, repository, scope, workerName)
            }
        } else {
            WorkStates.setState(WorkType.DEBUG_EXCHANGE_POST, WorkInfo.State.FAILED)

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: DEBUG EXCHANGE - ERROR: ${debugExchangeResponse?.error}")
        }
    }
}

private val debugExchangeErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY $error")
        WorkStates.setState(WorkType.DEBUG_EXCHANGE_POST, WorkInfo.State.FAILED)
    }
}