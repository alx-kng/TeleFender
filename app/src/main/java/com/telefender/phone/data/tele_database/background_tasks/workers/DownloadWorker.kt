package com.telefender.phone.data.tele_database.background_tasks.workers

import android.content.Context
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.data.server_related.RequestWrappers
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

object DownloadScheduler {

    val downloadOneTag = "oneTimeDownloadWorker"
    val downloadPeriodTag = "periodicDownloadWorker"
    
    fun initiateOneTimeDownloadWorker(context : Context) : UUID {
        WorkStates.setState(
            workType = WorkType.ONE_TIME_DOWNLOAD,
            workState = WorkInfo.State.RUNNING,
            context = context,
            tag = downloadOneTag
        )

        val downloadRequest = OneTimeWorkRequestBuilder<CoroutineDownloadWorker>()
            .setInputData(workDataOf("variableName" to "oneTimeDownloadState", "notificationID" to "7777"))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(downloadOneTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(downloadOneTag, ExistingWorkPolicy.KEEP, downloadRequest)

        return downloadRequest.id
    }

    fun initiatePeriodicDownloadWorker(context : Context) : UUID {
        WorkStates.setState(
            workType = WorkType.PERIODIC_DOWNLOAD,
            workState = WorkInfo.State.RUNNING,
            context = context,
            tag = downloadPeriodTag
        )

        val downloadRequest = PeriodicWorkRequestBuilder<CoroutineDownloadWorker>(1, TimeUnit.HOURS)
            .setInputData(workDataOf("variableName" to "periodicDownloadState", "notificationID" to "8888"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(downloadPeriodTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(downloadPeriodTag, ExistingPeriodicWorkPolicy.KEEP, downloadRequest)

        return downloadRequest.id
    }
}


class CoroutineDownloadWorker(
    var context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    var NOTIFICATION_ID : Int? = -1
    val CHANNEL_ID = "alxkng5737"
    var stateVarString: String? = null

    val scope = CoroutineScope(Dispatchers.IO)

    
    override suspend fun doWork() : Result {
        stateVarString = inputData.getString("variableName")
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        if (stateVarString == "oneTimeDownloadState") {
            try {
                setForeground(getForegroundInfo())
            } catch(e: Exception) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: %s", e.message!!)
            }
        }

        when (stateVarString) {
            "oneTimeDownloadState" -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DOWNLOAD ONE TIME STARTED")
                // No need to set the state again for one time workers.
            }
            "periodicDownloadState" -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DOWNLOAD PERIODIC STARTED")

                /**
                 * Although this may seem redundant, we need to set the state to running here,
                 * because if we call initiatePeriodicWorker() and the worker is in its
                 * enqueued state (interval down time), then the setState() called there will not
                 * set the state to RUNNING due to its safety measures. However, when the periodic
                 * worker DOES start, we still need to make sure the state accurately reflects the
                 * actually RUNNING state of the worker.
                 */
                WorkStates.setState(WorkType.PERIODIC_DOWNLOAD, WorkInfo.State.RUNNING)
            }
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DOWNLOAD WORKER THREAD: Worker state variable name is wrong")
            }
        }

        val repository: ClientRepository = (applicationContext as App).repository

        /**
         * Downloads changes from server
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DOWNLOAD WORKER STARTED")
        RequestWrappers.downloadData(context, repository, scope, "DOWNlOAD WORKER")

        when (stateVarString) {
            "oneTimeDownloadState" -> WorkStates.setState(WorkType.ONE_TIME_DOWNLOAD, WorkInfo.State.SUCCEEDED)
            "periodicDownloadState" -> WorkStates.setState(WorkType.PERIODIC_DOWNLOAD, WorkInfo.State.SUCCEEDED)
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DOWNLOAD WORKER THREAD: Worker state variable name is wrong")
            }
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DOWNLOAD WORKER FOREGROUND")

        return ForegroundInfoCreator.createForegroundInfo(
            applicationContext = applicationContext,
            notificationID = NOTIFICATION_ID!!,
            channelID = CHANNEL_ID,
            contextText = "Downloading..."
        )
    }
}