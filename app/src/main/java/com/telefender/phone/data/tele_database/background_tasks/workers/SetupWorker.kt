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
import com.telefender.phone.data.server_related.UserSetup
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkerStates
import com.telefender.phone.data.tele_database.background_tasks.WorkerType
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.helpers.MiscHelpers
import com.telefender.phone.permissions.PermissionRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

object SetupScheduler {
    val setupTag = "setupWorker"

    fun initiateSetupWorker(context: Context): UUID? {
        if (!PermissionRequester.hasLogPermissions(context)) {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: No log permissions in initiateSetupWorker()")
            return null
        }

        WorkerStates.setState(WorkerType.SETUP, WorkInfo.State.RUNNING)

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
    val scope = CoroutineScope(Dispatchers.IO)

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork() : Result {
        WorkerStates.setState(WorkerType.SETUP, WorkInfo.State.RUNNING)

        try {
            setForeground(getForegroundInfo())
        } catch(e: Exception) {
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: %s", e.message!!)
        }

        val repository : ClientRepository = (applicationContext as App).repository
        val instanceNumber = MiscHelpers.getInstanceNumber(context)

        // Stops worker if user is already setup.
        if (repository.hasCredKey(instanceNumber!!)) {
            return Result.success()
        }

        UserSetup.initialPostRequest(context, repository, scope)

        if(!WorkerStates.workerWaiter(WorkerType.SETUP, "SETUP WORKER", stopOnFail = true)) {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: SETUP WORKER RETRYING...")
            return Result.retry()
        }

        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: SETUP WORKER DONE")
        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: SETUP WORKER FOREGROUND")

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