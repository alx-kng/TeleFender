package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.ChangeResponse
import com.telefender.phone.data.server_related.DefaultResponse
import com.telefender.phone.data.server_related.ResponseHelpers
import com.telefender.phone.data.server_related.ServerInteractions.downloadPostRequest
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkerStates
import com.telefender.phone.data.tele_database.background_tasks.WorkerType
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber


class DownloadRequestGen(
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
        ) : DownloadRequestGen {

            return DownloadRequestGen(
                method,
                url,
                downloadResponseHandler(context, repository, scope),
                downloadErrorHandler,
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
private fun downloadResponseHandler(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope
) : Response.Listener<String> {

    return Response.Listener<String> { response ->
        Timber.i("VOLLEY %s", response!!)

        val changeResponse: DefaultResponse? =
            ResponseHelpers.jsonToChangeResponse(response) ?:
            ResponseHelpers.jsonToDefaultResponse(response)

        /**
         * Guarantees that response has the right status before trying to iterate through change
         * logs stored in it.
         */
        if (changeResponse != null && changeResponse.status == "ok" && changeResponse is ChangeResponse) {

            /**
             * TODO: We not initializing next download request in parallel in order to decrease
             *  load on server, but if this really requires speed later, we can change it.
             *
             * Remember that the lambda might not be called inside a suspend environment, so you
             * should launch another coroutine to do database work or launch another post request.
             */
            scope.launch(Dispatchers.IO) {
                for (changeLog in changeResponse.changeLogs) {

                    // serverChangeID can't be null since it's needed for future download requests
                    if (changeLog.serverChangeID != null) {

                        // Inserts each change log into right tables
                        repository.changeFromServer(changeLog)
                    } else {
                        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN DOWNLOAD: serverChangeID is null")
                    }
                }

                /**
                 * Keep launching download requests to server until no changeLogs left.
                 */
                if (changeResponse.changeLogs.isNotEmpty()) {
                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: MORE TO DOWNLOAD")

                    downloadPostRequest(context, repository, scope)
                } else {
                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: All DOWNLOADS COMPLETE")

                    WorkerStates.setState(WorkerType.DOWNLOAD_POST, WorkInfo.State.SUCCEEDED)
                }
            }
        } else {
            WorkerStates.setState(WorkerType.DOWNLOAD_POST, WorkInfo.State.FAILED)

            if (changeResponse != null) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN DOWNLOAD: ${changeResponse.error}")
            } else {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN DOWNLOAD: CHANGE RESPONSE IS NULL")
            }
        }
    }
}

private val downloadErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("VOLLEY %s", error.toString())
        WorkerStates.setState(WorkerType.DOWNLOAD_POST, WorkInfo.State.FAILED)
    }
}
