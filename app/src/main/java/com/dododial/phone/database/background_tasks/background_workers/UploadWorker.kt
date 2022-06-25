package com.dododial.phone.database.background_tasks.background_workers

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
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

object UploadScheduler {
    val uploadOneTag = "oneTimeUploadWorker"
    val uploadPeriodTag = "periodicUploadWorker"
    
    fun initiateOneTimeUploadWorker(context : Context) : UUID {
        val uploadRequest = OneTimeWorkRequestBuilder<CoroutineUploadWorker>()
            .setInputData(workDataOf("variableName" to "oneTimeUploadState", "notificationID" to "3333"))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(uploadOneTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(uploadOneTag, ExistingWorkPolicy.KEEP, uploadRequest)

        return uploadRequest.id
    }

    fun initiatePeriodicUploadWorker(context : Context) : UUID {
        val uploadRequest = PeriodicWorkRequestBuilder<CoroutineUploadWorker>(1, TimeUnit.HOURS)
            .setInputData(workDataOf("variableName" to "periodicUploadState", "notificationID" to "4444"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(uploadPeriodTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(uploadPeriodTag, ExistingPeriodicWorkPolicy.KEEP, uploadRequest)

        return uploadRequest.id
    }
}

class CoroutineUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    var NOTIFICATION_ID : Int? = -1
    val CHANNEL_ID = "alxkng5737"
    var stateVarString: String? = null
    val context = context

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork() : Result {
        stateVarString = inputData.getString("variableName")
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        if (stateVarString == "oneTimeUploadState") {
            try {
                setForeground(getForegroundInfo())
            } catch(e: Exception) {
                Timber.i("DODODEBUG: %s", e.message!!)
            }
        }

        val repository: ClientRepository? = (applicationContext as App).repository
        if (repository != null && repository.hasQTUs()) {
            ServerHelpers.uploadPostRequest(context, repository, (applicationContext as App).applicationScope)
        } else {
            if (repository?.hasQTUs() == false) {
                return Result.success()
            } else {
                return Result.retry()
            }
        }
        when (stateVarString) {
            "oneTimeUploadState" ->  WorkerStates.oneTimeUploadState = WorkInfo.State.SUCCEEDED
            "periodicUploadState" -> WorkerStates.periodicUploadState = WorkInfo.State.SUCCEEDED
            else -> {
                Timber.i("DODODEBUG: UPLOAD WORKER THREAD: Worker state variable name is wrong")
            }
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        val pendingIntent: PendingIntent = Intent(applicationContext, DialerActivity::class.java).let {
                notificationIntent: Intent ->
                    PendingIntent.getActivity(applicationContext, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
                }

        val notification : Notification = NotificationCompat.Builder(applicationContext)
            .setSmallIcon(android.R.mipmap.sym_def_app_icon)
            .setContentTitle("DodoDial")
            .setContentText("Uploading...")
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
