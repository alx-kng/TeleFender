package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.DataRequests.uploadChangeRequest
import com.telefender.phone.data.server_related.RequestWrappers
import com.telefender.phone.data.server_related.json_classes.DefaultResponse
import com.telefender.phone.data.server_related.json_classes.ServerResponseType
import com.telefender.phone.data.server_related.json_classes.UploadResponse
import com.telefender.phone.data.server_related.json_classes.toServerResponse
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.*
import timber.log.Timber


class UploadChangeRequestGen(
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
        ) : UploadChangeRequestGen {

            return UploadChangeRequestGen(
                method = method,
                url = url,
                listener = uploadChangeResponseHandler(context, repository, scope, errorCount),
                errorListener = uploadChangeErrorHandler,
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
private fun uploadChangeResponseHandler(
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
             * the corresponding upload logs from the UploadChangeQueue table
             */
            scope.launch(Dispatchers.IO) {
                var nextErrorCount = errorCount

                when (uploadResponse.status) {
                    "ok" -> {
                        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: UPLOAD_CHANGE - $uploadResponse")
                        repository.deleteChangeQTUInclusive(uploadResponse.lastUploadedRowID)
                    }
                    else -> {
                        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: PARTIALLY UPLOADED CHANGES WITH ERROR: ${uploadResponse.error}")
                        repository.deleteChangeQTUExclusive(uploadResponse.lastUploadedRowID)
                        nextErrorCount++
                        delay(RequestWrappers.retryDelayTime)
                    }

                }

                /**
                 * Keep launching upload requests to server until no uploadLogs left.
                 */
                if (repository.hasChangeQTU()) {
                    Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: MORE CHANGES TO UPLOAD")

                    uploadChangeRequest(context, repository, scope, nextErrorCount)
                } else {
                    Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: All CHANGE UPLOADS COMPLETE")

                    WorkStates.setState(WorkType.UPLOAD_CHANGE_POST, WorkInfo.State.SUCCEEDED)
                }
            }
        } else {
            WorkStates.setState(WorkType.UPLOAD_CHANGE_POST, WorkInfo.State.FAILED)

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN UPLOAD_CHANGE: ${uploadResponse?.error}")
        }
    }
}

private val uploadChangeErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY $error")
        WorkStates.setState(WorkType.UPLOAD_CHANGE_POST, WorkInfo.State.FAILED)
    }
}
