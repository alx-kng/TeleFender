package com.telefender.phone.data.tele_database.background_tasks.workers

import android.content.Context
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.server_related.RequestWrappers
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

object UploadScheduler {
    val uploadOneTag = "oneTimeUploadWorker"
    val uploadPeriodTag = "periodicUploadWorker"
    
    fun initiateOneTimeUploadWorker(context : Context) : UUID {
        WorkStates.setState(
            workType = WorkType.ONE_TIME_UPLOAD,
            workState = WorkInfo.State.RUNNING,
            context = context,
            tag = uploadOneTag
        )

        val uploadRequest = OneTimeWorkRequestBuilder<CoroutineUploadWorker>()
            .setInputData(workDataOf("variableName" to "oneTimeUploadState", "notificationID" to "3333"))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(uploadOneTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(uploadOneTag, ExistingWorkPolicy.KEEP, uploadRequest)

        return uploadRequest.id
    }

    fun initiatePeriodicUploadWorker(context : Context) : UUID {
        WorkStates.setState(
            workType = WorkType.PERIODIC_UPLOAD,
            workState = WorkInfo.State.RUNNING,
            context = context,
            tag = uploadPeriodTag
        )

        val uploadRequest = PeriodicWorkRequestBuilder<CoroutineUploadWorker>(1, TimeUnit.HOURS)
            .setInputData(workDataOf("variableName" to "periodicUploadState", "notificationID" to "4444"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(uploadPeriodTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(uploadPeriodTag, ExistingPeriodicWorkPolicy.KEEP, uploadRequest)

        return uploadRequest.id
    }
}

/**
 * TODO: Maybe we should just make UploadWorker for uploading AnalyzedNumbers and CallDetails
 *  (for data analysis or something) so that it's separate from all the other stuff in OmegaWorker.
 */
class CoroutineUploadWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    var NOTIFICATION_ID : Int? = -1
    val CHANNEL_ID = "alxkng5737"
    var stateVarString: String? = null

    val scope = CoroutineScope(Dispatchers.IO)

    
    override suspend fun doWork() : Result {
        stateVarString = inputData.getString("variableName")
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        try {
            setForeground(getForegroundInfo())
        } catch(e: Exception) {
            Timber.i("$DBL: %s", e.message!!)
        }

        when (stateVarString) {
            "oneTimeUploadState" ->  {
                Timber.i("$DBL: UPLOAD ONE TIME STARTED")
                // No need to set the state again for one time workers.
            }
            "periodicUploadState" -> {
                Timber.i("$DBL: UPLOAD PERIODIC STARTED")

                /**
                 * Although this may seem redundant, we need to set the state to running here,
                 * because if we call initiatePeriodicWorker() and the worker is in its
                 * enqueued state (interval down time), then the setState() called there will not
                 * set the state to RUNNING due to its safety measures. However, when the periodic
                 * worker DOES start, we still need to make sure the state accurately reflects the
                 * actually RUNNING state of the worker.
                 */
                WorkStates.setState(WorkType.PERIODIC_UPLOAD, WorkInfo.State.RUNNING)
            }
            else -> {
                Timber.i("$DBL: UPLOAD_CHANGE WORKER THREAD: Worker state variable name is wrong")
            }
        }

        val repository: ClientRepository = (applicationContext as App).repository

        /**
         * Uploads changes to server.
         */
        Timber.i("$DBL: OMEGA UPLOAD_CHANGE STARTED")
        RequestWrappers.uploadChange(context, repository, scope, "UPLOAD WORKER")

        /**
         * TODO: Confirm that we want to upload AnalyzedNumbers here. Or maybe, have some type
         *  or condition that allows us to choose whether or not we want to upload
         *  AnalyzedNumbers or not.
         *
         * Uploads analyzedNumbers to server.
         */
        Timber.i("$DBL: OMEGA UPLOAD_ANALYZED_POST STARTED")
        RequestWrappers.uploadAnalyzed(context, repository, scope, "UPLOAD WORKER")

        /**
         * Uploads error logs to server.
         */
        Timber.i("$DBL: OMEGA UPLOAD_ERROR STARTED")
        RequestWrappers.uploadError(context, repository, scope, "OMEGA")

        when (stateVarString) {
            "oneTimeUploadState" ->  WorkStates.setState(WorkType.ONE_TIME_UPLOAD, WorkInfo.State.SUCCEEDED)
            "periodicUploadState" -> WorkStates.setState(WorkType.PERIODIC_UPLOAD, WorkInfo.State.SUCCEEDED)
            else -> {
                Timber.i("$DBL: UPLOAD_CHANGE WORKER THREAD: Worker state variable name is wrong")
            }
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("$DBL: UPLOAD_CHANGE WORKER FOREGROUND")

        return ForegroundInfoCreator.createForegroundInfo(
            applicationContext = applicationContext,
            notificationID = NOTIFICATION_ID!!,
            channelID = CHANNEL_ID,
            contextText = "Uploading..."
        )
    }
}
