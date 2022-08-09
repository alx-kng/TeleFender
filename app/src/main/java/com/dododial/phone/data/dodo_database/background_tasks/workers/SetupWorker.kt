package com.dododial.phone.data.dodo_database.background_tasks.workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.dododial.phone.App
import com.dododial.phone.gui.DialerActivity
import com.dododial.phone.data.dodo_database.ClientRepository
import com.dododial.phone.data.dodo_database.background_tasks.WorkerStates
import java.util.*
import java.util.concurrent.TimeUnit
import android.content.pm.ServiceInfo
import android.app.Notification
import androidx.annotation.RequiresApi
import com.dododial.phone.data.server_related.UserSetup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import timber.log.Timber

object SetupScheduler {
    val setupTag = "setupWorker"

    fun initiateSetupWorker(context: Context): UUID {
        val uploadRequest = OneTimeWorkRequestBuilder<CoroutineSetupWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(setupTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(setupTag, ExistingWorkPolicy.KEEP, uploadRequest)

        return uploadRequest.id
    }
}

class CoroutineSetupWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    var NOTIFICATION_ID = 4324
    val CHANNEL_ID = "alxkng5737"

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork() : Result {
        try {
            setForeground(getForegroundInfo())
        } catch(e: Exception) {
            Timber.i("DODODEBUG: %s", e.message!!)
        }

        val repository : ClientRepository = (applicationContext as App).repository
//        val scope = (applicationContext as App).applicationScope
        val scope = CoroutineScope(Dispatchers.IO)

        WorkerStates.setupState = WorkInfo.State.RUNNING
        UserSetup.initialPostRequest(context, repository, scope)

        while (WorkerStates.setupState == WorkInfo.State.RUNNING) {
            delay(1000)
            Timber.i("DODODEBUG: SETUP WORKER STILL RUNNING")
        }

        if (WorkerStates.setupState == WorkInfo.State.FAILED) {
            Timber.i("DODODEBUG: SETUP WORKER RETRYING...")
            return Result.retry()
        }

        Timber.i("DODODEBUG: SETUP WORKER DONE")
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
            .setContentText("Connecting to Server...")
            .setContentIntent(pendingIntent)
            .setChannelId(CHANNEL_ID)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification
            )
        }
    }
}