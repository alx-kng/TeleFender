package com.telefender.phone.data.tele_database.background_tasks.workers

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.server_related.ServerInteractions
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkerStates
import com.telefender.phone.data.tele_database.background_tasks.WorkerType
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.helpers.MiscHelpers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

object DownloadScheduler {

    val downloadOneTag = "oneTimeDownloadWorker"
    val downloadPeriodTag = "periodicDownloadWorker"
    
    fun initiateOneTimeDownloadWorker(context : Context) : UUID {

        WorkerStates.setState(WorkerType.ONE_TIME_DOWNLOAD, WorkInfo.State.RUNNING)

        val downloadRequest = OneTimeWorkRequestBuilder<CoroutineDownloadWorker>()
            .setInputData(workDataOf("variableName" to "oneTimeDownloadState", "notificationID" to "7777"))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(downloadOneTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(downloadOneTag, ExistingWorkPolicy.KEEP, downloadRequest)

        return downloadRequest.id
    }

    fun initiatePeriodicDownloadWorker(context : Context) : UUID {

        WorkerStates.setState(WorkerType.PERIODIC_DOWNLOAD, WorkInfo.State.RUNNING)

        val downloadRequest = PeriodicWorkRequestBuilder<CoroutineDownloadWorker>(1, TimeUnit.HOURS)
            .setInputData(workDataOf("variableName" to "periodicDownloadState", "notificationID" to "8888"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
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

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork() : Result {
        stateVarString = inputData.getString("variableName")
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        if (stateVarString == "oneTimeDownloadState") {
            try {
                setForeground(getForegroundInfo())
            } catch(e: Exception) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: %s", e.message!!)
            }
        }

        val repository: ClientRepository? = (applicationContext as App).repository
        if (repository != null) {
            ServerInteractions.downloadPostRequest(context, repository, (applicationContext as App).applicationScope)
        } else {
            return Result.retry()
        }

        when (stateVarString) {
            "oneTimeDownloadState" -> WorkerStates.setState(WorkerType.ONE_TIME_DOWNLOAD, WorkInfo.State.SUCCEEDED)
            "periodicDownloadState" -> WorkerStates.setState(WorkerType.PERIODIC_DOWNLOAD, WorkInfo.State.SUCCEEDED)
            else -> {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: DOWNLOAD WORKER THREAD: Worker state variable name is wrong")
            }
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: DOWNLOAD WORKER FOREGROUND")

        val pendingIntent: PendingIntent =
            Intent(applicationContext, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

        val notification : Notification = NotificationCompat.Builder(applicationContext)
            .setSmallIcon(android.R.mipmap.sym_def_app_icon)
            .setContentTitle("TeleFender")
            .setContentText("Downloading...")
            .setContentIntent(pendingIntent)
            .setChannelId(CHANNEL_ID)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID!!,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                NOTIFICATION_ID!!,
                notification
            )
        }
    }
}