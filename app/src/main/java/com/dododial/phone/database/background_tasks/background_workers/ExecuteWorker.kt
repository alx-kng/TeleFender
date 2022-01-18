package com.dododial.phone.database.background_tasks.background_workers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
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
import timber.log.Timber
import java.lang.Exception
import java.util.*
import java.util.concurrent.TimeUnit


object ExecuteScheduler {

    val execOneTag = "oneTimeExecuteWorker"
    val execPeriodTag = "periodicExecuteWorker"
    
    fun initiateOneTimeExecuteWorker(context: Context) : UUID {
        WorkerStates.oneTimeExecState = WorkInfo.State.RUNNING
        val executeRequest = OneTimeWorkRequestBuilder<CoroutineExecuteWorker>()
            .setInputData(workDataOf("variableName" to "oneTimeExecState", "notificationID" to "1111"))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(execOneTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(execOneTag, ExistingWorkPolicy.KEEP, executeRequest)

        return executeRequest.id
    }

    fun initiatePeriodicExecuteWorker(context : Context) : UUID {
        val executeRequest = PeriodicWorkRequestBuilder<CoroutineExecuteWorker>(1, TimeUnit.HOURS)
            .setInputData(workDataOf("variableName" to "periodicExecState", "notificationID" to "2222"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(execPeriodTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(execPeriodTag, ExistingPeriodicWorkPolicy.KEEP, executeRequest)

        return executeRequest.id
    }
}


class CoroutineExecuteWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {


    var NOTIFICATION_ID : Int? = -1
    val CHANNEL_ID = "alxkng5737"
    var stateVarString: String? = null

    override suspend fun doWork(): Result {
        stateVarString = inputData.getString("variableName")
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        if (stateVarString == "oneTimeExecState") {
            try {
                setForeground(getForegroundInfo())
            } catch(e: Exception) {
                Timber.d("DODODEBUG: %s", e.message!!)
            }
        }

        val repository: ClientRepository? = (applicationContext as App).repository
        repository?.executeAll()

        Timber.d("DODODEBUG: EXECUTE STARTED")
        if (repository?.hasQTEs() != false) {
            return Result.retry()
        } else {

            when (stateVarString) {
                "oneTimeExecState" -> WorkerStates.oneTimeExecState = WorkInfo.State.SUCCEEDED
                "periodicExecState" -> WorkerStates.periodicExecState = WorkInfo.State.SUCCEEDED
                else -> {
                    Timber.d("DODODEBUG: EXECUTE WORKER THREAD: Worker state variable name is wrong")
                }
            }
            Timber.d("DODODEBUG: EXECUTE ENDED")
            return Result.success()
        }

    }

    override suspend fun getForegroundInfo(): ForegroundInfo {

        val pendingIntent: PendingIntent = Intent(applicationContext, DialerActivity::class.java).let {
                notificationIntent: Intent ->
            PendingIntent.getActivity(applicationContext, 0, notificationIntent, 0)
        }

        val notification : Notification = NotificationCompat.Builder(applicationContext)
            .setSmallIcon(android.R.mipmap.sym_def_app_icon)
            .setContentTitle("DodoDial")
            .setContentText("Executing...")
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
