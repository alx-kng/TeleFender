package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.*
import com.telefender.phone.data.server_related.ServerInteractions.uploadPostRequest
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkerStates
import com.telefender.phone.data.tele_database.background_tasks.WorkerType
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.*
import timber.log.Timber


class UploadRequestGen(
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
        ) : UploadRequestGen {

            return UploadRequestGen(
                method,
                url,
                uploadResponseHandler(context, repository, scope),
                uploadErrorHandler,
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
private fun uploadResponseHandler(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope
) : Response.Listener<String> {

    return Response.Listener<String> { response ->
        Timber.i("VOLLEY %s", response!!)

        val uploadResponse: DefaultResponse? =
            ResponseHelpers.jsonToUploadResponse(response) ?:
            ResponseHelpers.jsonToDefaultResponse(response)

        /**
         * Guarantees that response has the right status before trying to iterate through upload
         * logs. Also upload logs shouldn't be null.
         */
        if (uploadResponse != null && uploadResponse is UploadResponse) {

            /**
             * If all upload logs are uploaded to the server successfully, we will delete
             * the corresponding upload logs from the UploadQueue table
             */
            scope.launch(Dispatchers.IO) {
                when (uploadResponse.status) {
                    "ok" -> {
                        repository.deleteUploadInclusive(uploadResponse.lastUploadRow)
                    }
                    else -> {
                        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: PARTIALLY UPLOADED WITH ERROR: ${uploadResponse.error}")

                        repository.deleteUploadExclusive(uploadResponse.lastUploadRow)
                    }

                }

                /**
                 * Keep launching upload requests to server until no uploadLogs left.
                 */
                if (repository.hasQTUs()) {
                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: MORE TO UPLOAD")

                    uploadPostRequest(context, repository, scope)
                } else {
                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: All UPLOADS COMPLETE")

                    WorkerStates.setState(WorkerType.UPLOAD_POST, WorkInfo.State.SUCCEEDED)
                }
            }
        } else {
            WorkerStates.setState(WorkerType.UPLOAD_POST, WorkInfo.State.FAILED)

            if (uploadResponse != null) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN UPLOAD: ${uploadResponse.error}")
            } else {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN UPLOAD: UPLOAD RESPONSE IS NULL")
            }
        }
    }
}

private val uploadErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY $error")
        WorkerStates.setState(WorkerType.UPLOAD_POST, WorkInfo.State.FAILED)
    }
}
