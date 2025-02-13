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
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                errorListener = tokenErrorHandler(scope),
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
            scope.launch(Dispatchers.IO) {
                ExperimentalWorkStates.generalizedSetState(WorkType.UPLOAD_TOKEN, null)
            }
        } else {
            scope.launch(Dispatchers.IO) {
                ExperimentalWorkStates.generalizedSetState(WorkType.UPLOAD_TOKEN, WorkInfo.State.FAILED)

                Timber.i("$DBL: VOLLEY: ERROR WHEN TOKEN UPLOAD_TOKEN: ${defaultResponse?.error}")
            }
        }
    }
}

private fun tokenErrorHandler(scope: CoroutineScope) = Response.ErrorListener { error ->
    scope.launch(Dispatchers.IO) {
        if (error.toString() != "null") {
            Timber.e("$DBL: VOLLEY $error")
            ExperimentalWorkStates.generalizedSetState(WorkType.UPLOAD_TOKEN, WorkInfo.State.FAILED)
        }
    }
}
