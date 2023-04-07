package com.telefender.phone.data.tele_database.background_tasks.workers

import android.content.Context
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.server_related.RequestWrappers
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
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

        if (stateVarString == "oneTimeUploadState") {
            try {
                setForeground(getForegroundInfo())
            } catch(e: Exception) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: %s", e.message!!)
            }
        }

        val repository: ClientRepository = (applicationContext as App).repository

        /**
         * Uploads changes to server. Returns next Result action if uploadChange()
         * doesn't return null (failure or retry).
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA UPLOAD_CHANGE STARTED")
        RequestWrappers.uploadChange(context, repository, scope, "UPLOAD WORKER")

        /**
         * TODO: Confirm that we want to upload AnalyzedNumbers here. Or maybe, have some type
         *  or condition that allows us to choose whether or not we want to upload
         *  AnalyzedNumbers or not.
         *
         * Uploads analyzedNumbers to server. Returns next Result action if uploadAnalyzed()
         * doesn't return null (failure or retry).
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA UPLOAD_ANALYZED_POST STARTED")
        RequestWrappers.uploadAnalyzed(context, repository, scope, "UPLOAD WORKER")

        /**
         * Uploads error logs to server.
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA UPLOAD_ERROR STARTED")
        RequestWrappers.uploadError(context, repository, scope, "OMEGA")

        when (stateVarString) {
            "oneTimeUploadState" ->  WorkStates.setState(WorkType.ONE_TIME_UPLOAD, WorkInfo.State.SUCCEEDED)
            "periodicUploadState" -> WorkStates.setState(WorkType.PERIODIC_UPLOAD, WorkInfo.State.RUNNING)
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: UPLOAD_CHANGE WORKER THREAD: Worker state variable name is wrong")
            }
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: UPLOAD_CHANGE WORKER FOREGROUND")

        return ForegroundInfoCreator.createForegroundInfo(
            applicationContext = applicationContext,
            notificationID = NOTIFICATION_ID!!,
            channelID = CHANNEL_ID,
            contextText = "Uploading..."
        )
    }
}
