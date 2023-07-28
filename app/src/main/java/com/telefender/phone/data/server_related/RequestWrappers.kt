package com.telefender.phone.data.server_related

import android.content.Context
import androidx.work.WorkInfo
import com.telefender.phone.data.server_related.RemoteDebug.incrementExchangeErrorCounter
import com.telefender.phone.data.server_related.RemoteDebug.resetExchangeCounters
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.ExperimentalWorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.misc_helpers.DBL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import timber.log.Timber

object RequestWrappers {

    private const val retryAmount = 3
    const val retryDelayTime = 5000L // In milliseconds

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
            ExperimentalWorkStates.generalizedSetState(WorkType.DOWNLOAD_POST, WorkInfo.State.RUNNING)
            DataRequests.downloadDataRequest(context, repository, scope)

            val success = ExperimentalWorkStates.generalizedWorkWaiter(
                workType = WorkType.DOWNLOAD_POST,
                runningMsg = "DOWNLOAD",
                stopOnFail = true,
                certainFinish = true
            )
            /*
            Success can be null in the odd case that the request somehow finishes by the time the
            waiter is called.
             */
            if (success == true || success == null) break
            Timber.i("$DBL: $workerName - DOWNLOAD RETRYING")
            delay(retryDelayTime)
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

        ExperimentalWorkStates.generalizedSetState(WorkType.UPLOAD_CHANGE_POST, WorkInfo.State.RUNNING)
        if (repository.hasChangeQTU()) {
            DataRequests.uploadChangeRequest(context, repository, scope, errorCount = 0)
        } else {
            ExperimentalWorkStates.generalizedSetState(WorkType.UPLOAD_CHANGE_POST, null)
            return
        }

        /*
        Acts as waiter for uploading ChangeLogs.

        NOTE: failures have more to do with the request being faulty / the server having
        connection issues or execution problems.
         */
        val success = ExperimentalWorkStates.generalizedWorkWaiter(
            workType = WorkType.UPLOAD_CHANGE_POST,
            runningMsg = "UPLOAD_CHANGE",
            stopOnFail = true,
            certainFinish = true
        )
        if (success == false) {
            Timber.e("$DBL: INSIDE $workerName WORKER. PROBLEM WITH UPLOAD_CHANGE.")
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

        // Only upload AnalyzedNumbers if the ParametersWrapper specify so.
        val parameters = repository.getParameters()
        if (parameters?.shouldUploadAnalyzed != true) {
            return
        }

        ExperimentalWorkStates.generalizedSetState(WorkType.UPLOAD_ANALYZED_POST, WorkInfo.State.RUNNING)
        if (repository.hasAnalyzedQTU()) {
            DataRequests.uploadAnalyzedRequest(context, repository, scope, errorCount = 0)
        } else {
            ExperimentalWorkStates.generalizedSetState(WorkType.UPLOAD_ANALYZED_POST, null)
            return
        }

        /*
        Acts as waiter for uploading AnalyzedNumbers.

        NOTE: failures have more to do with the request being faulty / the server having
        connection issues or execution problems.
         */
        val success = ExperimentalWorkStates.generalizedWorkWaiter(
            workType = WorkType.UPLOAD_ANALYZED_POST,
            runningMsg = "UPLOAD_ANALYZED",
            stopOnFail = true,
            certainFinish = true
        )
        if (success == false) {
            Timber.e("$DBL: INSIDE $workerName WORKER. PROBLEM WITH UPLOAD_ANALYZED.")
        }
    }

    /**
     * TODO: Double check retry strategy.
     *
     * Uploads changes to server. If a big error occurs, like a 404, the upload doesn't continue.
     * Returns whether the worker should continue, retry, or fail (not anymore).
     */
    suspend fun uploadError(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        workerName: String
    ) {

        ExperimentalWorkStates.generalizedSetState(WorkType.UPLOAD_ERROR_POST, WorkInfo.State.RUNNING)
        if (repository.hasErrorLog()) {
            DataRequests.uploadErrorRequest(context, repository, scope, errorCount = 0)
        } else {
            ExperimentalWorkStates.generalizedSetState(WorkType.UPLOAD_ERROR_POST, null)
            return
        }

        /*
        Acts as waiter for uploading ErrorLogs.

        NOTE: failures have more to do with the request being faulty / the server having
        connection issues or execution problems.
         */
        val success = ExperimentalWorkStates.generalizedWorkWaiter(
            workType = WorkType.UPLOAD_ERROR_POST,
            runningMsg = "UPLOAD_ERROR",
            stopOnFail = true,
            certainFinish = true
        )
        if (success == false) {
            Timber.e("$DBL: INSIDE $workerName WORKER. PROBLEM WITH UPLOAD_ERROR.")
        }
    }

    /**
     * TODO: Maybe we shouldn't retry?
     *
     * Asks the server to SMS verify a number.
     *
     * NOTE: There is no need to retry here, as SMS requests are short and should be a one time
     * check. Additionally, we currently don't setup a waiter here, as there is no need yet.
     */
    suspend fun smsVerify(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        number: String
    ) {
        ExperimentalWorkStates.generalizedSetState(WorkType.SMS_VERIFY_POST, WorkInfo.State.RUNNING)
        DataRequests.smsVerifyRequest(context, repository, scope, number)
    }

    /**
     * Downloads ServerData from server. Retries the request here if something goes wrong.
     */
    suspend fun debugCheck(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        workerName: String
    ) {
        for (i in 1..retryAmount) {
            ExperimentalWorkStates.generalizedSetState(WorkType.DEBUG_CHECK_POST, WorkInfo.State.RUNNING)
            RemoteDebug.debugCheckRequest(context, repository, scope)

            val success = ExperimentalWorkStates.generalizedWorkWaiter(
                workType = WorkType.DEBUG_CHECK_POST,
                runningMsg = "DEBUG_CHECK",
                stopOnFail = true,
                certainFinish = true
            )
            /*
            Success can be null in the odd case that the request somehow finishes by the time the
            waiter is called.
             */
            if (success == true || success == null) break
            Timber.i("$DBL: $workerName - DEBUG_CHECK RETRYING")
            delay(retryDelayTime)
        }
    }

    /**
     * TODO: Double check this. Can't tell if I saw something fishy at one point.
     *
     * Downloads ServerData from server. Retries the request here if something goes wrong.
     */
    suspend fun debugSession(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        workerName: String
    ) {
        for (i in 1..retryAmount) {
            ExperimentalWorkStates.generalizedSetState(WorkType.DEBUG_SESSION_POST, WorkInfo.State.RUNNING)
            RemoteDebug.debugSessionRequest(context, repository, scope)

            val success = ExperimentalWorkStates.generalizedWorkWaiter(
                workType = WorkType.DEBUG_SESSION_POST,
                runningMsg = "DEBUG_SESSION",
                stopOnFail = true,
                certainFinish = true
            )
            /*
            Success can be null in the odd case that the request somehow finishes by the time the
            waiter is called.
             */
            if (success == true || success == null) break
            Timber.i("$DBL: $workerName - DEBUG_SESSION RETRYING")
            delay(retryDelayTime)
        }
    }

    /**
     * Downloads ServerData from server. Retries the request here if something goes wrong.
     */
    suspend fun debugExchange(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        workerName: String
    ) {
        resetExchangeCounters()

        while (RemoteDebug.exchangeErrorCounter < retryAmount
            && RemoteDebug.invTokenCounter < retryAmount
        ) {
            ExperimentalWorkStates.generalizedSetState(WorkType.DEBUG_EXCHANGE_POST, WorkInfo.State.RUNNING)
            RemoteDebug.debugExchangeRequest(context, repository, scope, workerName)

            val success = ExperimentalWorkStates.generalizedWorkWaiter(
                workType = WorkType.DEBUG_EXCHANGE_POST,
                runningMsg = null,
                stopOnFail = true,
                certainFinish = true
            )
            /*
            Success can be null in the odd case that the request somehow finishes by the time the
            waiter is called.
             */
            if (success == true || success == null) break

            if (RemoteDebug.invTokenCounter < retryAmount) {
                Timber.i("$DBL: $workerName - DEBUG_EXCHANGE RETRYING")
                delay(retryDelayTime)
                incrementExchangeErrorCounter()
            }
        }
    }

    /**
     * TODO: We currently don't retry here, as it would become too messy for the server (as this is
     *  invoked everytime there is a change in call state).
     *
     * Uploads call / UI state data to server.
     */
    suspend fun debugCallState(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope
    ) {
        ExperimentalWorkStates.generalizedSetState(WorkType.DEBUG_CALL_STATE_POST, WorkInfo.State.RUNNING)
        RemoteDebug.debugCallStateRequest(context, repository, scope)
    }
}