package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.*
import com.telefender.phone.data.server_related.ServerInteractions.uploadChangeRequest
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkerStates
import com.telefender.phone.data.tele_database.background_tasks.WorkerType
import com.telefender.phone.helpers.MiscHelpers
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
            scope: CoroutineScope
        ) : UploadChangeRequestGen {

            return UploadChangeRequestGen(
                method,
                url,
                uploadChangesResponseHandler(context, repository, scope),
                uploadChangesErrorHandler,
                requestJson
            )
        }
    }
}

/**
 * Basically checks response from server for lastUploadRow and deletes the successfully uploaded
 * rows from the QTU. If there are more QTUs, then another upload request is launched.
 */
private fun uploadChangesResponseHandler(
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
             * the corresponding upload logs from the UploadChangeQueue table
             */
            scope.launch(Dispatchers.IO) {
                when (uploadResponse.status) {
                    "ok" -> {
                        repository.deleteChangeQTUInclusive(uploadResponse.lastUploadedRowID)
                    }
                    else -> {
                        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: PARTIALLY UPLOADED CHANGES WITH ERROR: ${uploadResponse.error}")

                        repository.deleteChangeQTUExclusive(uploadResponse.lastUploadedRowID)
                    }

                }

                /**
                 * Keep launching upload requests to server until no uploadLogs left.
                 */
                if (repository.hasChangeQTU()) {
                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: MORE TO CHANGES TO UPLOAD")

                    uploadChangeRequest(context, repository, scope)
                } else {
                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: All CHANGE UPLOADS COMPLETE")

                    WorkerStates.setState(WorkerType.UPLOAD_CHANGES, WorkInfo.State.SUCCEEDED)
                }
            }
        } else {
            WorkerStates.setState(WorkerType.UPLOAD_CHANGES, WorkInfo.State.FAILED)

            if (uploadResponse != null) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN UPLOAD_CHANGE: ${uploadResponse.error}")
            } else {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN UPLOAD_CHANGE: RESPONSE IS NULL")
            }
        }
    }
}

private val uploadChangesErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY $error")
        WorkerStates.setState(WorkerType.UPLOAD_CHANGES, WorkInfo.State.FAILED)
    }
}
