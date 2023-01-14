package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.*
import com.telefender.phone.data.server_related.ServerInteractions.downloadDataRequest
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
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
                method = method,
                url = url,
                listener = downloadResponseHandler(context, repository, scope),
                errorListener = downloadErrorHandler,
                requestJson = requestJson
            )
        }
    }
}

/**
 * Retrieves DownloadResponse object containing (status, error, List<ServerData>)
 * and inserts each ServerData value into our database using changeFromServer() in ChangeAgentDao.
 */
private fun downloadResponseHandler(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope
) : Response.Listener<String> {

    return Response.Listener<String> { response ->
        Timber.i("VOLLEY %s", response!!)

        val downloadResponse: DefaultResponse? =
            response.toServerResponse(ServerResponseType.DOWNLOAD) ?:
            response.toServerResponse(ServerResponseType.DEFAULT)

        /**
         * Guarantees that response has the right status before trying to iterate through the
         * generic data stored in it.
         */
        if (downloadResponse != null && downloadResponse.status == "ok" && downloadResponse is DownloadResponse) {

            /**
             * TODO: We not initializing next download request in parallel in order to decrease
             *  load on server, but if this really requires speed later, we can change it.
             *
             * TODO: Don't immediately do another Download request if success of changeFromServer()
             *  is false -> Pretty much implemented, but double check!
             *
             * Remember that the lambda might not be called inside a suspend environment, so you
             * should launch another coroutine to do database work or launch another post request.
             */
            scope.launch(Dispatchers.IO) {
                for (genericData in downloadResponse.data) {

                    // Inserts ServerData into right table and into ExecuteQueue
                    val success = repository.changeFromServer(genericData)
                    if (!success) {
                        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: " +
                            "VOLLEY: changeFromServer() wasn't successful. Stopping further requests.")

                        WorkStates.setState(WorkType.DOWNLOAD_POST, WorkInfo.State.SUCCEEDED)
                        return@launch
                    }
                }

                /**
                 * Keep launching download requests to server until no changeLogs left.
                 */
                if (downloadResponse.data.isNotEmpty()) {
                    Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: MORE TO DOWNLOAD")

                    downloadDataRequest(context, repository, scope)
                } else {
                    Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: All DOWNLOADS COMPLETE")

                    WorkStates.setState(WorkType.DOWNLOAD_POST, WorkInfo.State.SUCCEEDED)
                }
            }
        } else {
            WorkStates.setState(WorkType.DOWNLOAD_POST, WorkInfo.State.FAILED)

            if (downloadResponse != null) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN DOWNLOAD: ${downloadResponse.error}")
            } else {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN DOWNLOAD: DOWNLOAD RESPONSE IS NULL")
            }
        }
    }
}

private val downloadErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY $error")
        WorkStates.setState(WorkType.DOWNLOAD_POST, WorkInfo.State.FAILED)
    }
}
