package com.telefender.phone.data.server_related.request_generators

import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.*
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.MiscHelpers
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
                errorListener = verifyPostErrorHandler,
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

        /**
         * Basically, if a normal session response is returned, then we will be
         * able to convert JSON into KeyResponse. Otherwise, the response is
         * probably an error JSON, in which we can convert to DefaultResponse.
         */
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
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: key = $key")

                WorkStates.setState(WorkType.SETUP, WorkInfo.State.SUCCEEDED)
            }
        } else {
            WorkStates.setState(WorkType.SETUP, WorkInfo.State.FAILED)

            if (keyResponse != null) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN VERIFY INSTALLATION: $keyResponse.error",)
            } else {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN VERIFY INSTALLATION: SESSION RESPONSE IS NULL")
            }
        }
    }
}

private val verifyPostErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY $error")
        WorkStates.setState(WorkType.SETUP, WorkInfo.State.FAILED)
    }
}

