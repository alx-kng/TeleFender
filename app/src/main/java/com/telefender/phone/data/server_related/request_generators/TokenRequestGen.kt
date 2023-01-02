package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.DefaultResponse
import com.telefender.phone.data.server_related.ResponseHelpers.jsonToDefaultResponse
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkerStates
import com.telefender.phone.data.tele_database.background_tasks.WorkerType
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber


class TokenRequestGen(
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
        ) : TokenRequestGen {

            return TokenRequestGen(
                method,
                url,
                tokenResponseHandler(context, repository, scope),
                tokenErrorHandler,
                requestJson
            )
        }
    }
}

/**
 * Retrieves ChangeResponse object containing (status, error, List<ChangeLogs>)
 * and inserts each change log into our database using changeFromServer()
 * defined in ChangeAgentDao.
 */
private fun tokenResponseHandler(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope
) : Response.Listener<String> {

    return Response.Listener<String> { response ->
        Timber.i("TOKEN RESPONSE: %s", response!!)

        val defaultResponse: DefaultResponse? = jsonToDefaultResponse(response)

        if (defaultResponse != null && defaultResponse.status == "ok") {
            WorkerStates.setState(WorkerType.UPLOAD_TOKEN, WorkInfo.State.SUCCEEDED)
        } else {
            WorkerStates.setState(WorkerType.UPLOAD_TOKEN, WorkInfo.State.FAILED)

            if (defaultResponse != null) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN TOKEN UPLOAD: ${defaultResponse.error}")
            } else {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN TOKEN UPLOAD: DEFAULT RESPONSE IS NULL")
            }
        }
    }
}

private val tokenErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("VOLLEY %s", error.toString())
        WorkerStates.setState(WorkerType.UPLOAD_TOKEN, WorkInfo.State.FAILED)
    }
}
