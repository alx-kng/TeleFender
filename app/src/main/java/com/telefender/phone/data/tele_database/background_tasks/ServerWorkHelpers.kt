package com.telefender.phone.data.tele_database.background_tasks

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import com.telefender.phone.data.server_related.ServerInteractions
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import timber.log.Timber

object ServerWorkHelpers {

    private const val retryAmount = 5

    /**
     * Downloads ServerData from server. Retries the request here if something goes wrong.
     */
    suspend fun downloadData(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        workerName: String
    ) {
        for (i in 1..retryAmount) {
            WorkStates.setState(WorkType.DOWNLOAD_POST, WorkInfo.State.RUNNING)
            ServerInteractions.downloadDataRequest(context, repository, scope)

            val success = WorkStates.workWaiter(WorkType.DOWNLOAD_POST, "DOWNLOAD", stopOnFail = true, certainFinish = true)
            if (success) break
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: $workerName - DOWNLOAD RETRYING")
            delay(2000)
        }
    }

    /**
     * TODO: Double check retry strategy.
     *
     * Uploads changes to server. If a big error occurs, like a 404, the upload doesn't continue.
     * Returns whether the worker should continue, retry, or fail.
     */
    suspend fun uploadChange(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        workerName: String
    ) {

        WorkStates.setState(WorkType.UPLOAD_CHANGE_POST, WorkInfo.State.RUNNING)
        if (repository.hasChangeQTU()) {
            ServerInteractions.uploadChangeRequest(context, repository, scope, errorCount = 0)
        } else {
            WorkStates.setState(WorkType.UPLOAD_CHANGE_POST, WorkInfo.State.SUCCEEDED)
        }

        /*
        Acts as waiter for uploading ChangeLogs.

        NOTE: failures have more to do with the request being faulty / the server having
        connection issues or execution problems.
         */
        if (!WorkStates.workWaiter(WorkType.UPLOAD_CHANGE_POST, "UPLOAD_CHANGE", stopOnFail = true, certainFinish = true)) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: INSIDE $workerName WORKER. PROBLEM WITH UPLOAD_CHANGE.")
        }
    }

    /**
     * TODO: Double check retry strategy.
     *
     * Uploads analyzedNumbers to server. If a big error occurs, like a 404, the upload doesn't
     * continue. Returns whether the worker should continue, retry, or fail.
     */
    suspend fun uploadAnalyzed(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        workerName: String
    ) {

        // Only upload AnalyzedNumbers if the Parameters specify so.
        val parameters = repository.getParameters()
        if (parameters?.shouldUploadAnalyzed != true) {
            return
        }

        WorkStates.setState(WorkType.UPLOAD_ANALYZED_POST, WorkInfo.State.RUNNING)
        if (repository.hasAnalyzedQTU()) {
            ServerInteractions.uploadAnalyzedRequest(context, repository, scope, errorCount = 0)
        } else {
            WorkStates.setState(WorkType.UPLOAD_ANALYZED_POST, WorkInfo.State.SUCCEEDED)
        }

        /*
        Acts as waiter for uploading AnalyzedNumbers.

        NOTE: failures have more to do with the request being faulty / the server having
        connection issues or execution problems.
         */
        if (!WorkStates.workWaiter(WorkType.UPLOAD_ANALYZED_POST, "UPLOAD_ANALYZED", stopOnFail = true, certainFinish = true)) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: INSIDE $workerName WORKER. PROBLEM WITH UPLOAD_ANALYZED.")
        }
    }

    /**
     * TODO: Double check retry strategy.
     *
     * Uploads changes to server. If a big error occurs, like a 404, the upload doesn't continue.
     * Returns whether the worker should continue, retry, or fail.
     */
    suspend fun uploadError(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        workerName: String
    ) {

        WorkStates.setState(WorkType.UPLOAD_ERROR_POST, WorkInfo.State.RUNNING)
        if (repository.hasErrorLog()) {
            ServerInteractions.uploadErrorRequest(context, repository, scope, errorCount = 0)
        } else {
            WorkStates.setState(WorkType.UPLOAD_ERROR_POST, WorkInfo.State.SUCCEEDED)
        }

        /*
        Acts as waiter for uploading ErrorLogs.

        NOTE: failures have more to do with the request being faulty / the server having
        connection issues or execution problems.
         */
        if (!WorkStates.workWaiter(WorkType.UPLOAD_ERROR_POST, "UPLOAD_ERROR", stopOnFail = true, certainFinish = true)) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: INSIDE $workerName WORKER. PROBLEM WITH UPLOAD_ERROR.")
        }
    }
}