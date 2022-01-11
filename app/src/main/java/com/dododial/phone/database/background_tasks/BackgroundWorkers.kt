package com.dododial.phone.database.background_tasks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import androidx.work.impl.foreground.SystemForegroundService
import com.dododial.phone.App
import com.dododial.phone.DialerActivity
import com.dododial.phone.database.ClientRepository
import java.lang.Exception
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.TimeUnit

object WorkScheduler {

    val execPeriod = "periodicExecuteWorker"
    val execOne = "oneTimeExecuteWorker"
    
    
    // TODO fix the setExpedited with foreground stuff
    fun initiatePeriodicExecuteWorker(context : Context) : UUID {

        val executeRequest = PeriodicWorkRequestBuilder<CoroutineExecuteWorker>(1, TimeUnit.HOURS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(execPeriod)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(execPeriod, ExistingPeriodicWorkPolicy.KEEP, executeRequest)
        
        return executeRequest.id
    }

    fun initiateOneTimeExecuteWorker(context: Context) : UUID{

        val executeRequest = OneTimeWorkRequestBuilder<CoroutineExecuteWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(execOne)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(execOne, ExistingWorkPolicy.KEEP, executeRequest)

        return executeRequest.id
    }
}

class CoroutineExecuteWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {


    val NOTIFICATION_ID = 2425
    val CHANNEL_ID = "alxkng5737"

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as
            NotificationManager

    override suspend fun doWork(): Result {
        try {
            setForeground(getForegroundInfo())
        } catch(e: Exception) {
            Log.i("DODODEBUG: ", e.message!!)
        }

        val repository: ClientRepository? = (applicationContext as App).repository
        repository?.executeAll()

        if (repository?.hasQTEs() != false) {
            return Result.retry()
        } else {
            WorkerStates.initExecState = WorkInfo.State.SUCCEEDED
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
            .setContentText("Syncing...")
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

