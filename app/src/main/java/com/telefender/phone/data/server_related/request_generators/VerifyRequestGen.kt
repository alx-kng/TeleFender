package com.telefender.phone.data.server_related.request_generators

import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.json_classes.DefaultResponse
import com.telefender.phone.data.server_related.json_classes.KeyResponse
import com.telefender.phone.data.server_related.json_classes.ServerResponseType
import com.telefender.phone.data.server_related.json_classes.toServerResponse
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


class VerifyRequestGen(
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
            repository: ClientRepository,
            scope: CoroutineScope
        ) : VerifyRequestGen {

            return VerifyRequestGen(
                method = method,
                url = url,
                listener = verifyPostResponseHandler(repository, scope),
                errorListener = verifyPostErrorHandler(scope),
                requestJson = requestJson
            )
        }
    }
}

private fun verifyPostResponseHandler(
    repository: ClientRepository,
    scope: CoroutineScope
) : Response.Listener<String> {

    return Response.Listener<String> { response ->
        Timber.i("VOLLEY %s", response!!)

        val keyResponse: DefaultResponse? =
            response.toServerResponse(ServerResponseType.KEY) ?:
            response.toServerResponse(ServerResponseType.DEFAULT)

        if (keyResponse != null && keyResponse.status == "ok" && keyResponse is KeyResponse) {
            /**
             * Update StoredMap row with clientKey
             */
            scope.launch(Dispatchers.IO) {
                repository.updateStoredMap(clientKey = keyResponse.key)

                val key = repository.getClientKey()
                Timber.i("$DBL: key = $key")

                ExperimentalWorkStates.generalizedSetState(WorkType.SETUP, null)
            }
        } else {
            scope.launch(Dispatchers.IO) {
                ExperimentalWorkStates.generalizedSetState(WorkType.SETUP, WorkInfo.State.FAILED)

                Timber.i("$DBL: VOLLEY: ERROR WHEN VERIFY INSTALLATION: ${keyResponse?.error}")
            }
        }
    }
}

private fun verifyPostErrorHandler(scope: CoroutineScope) = Response.ErrorListener { error ->
    scope.launch(Dispatchers.IO) {
        if (error.toString() != "null") {
            Timber.e("$DBL: VOLLEY $error")
            ExperimentalWorkStates.generalizedSetState(WorkType.SETUP, WorkInfo.State.FAILED)
        }
    }
}


