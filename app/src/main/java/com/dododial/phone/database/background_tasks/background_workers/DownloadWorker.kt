package com.dododial.phone.database.background_tasks.background_workers

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.dododial.phone.App
import com.dododial.phone.DialerActivity
import com.dododial.phone.database.ClientRepository
import com.dododial.phone.database.background_tasks.WorkerStates
import com.dododial.phone.database.background_tasks.server_related.ServerHelpers
import timber.log.Timber
import java.lang.Exception
import java.util.*
import java.util.concurrent.TimeUnit

object DownloadScheduler {

    val downloadOneTag = "oneTimeDownloadWorker"
    val downloadPeriodTag = "periodicDownloadWorker"
    
    fun initiateOneTimeDownloadWorker(context : Context) : UUID {
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
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    var NOTIFICATION_ID : Int? = -1
    val CHANNEL_ID = "alxkng5737"
    var stateVarString: String? = null
    var context = context

    override suspend fun doWork() : Result {
        stateVarString = inputData.getString("variableName")
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        if (stateVarString == "oneTimeDownloadState") {
            try {
                setForeground(getForegroundInfo())
            } catch(e: Exception) {
                Timber.i("DODODEBUG: %s", e.message!!)
            }
        }

        val repository: ClientRepository? = (applicationContext as App).repository
        if (repository != null) {
            ServerHelpers.downloadPostRequest(context, repository, (applicationContext as App).applicationScope)
        } else {
            return Result.retry()
        }

        when (stateVarString) {
            "oneTimeDownloadState" ->  WorkerStates.oneTimeDownloadState = WorkInfo.State.SUCCEEDED
            "periodicDownloadState" -> WorkerStates.periodicDownloadState = WorkInfo.State.SUCCEEDED
            else -> {
                Timber.i("DODODEBUG: DOWNLOAD WORKER THREAD: Worker state variable name is wrong")
            }
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        val pendingIntent: PendingIntent = Intent(applicationContext, DialerActivity::class.java).let {
                notificationIntent: Intent ->
                    PendingIntent.getActivity(applicationContext, 0, notificationIntent, 0)
                }

        val notification : Notification = NotificationCompat.Builder(applicationContext)
            .setSmallIcon(android.R.mipmap.sym_def_app_icon)
            .setContentTitle("DodoDial")
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