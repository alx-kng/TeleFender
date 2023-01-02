package com.telefender.phone.data.server_related.request_generators

import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.DefaultResponse
import com.telefender.phone.data.server_related.KeyResponse
import com.telefender.phone.data.server_related.ResponseHelpers
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkerStates
import com.telefender.phone.data.tele_database.background_tasks.WorkerType
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
    requestJson: String,
) : RequestGen(method, url, listener, errorListener, requestJson) {

    companion object {
        fun create(
            method: Int,
            url: String,
            requestJson: String,
            repository: ClientRepository,
            scope: CoroutineScope
        ) : VerifyRequestGen {

            return VerifyRequestGen(
                method,
                url,
                verifyPostResponseHandler(repository, scope),
                verifyPostErrorHandler,
                requestJson
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
         * TODO: Can we just check for null instead of exception? If not, then
         *  replace with old code from previous commit.
         *
         * Basically, if a normal session response is returned, then we will be
         * able to convert JSON into KeyResponse. Otherwise, the response is
         * probably an error JSON, in which we can convert to DefaultResponse.
         */
        val keyResponse: DefaultResponse? =
            ResponseHelpers.jsonToKeyResponse(response) ?:
            ResponseHelpers.jsonToDefaultResponse(response)

        if (keyResponse != null && keyResponse.status == "ok" && keyResponse is KeyResponse) {
            /**
             * Update StoredMap row with clientKey
             */
            scope.launch(Dispatchers.IO) {
                val instanceNumber = repository.getInstanceNumber()!!
                repository.updateStoredMap(instanceNumber, clientKey = keyResponse.key)

                WorkerStates.setState(WorkerType.SETUP, WorkInfo.State.SUCCEEDED)
            }
        } else {
            WorkerStates.setState(WorkerType.SETUP, WorkInfo.State.FAILED)

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
        Timber.e("VOLLEY %s", error.toString())
        WorkerStates.setState(WorkerType.SETUP, WorkInfo.State.FAILED)
    }
}

