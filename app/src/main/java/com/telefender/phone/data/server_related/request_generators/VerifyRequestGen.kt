package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import android.icu.lang.UCharacter.GraphemeClusterBreak.V
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
import com.telefender.phone.misc_helpers.SharedPreferenceHelpers
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
            context: Context,
            scope: CoroutineScope
        ) : VerifyRequestGen {

            return VerifyRequestGen(
                method = method,
                url = url,
                listener = verifyPostResponseHandler(context, scope),
                errorListener = verifyPostErrorHandler(scope),
                requestJson = requestJson
            )
        }
    }
}

private fun verifyPostResponseHandler(
    context: Context,
    scope: CoroutineScope
) : Response.Listener<String> {

    return Response.Listener<String> { response ->
        Timber.i("VOLLEY %s", response!!)

        val keyResponse: DefaultResponse? =
            response.toServerResponse(ServerResponseType.KEY) ?:
            response.toServerResponse(ServerResponseType.DEFAULT)

        if (keyResponse != null && keyResponse.status == "ok" && keyResponse is KeyResponse) {
            /**
             * Update SharedPreferences with clientKey
             */
            scope.launch(Dispatchers.IO) {
                SharedPreferenceHelpers.setClientKey(context, keyResponse.key)

                val key = SharedPreferenceHelpers.getClientKey(context)
                Timber.i("$DBL: key = $key")

                ExperimentalWorkStates.generalizedSetState(WorkType.VERIFY_POST, null)
            }
        } else {
            scope.launch(Dispatchers.IO) {
                ExperimentalWorkStates.generalizedSetState(WorkType.VERIFY_POST, WorkInfo.State.FAILED)

                Timber.i("$DBL: VOLLEY: ERROR WHEN VERIFY INSTALLATION: ${keyResponse?.error}")
            }
        }
    }
}

private fun verifyPostErrorHandler(scope: CoroutineScope) = Response.ErrorListener { error ->
    scope.launch(Dispatchers.IO) {
        if (error.toString() != "null") {
            Timber.e("$DBL: VOLLEY $error")
            ExperimentalWorkStates.generalizedSetState(WorkType.VERIFY_POST, WorkInfo.State.FAILED)
        }
    }
}


