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
import com.telefender.phone.data.tele_database.background_tasks.ServerWorkHelpers
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

// TODO: Change delay and backoff time later for production / optimization.
object OmegaPeriodicScheduler {

    const val oneTimeOmegaWorkerTag = "oneTimeOmegaWorker"
    const val periodicOmegaWorkerTag = "periodicOmegaWorker"

    fun initiateOneTimeOmegaWorker(context : Context) : UUID {
        WorkStates.setState(WorkType.ONE_TIME_OMEGA, WorkInfo.State.RUNNING)

        val uploadRequest = OneTimeWorkRequestBuilder<CoroutineOmegaWorker>()
            .setInputData(workDataOf("variableName" to "oneTimeOmegaState", "notificationID" to "6565"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .setInitialDelay(0, TimeUnit.SECONDS)
            .addTag(oneTimeOmegaWorkerTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(oneTimeOmegaWorkerTag, ExistingWorkPolicy.KEEP, uploadRequest)

        return uploadRequest.id
    }

    fun initiatePeriodicOmegaWorker(context : Context) : UUID {
        WorkStates.setState(WorkType.PERIODIC_OMEGA, WorkInfo.State.RUNNING)

        val uploadRequest = PeriodicWorkRequestBuilder<CoroutineOmegaWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf("variableName" to "periodicOmegaState", "notificationID" to "5656"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .setInitialDelay(0, TimeUnit.SECONDS)
            .addTag(periodicOmegaWorkerTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(periodicOmegaWorkerTag, ExistingPeriodicWorkPolicy.KEEP, uploadRequest)

        return uploadRequest.id
    }

    fun cancelOneTimeOmegaWorker(context: Context) {
        WorkStates.setState(WorkType.ONE_TIME_OMEGA, null)

        WorkManager
            .getInstance(context)
            .cancelAllWorkByTag(oneTimeOmegaWorkerTag)
    }

    fun cancelPeriodicOmegaWorker(context: Context) {
        WorkStates.setState(WorkType.PERIODIC_OMEGA, null)

        WorkManager
            .getInstance(context)
            .cancelAllWorkByTag(periodicOmegaWorkerTag)
    }
}

/**
 * TODO: Test OmegaWorker more
 * TODO: Clean up / finish notification implementation in getForegroundInfo()
 */
class CoroutineOmegaWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private var NOTIFICATION_ID : Int? = -1
    private val CHANNEL_ID = "alxkng5737"
    private var stateVarString: String? = null

    val scope = CoroutineScope(Dispatchers.IO)

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork() : Result {

        stateVarString = inputData.getString("variableName")
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        try {
            setForeground(getForegroundInfo())
        } catch(e: Exception) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: %s", e.message!!)
        }

        when (stateVarString) {
            "oneTimeOmegaState" -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA ONE TIME STARTED")
            }
            "periodicOmegaState" -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA PERIODIC STARTED")
            }
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA WORKER THREAD: Worker state variable name is wrong")
            }
        }

        val repository: ClientRepository = (applicationContext as App).repository
        val database: ClientDatabase = (applicationContext as App).database

        /**
         * Sync with contacts and call logs
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA SYNC STARTED")
        TableSynchronizer.syncContacts(context, database, context.contentResolver)
        TableSynchronizer.syncCallLogs(context, repository, context.contentResolver)

        /**
         * Downloads changes from server
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA DOWNLOAD STARTED")
        ServerWorkHelpers.downloadData(context, repository, scope, "OMEGA")

        /**
         * Executes logs in ExecuteQueue
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA EXECUTE STARTED")
        repository.executeAll()

        /**
         * Uploads changes to server.
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA UPLOAD_CHANGE STARTED")
        ServerWorkHelpers.uploadChange(context, repository, scope, "OMEGA")

        /**
         * TODO: Confirm that we want to upload AnalyzedNumbers here. Note that we already have a
         *  parameter that allows us to choose whether or not we want to upload
         *  AnalyzedNumbers.
         *
         * Uploads analyzedNumbers to server.
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA UPLOAD_ANALYZED STARTED")
        ServerWorkHelpers.uploadAnalyzed(context, repository, scope, "OMEGA")

        /**
         * Uploads error logs to server.
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA UPLOAD_ERROR STARTED")
        ServerWorkHelpers.uploadError(context, repository, scope, "OMEGA")

        when (stateVarString) {
            "oneTimeOmegaState" -> {
                WorkStates.setState(WorkType.ONE_TIME_OMEGA, WorkInfo.State.SUCCEEDED)
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA ONE TIME ENDED")
            }
            "periodicOmegaState" -> {
                WorkStates.setState(WorkType.PERIODIC_OMEGA, WorkInfo.State.SUCCEEDED)
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA PERIODIC ENDED")
            }
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA WORKER THREAD: Worker state variable name is wrong")
            }
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA WORKER FOREGROUND")

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
            .setContentText("TeleFender updating...")
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
