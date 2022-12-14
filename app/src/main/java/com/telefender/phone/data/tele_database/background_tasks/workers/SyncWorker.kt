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
import com.telefender.phone.data.tele_database.ClientDatabase
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.TableSynchronizer
import com.telefender.phone.data.tele_database.background_tasks.WorkerStates
import com.telefender.phone.data.tele_database.background_tasks.WorkerType
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.helpers.MiscHelpers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

object SyncScheduler{

    val syncOneTag = "oneTimeSyncWorker"
    val syncPeriodTag = "periodicSyncWorker"

    fun initiateOneTimeSyncWorker(context : Context) : UUID {

        WorkerStates.setState(WorkerType.ONE_TIME_SYNC, WorkInfo.State.RUNNING)

        val syncRequest = OneTimeWorkRequestBuilder<CoroutineSyncWorker>()
            .setInputData(workDataOf("variableName" to "oneTimeSyncState", "notificationID" to "5555"))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(syncOneTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(syncOneTag, ExistingWorkPolicy.KEEP, syncRequest)

        return syncRequest.id
    }

    fun initiatePeriodicSyncWorker(context : Context) : UUID {

        WorkerStates.setState(WorkerType.PERIODIC_SYNC, WorkInfo.State.RUNNING)

        val syncRequest = PeriodicWorkRequestBuilder<CoroutineSyncWorker>(1, TimeUnit.HOURS)
            .setInputData(workDataOf("variableName" to "periodicSyncState", "notificationID" to "6666"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .setInitialDelay(10, TimeUnit.SECONDS)
            .addTag(syncPeriodTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(syncPeriodTag, ExistingPeriodicWorkPolicy.KEEP, syncRequest)

        return syncRequest.id
    }
}

class CoroutineSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    var NOTIFICATION_ID : Int? = -1
    val CHANNEL_ID = "alxkng5737"
    var stateVarString: String? = null
    val context: Context? = context

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork() : Result {
        stateVarString = inputData.getString("variableName")
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        if (stateVarString == "oneTimeSyncState") {
            try {
                setForeground(getForegroundInfo())
            } catch(e: Exception) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: %s", e.message!!)
            }
        }

        val repository: ClientRepository = (applicationContext as App).repository
        val database : ClientDatabase = (applicationContext as App).database

        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: SYNC STARTED")
        if (!repository.hasQTEs() && context != null) {
                TableSynchronizer.syncContacts(context, database, context.contentResolver)
            TableSynchronizer.syncCallLogs(database, repository, context.contentResolver)
        } else {
            return Result.retry()
        }
        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: SYNC ENDED")

        when (stateVarString) {
            "oneTimeSyncState" ->  WorkerStates.setState(WorkerType.ONE_TIME_SYNC, WorkInfo.State.RUNNING)
            "periodicSyncState" -> WorkerStates.setState(WorkerType.PERIODIC_SYNC, WorkInfo.State.RUNNING)
            else -> {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: SYNC WORKER THREAD: Worker state variable name is wrong")
            }
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: SYNC WORKER FOREGROUND")

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
            .setContentText("Syncing...")
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