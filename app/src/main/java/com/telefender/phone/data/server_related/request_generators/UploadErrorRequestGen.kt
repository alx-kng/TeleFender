package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.*
import com.telefender.phone.data.server_related.json_classes.DefaultResponse
import com.telefender.phone.data.server_related.json_classes.ServerResponseType
import com.telefender.phone.data.server_related.json_classes.UploadResponse
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


class UploadErrorRequestGen(
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
            errorCount: Int,
        ) : UploadErrorRequestGen {

            return UploadErrorRequestGen(
                method = method,
                url = url,
                listener = uploadErrorResponseHandler(context, repository, scope, errorCount),
                errorListener = uploadErrorErrorHandler,
                requestJson = requestJson,
            )
        }
    }
}

/**
 * TODO: Is it correct to use deleteQTUExclusive for partial upload error?
 *
 * Basically checks response from server for lastUploadRow and deletes the successfully uploaded
 * rows from the QTU. If there are more QTUs, then another upload request is launched.
 */
private fun uploadErrorResponseHandler(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope,
    errorCount: Int
) : Response.Listener<String> {

    return Response.Listener<String> { response ->
        Timber.i("VOLLEY %s", response!!)

        val uploadResponse: DefaultResponse? =
            response.toServerResponse(ServerResponseType.UPLOAD) ?:
            response.toServerResponse(ServerResponseType.DEFAULT)

        /**
         * Guarantees that response has the right status before trying to continue requests.
         */
        if (uploadResponse != null && uploadResponse is UploadResponse) {

            /**
             * If all upload logs are uploaded to the server successfully, we will delete
             * the corresponding upload logs from the UploadErrorQueue table
             */
            scope.launch(Dispatchers.IO) {
                var nextErrorCount = errorCount

                when (uploadResponse.status) {
                    "ok" -> {
                        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: UPLOAD_ERROR - $uploadResponse")
                        repository.deleteErrorLogInclusive(uploadResponse.lastUploadedRowID)
                    }
                    else -> {
                        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: PARTIALLY UPLOADED ERROR LOGS WITH ERROR: ${uploadResponse.error}")
                        repository.deleteErrorLogExclusive(uploadResponse.lastUploadedRowID)
                        nextErrorCount++
                        delay(2000)
                    }

                }

                /**
                 * Keep launching upload requests to server until no uploadLogs left.
                 */
                if (repository.hasErrorLog()) {
                    Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: MORE ERROR LOGS TO UPLOAD")

                    DataRequests.uploadErrorRequest(
                        context,
                        repository,
                        scope,
                        nextErrorCount
                    )
                } else {
                    Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: ALL ERROR LOG UPLOADS COMPLETE")

                    WorkStates.setState(WorkType.UPLOAD_ERROR_POST, WorkInfo.State.SUCCEEDED)
                }
            }
        } else {
            WorkStates.setState(WorkType.UPLOAD_ERROR_POST, WorkInfo.State.FAILED)

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN UPLOAD_ERROR: ${uploadResponse?.error}")
        }
    }
}

private val uploadErrorErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY $error")
        WorkStates.setState(WorkType.UPLOAD_ERROR_POST, WorkInfo.State.FAILED)
    }
}
