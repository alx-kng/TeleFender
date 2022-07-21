package com.dododial.phone.database.background_tasks.background_workers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.Cursor
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.dododial.phone.App
import com.dododial.phone.DialerActivity
import com.dododial.phone.database.ClientDatabase
import com.dododial.phone.database.ClientRepository
import com.dododial.phone.database.DatabaseLogFunctions
import com.dododial.phone.database.android_db.ContactDetailsHelper
import com.dododial.phone.database.background_tasks.TableInitializers.cursContactInsert
import com.dododial.phone.database.background_tasks.TableSynchronizer
import com.dododial.phone.database.background_tasks.WorkerStates
import com.dododial.phone.database.background_tasks.server_related.ServerHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import timber.log.Timber
import java.lang.Exception
import java.util.*
import java.util.concurrent.TimeUnit

object OmegaPeriodicScheduler {

    val oneTimeOmegaWorkerTag = "oneTimeOmegaWorker"
    val periodicOmegaWorkerTag = "periodicOmegaWorker"

    fun initiateOneTimeOmegaWorker(context : Context) : UUID {
        WorkerStates.oneTimeOmegaState = WorkInfo.State.RUNNING
        val uploadRequest = OneTimeWorkRequestBuilder<CoroutineOmegaWorker>()
            .setInputData(workDataOf("variableName" to "oneTimeOmegaState", "notificationID" to "6565"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                PeriodicWorkRequest.MIN_BACKOFF_MILLIS, // TODO Change backoff time to around a min
                TimeUnit.MILLISECONDS)
            .setInitialDelay(15, TimeUnit.SECONDS) // TODO Change delay to 30 min later
            .addTag(oneTimeOmegaWorkerTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(oneTimeOmegaWorkerTag, ExistingWorkPolicy.KEEP, uploadRequest)

        return uploadRequest.id
    }

    fun initiatePeriodicOmegaWorker(context : Context) : UUID {
        WorkerStates.periodicOmegaState = WorkInfo.State.RUNNING
        val uploadRequest = PeriodicWorkRequestBuilder<CoroutineOmegaWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf("variableName" to "periodicOmegaState", "notificationID" to "5656"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                PeriodicWorkRequest.MIN_BACKOFF_MILLIS, // TODO Change backoff time to around a min
                TimeUnit.MILLISECONDS)
            .setInitialDelay(15, TimeUnit.SECONDS) // TODO Change delay to 30 min later
            .addTag(periodicOmegaWorkerTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(periodicOmegaWorkerTag, ExistingPeriodicWorkPolicy.KEEP, uploadRequest)

        return uploadRequest.id
    }

    fun cancelOneTimeOmegaWorker(context: Context) {
        WorkManager
            .getInstance(context)
            .cancelAllWorkByTag(oneTimeOmegaWorkerTag)
    }

    fun cancelPeriodicOmegaWorker(context: Context) {
        WorkManager
            .getInstance(context)
            .cancelAllWorkByTag(periodicOmegaWorkerTag)
    }
}

// TODO may need to make into long running worker
// TODO synchronize worker threads and use of resources (particularly database)
// TODO worker should run continuously since server will send batches of changes instead of all
class CoroutineOmegaWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    var NOTIFICATION_ID : Int? = -1
    val CHANNEL_ID = "alxkng5737"
    val context: Context? = context
    var stateVarString: String? = null
    val scope = CoroutineScope(Dispatchers.IO)

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork() : Result {

        stateVarString = inputData.getString("variableName")
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        try {
            setForeground(getForegroundInfo())
        } catch(e: Exception) {
            Timber.i("DODODEBUG: %s", e.message!!)
        }

        when (stateVarString) {
            "oneTimeOmegaState" -> {
                Timber.i("DODODEBUG: OMEGA ONE TIME STARTED")
            }
            "periodicOmegaState" -> {
                Timber.i("DODODEBUG: OMEGA PERIODIC STARTED")
            }
            else -> {
                Timber.i("DODODEBUG: OMEGA WORKER THREAD: Worker state variable name is wrong")
            }
        }

        val repository: ClientRepository? = (applicationContext as App).repository
        val database: ClientDatabase? = (applicationContext as App).database

        if (context != null && database != null && repository != null) {

            /**
             * Sync with contacts and call logs
             */

            if (!repository.hasQTEs()) {
                TableSynchronizer.syncContacts(context, database, context.contentResolver)
                TableSynchronizer.syncCallLogs(database, context.contentResolver)
            } else {
                repository.executeAll()
                return Result.retry()
            }

            /**
             * Downloads changes from server
             */
            WorkerStates.downloadPostState = WorkInfo.State.RUNNING
            ServerHelpers.downloadPostRequest(context, repository, scope)

            /**
             * Used to observe the download state. Makes sure that download is finished before
             * execution begins
             */
            while (WorkerStates.downloadPostState == WorkInfo.State.RUNNING) {
                delay(1000)
                Timber.i("DODODEBUG: DOWNLOAD STILL RUNNING")
            }
            WorkerStates.downloadPostState = null

            /**
             * Executes logs in QueueToExecute
             */
            repository.executeAll()
            if (repository.hasQTEs()) {
                return Result.retry()
            }

            /**
             * Uploads changes to server
             * If a big error occurs, like a 404, the upload should not continue
             */
            WorkerStates.uploadPostState = WorkInfo.State.RUNNING
            if (repository.hasQTUs()) {
                ServerHelpers.uploadPostRequest(context, repository, scope)
            } else {
                WorkerStates.uploadPostState = WorkInfo.State.SUCCEEDED
            }

            /**
             * Used to observe the upload state. Makes sure that upload doesn't have a big error before
             * retrying
             */
            while (WorkerStates.uploadPostState == WorkInfo.State.RUNNING) {
                delay(1000)
                Timber.i("DODODEBUG: UPLOAD STILL RUNNING")
            }
            
            if (WorkerStates.uploadPostState == WorkInfo.State.FAILED) {
                Timber.e("DODODEBUG: OMEGA PERIODIC ENDED EARLY. PROBLEM WITH UPLOAD.")
                return Result.failure()
            }
            WorkerStates.uploadPostState = null

            if (repository.hasQTUs()) {
                return Result.retry()
            }

            when (stateVarString) {
                "oneTimeOmegaState" -> {
                    WorkerStates.oneTimeOmegaState = WorkInfo.State.SUCCEEDED
                    Timber.i("DODODEBUG: OMEGA ONE TIME ENDED")
                }
                "periodicOmegaState" -> {
                    WorkerStates.periodicOmegaState = WorkInfo.State.SUCCEEDED
                    Timber.i("DODODEBUG: OMEGA PERIODIC ENDED")
                }
                else -> {
                    Timber.i("DODODEBUG: OMEGA WORKER THREAD: Worker state variable name is wrong")
                }
            }

            DatabaseLogFunctions.logContacts(database, null)
            DatabaseLogFunctions.logContactNumbers(database, null)
            DatabaseLogFunctions.logChangeLogs(database, null)
            DatabaseLogFunctions.logExecuteLogs(database, null)
            DatabaseLogFunctions.logUploadLogs(database, null)

            return Result.success()
        } else {
            return Result.retry()
        }
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        val pendingIntent: PendingIntent = Intent(applicationContext, DialerActivity::class.java).let {
                notificationIntent: Intent ->
            PendingIntent.getActivity(applicationContext, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        val notification : Notification = NotificationCompat.Builder(applicationContext)
            .setSmallIcon(android.R.mipmap.sym_def_app_icon)
            .setContentTitle("DodoDial")
            .setContentText("DodoDial updating...")
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
