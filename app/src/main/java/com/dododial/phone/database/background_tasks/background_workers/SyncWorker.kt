package com.dododial.phone.database.background_tasks.background_workers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.dododial.phone.App
import com.dododial.phone.DialerActivity
import com.dododial.phone.database.ClientDatabase
import com.dododial.phone.database.ClientRepository
import com.dododial.phone.database.background_tasks.TableSynchronizer
import com.dododial.phone.database.background_tasks.WorkerStates
import kotlinx.coroutines.delay
import java.lang.Exception
import java.util.*
import java.util.concurrent.TimeUnit

object SyncScheduler{

    val syncOneTag = "oneTimeSyncWorker"
    val syncPeriodTag = "periodicSyncWorker"

    fun initiateOneTimeSyncWorker(context : Context) : UUID {
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

    @SuppressLint("LogNotTimber")
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork() : Result {
        stateVarString = inputData.getString("variableName")
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        if (stateVarString == "oneTimeSyncState") {
            try {
                setForeground(getForegroundInfo())
            } catch(e: Exception) {
                Log.i("DODODEBUG: ", e.message!!)
            }
        }

        val repository: ClientRepository? = (applicationContext as App).repository
        val database : ClientDatabase? = (applicationContext as App).database

        Log.i("DODODEBUG:", "SYNC STARTED")
        if (repository?.hasQTEs() != true && context != null && database != null) {
                TableSynchronizer.syncContacts(context, database, context.contentResolver)
            TableSynchronizer.syncCallLogs(database, context.contentResolver)
        } else {
            return Result.retry()
        }
        Log.i("DODODEBUG: ", "SYNC ENDED")

        when (stateVarString) {
            "oneTimeSyncState" ->  WorkerStates.oneTimeSyncState = WorkInfo.State.SUCCEEDED
            "periodicSyncState" -> WorkerStates.periodicSyncState = WorkInfo.State.SUCCEEDED
            else -> {
                Log.i("DODODEBUG: SYNC WORKER THREAD: ","Worker state variable name is wrong")
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