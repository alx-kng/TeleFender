package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.*
import com.telefender.phone.data.server_related.ServerInteractions.uploadAnalyzedRequest
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkerStates
import com.telefender.phone.data.tele_database.background_tasks.WorkerType
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.*
import timber.log.Timber


class UploadAnalyzedRequestGen(
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
        ) : UploadAnalyzedRequestGen {

            return UploadAnalyzedRequestGen(
                method,
                url,
                uploadAnalyzedResponseHandler(context, repository, scope),
                uploadAnalyzedErrorHandler,
                requestJson
            )
        }
    }
}

/**
 * Basically checks response from server for lastUploadRow and deletes the successfully uploaded
 * rows from the QTU. If there are more QTUs, then another upload request is launched.
 */
private fun uploadAnalyzedResponseHandler(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope
) : Response.Listener<String> {

    return Response.Listener<String> { response ->
        Timber.i("VOLLEY %s", response!!)

        val uploadResponse: DefaultResponse? =
            response.toServerResponse(ServerResponseType.UPLOAD) ?:
            response.toServerResponse(ServerResponseType.DEFAULT)

        /**
         * Guarantees that response has the right status before trying to iterate through upload
         * logs. Also upload logs shouldn't be null.
         */
        if (uploadResponse != null && uploadResponse is UploadResponse) {

            /**
             * If all upload logs are uploaded to the server successfully, we will delete
             * the corresponding upload logs from the UploadAnalyzedQueue table
             */
            scope.launch(Dispatchers.IO) {
                when (uploadResponse.status) {
                    "ok" -> {
                        repository.deleteAnalyzedQTUInclusive(uploadResponse.lastUploadedRowID)
                    }
                    else -> {
                        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: PARTIALLY UPLOADED ANALYZED WITH ERROR: ${uploadResponse.error}")

                        repository.deleteAnalyzedQTUExclusive(uploadResponse.lastUploadedRowID)
                    }

                }

                /**
                 * Keep launching upload requests to server until no uploadLogs left.
                 */
                if (repository.hasAnalyzedQTU()) {
                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: MORE ANALYZED TO UPLOAD")

                    uploadAnalyzedRequest(context, repository, scope)
                } else {
                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: All ANALYZED UPLOADS COMPLETE")

                    WorkerStates.setState(WorkerType.UPLOAD_ANALYZED, WorkInfo.State.SUCCEEDED)
                }
            }
        } else {
            WorkerStates.setState(WorkerType.UPLOAD_ANALYZED, WorkInfo.State.FAILED)

            if (uploadResponse != null) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN UPLOAD_ANALYZED: ${uploadResponse.error}")
            } else {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN UPLOAD_ANALYZED: RESPONSE IS NULL")
            }
        }
    }
}

private val uploadAnalyzedErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY $error")
        WorkerStates.setState(WorkerType.UPLOAD_ANALYZED, WorkInfo.State.FAILED)
    }
}
