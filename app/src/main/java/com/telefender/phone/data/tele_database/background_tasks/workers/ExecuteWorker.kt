package com.telefender.phone.data.tele_database.background_tasks.workers

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkerStates
import com.telefender.phone.data.tele_database.background_tasks.WorkerType
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.helpers.MiscHelpers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

object ExecuteScheduler {

    val execOneTag = "oneTimeExecuteWorker"
    val execPeriodTag = "periodicExecuteWorker"
    
    fun initiateOneTimeExecuteWorker(context: Context) : UUID {

        WorkerStates.setState(WorkerType.ONE_TIME_EXEC, WorkInfo.State.RUNNING)

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

        WorkerStates.setState(WorkerType.PERIODIC_EXEC, WorkInfo.State.RUNNING)

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

        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: EXECUTE STARTED")

        if (stateVarString == "oneTimeExecState") {
            try {
                setForeground(getForegroundInfo())
            } catch(e: Exception) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: %s", e.message!!)
            }
        }

        val repository: ClientRepository? = (applicationContext as App).repository

        repository?.executeAll()

        return if (repository?.hasQTE() != false) {
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: EXECUTE RETRY")
            Result.retry()
        } else {
            when (stateVarString) {
                "oneTimeExecState" -> WorkerStates.setState(WorkerType.ONE_TIME_EXEC, WorkInfo.State.SUCCEEDED)
                "periodicExecState" -> WorkerStates.setState(WorkerType.PERIODIC_EXEC, WorkInfo.State.SUCCEEDED)
                else -> {
                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: EXECUTE WORKER THREAD: Worker state variable name is wrong")
                }
            }
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: EXECUTE ENDED")
            Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: EXECUTE WORKER FOREGROUND")

        val pendingIntent: PendingIntent =
            Intent(applicationContext, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

        val notification : Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.mipmap.sym_def_app_icon)
            .setContentTitle("TeleFender")
            .setContentText("Executing...")
            .setContentIntent(pendingIntent)
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
