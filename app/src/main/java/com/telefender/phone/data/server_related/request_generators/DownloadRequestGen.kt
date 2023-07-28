package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.DataRequests.downloadDataRequest
import com.telefender.phone.data.server_related.json_classes.DefaultResponse
import com.telefender.phone.data.server_related.json_classes.DownloadResponse
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
                errorListener = downloadErrorHandler(scope),
                requestJson = requestJson
            )
        }
    }
}

/**
 * TODO: Is it possible / do we need to make the Response lambda tail recursive?
 *  -> Apparently not necessary.
 *
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
             * TODO: Should we set Download_Post state to Failed or Success for the
             *  changeFromServer() error. Making it failed, will make us retry the entire
             *  download post request.
             *
             * Remember that the lambda might not be called inside a suspend environment, so you
             * should launch another coroutine to do database work or launch another post request.
             */
            scope.launch(Dispatchers.IO) {
                for (genericData in downloadResponse.data) {

                    // Inserts ServerData into right table and into ExecuteQueue
                    val success = repository.changeFromServer(genericData)
                    if (!success) {
                        Timber.i("$DBL: " +
                            "VOLLEY: changeFromServer() wasn't successful. Stopping further requests.")

                        ExperimentalWorkStates.generalizedSetState(WorkType.DOWNLOAD_POST, WorkInfo.State.FAILED)
                        return@launch
                    }
                }

                /**
                 * Keep launching download requests to server until no changeLogs left.
                 */
                if (downloadResponse.data.isNotEmpty()) {
                    Timber.i("$DBL: VOLLEY: MORE TO DOWNLOAD")

                    downloadDataRequest(context, repository, scope)
                } else {
                    Timber.i("$DBL: VOLLEY: All DOWNLOADS COMPLETE")

                    ExperimentalWorkStates.generalizedSetState(WorkType.DOWNLOAD_POST, null)
                }
            }
        } else {
            scope.launch(Dispatchers.IO) {
                ExperimentalWorkStates.generalizedSetState(WorkType.DOWNLOAD_POST, WorkInfo.State.FAILED)

                Timber.i("$DBL: VOLLEY: ERROR WHEN DOWNLOAD: ${downloadResponse?.error}")
            }
        }
    }
}

private fun downloadErrorHandler(scope: CoroutineScope) = Response.ErrorListener { error ->
    scope.launch(Dispatchers.IO) {
        if (error.toString() != "null") {
            Timber.e("$DBL: VOLLEY $error")
            ExperimentalWorkStates.generalizedSetState(WorkType.DOWNLOAD_POST, WorkInfo.State.FAILED)
        }
    }
}