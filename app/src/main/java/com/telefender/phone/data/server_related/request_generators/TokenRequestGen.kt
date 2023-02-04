package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.DefaultResponse
import com.telefender.phone.data.server_related.ServerResponseType
import com.telefender.phone.data.server_related.toServerResponse
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
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
                method = method,
                url = url,
                listener = tokenResponseHandler(context, repository, scope),
                errorListener = tokenErrorHandler,
                requestJson = requestJson
            )
        }
    }
}

private fun tokenResponseHandler(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope
) : Response.Listener<String> {

    return Response.Listener<String> { response ->
        Timber.i("TOKEN RESPONSE: %s", response!!)

        val defaultResponse: DefaultResponse? = response.toServerResponse(ServerResponseType.DEFAULT)

        if (defaultResponse != null && defaultResponse.status == "ok") {
            WorkStates.setState(WorkType.UPLOAD_TOKEN, WorkInfo.State.SUCCEEDED)
        } else {
            WorkStates.setState(WorkType.UPLOAD_TOKEN, WorkInfo.State.FAILED)

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN TOKEN UPLOAD_TOKEN: ${defaultResponse?.error}")
        }
    }
}

private val tokenErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY $error")
        WorkStates.setState(WorkType.UPLOAD_TOKEN, WorkInfo.State.FAILED)
    }
}
