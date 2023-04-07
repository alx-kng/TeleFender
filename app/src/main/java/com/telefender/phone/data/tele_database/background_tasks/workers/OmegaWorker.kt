package com.telefender.phone.data.tele_database.background_tasks.workers

import android.content.Context
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.ClientDatabase
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.TableSynchronizer
import com.telefender.phone.data.server_related.RequestWrappers
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * TODO: Change delay and backoff time later for production / optimization.
 * TODO: Enforce Application Context
 *
 * TODO: DANGEROUS TO SET RUNNING STATE FOR PERIODIC WORKER OUTSIDE OF doWork() as RUNNING STATE
 *  WILL BE SET, BUT THE ACTUAL work might not be, as there is a minimum repeat interval time,
 *  during which initiatePeriodicOmegaWorker() won't actually launch worker.
 *  --> Fixed with updated setState(), which uses WorkManagerHelper. Should still double check though.
 *
 * TODO: Change workers to use WorkManagerHelper functions (which actually check worker state).
 *  --> Done. But should double check.
 *
 * TODO for all workers with work that can wait a bit, maybe we set constraints so it begins when charging/full battery
 */
object OmegaScheduler {

    const val oneTimeOmegaWorkerTag = "oneTimeOmegaWorker"
    const val periodicOmegaWorkerTag = "periodicOmegaWorker"

    fun initiateOneTimeOmegaWorker(context : Context) : UUID {
        WorkStates.setState(
            workType = WorkType.ONE_TIME_OMEGA,
            workState = WorkInfo.State.RUNNING,
            context = context,
            tag = oneTimeOmegaWorkerTag
        )

        val omegaRequest = OneTimeWorkRequestBuilder<CoroutineOmegaWorker>()
            .setInputData(workDataOf("variableName" to "oneTimeOmegaState", "notificationID" to "6565"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .setInitialDelay(0, TimeUnit.SECONDS)
            .addTag(oneTimeOmegaWorkerTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(oneTimeOmegaWorkerTag, ExistingWorkPolicy.KEEP, omegaRequest)

        return omegaRequest.id
    }

    fun initiatePeriodicOmegaWorker(context : Context) : UUID {
        WorkStates.setState(
            workType = WorkType.PERIODIC_OMEGA,
            workState = WorkInfo.State.RUNNING,
            context = context,
            tag = periodicOmegaWorkerTag
        )

        val omegaRequest = PeriodicWorkRequestBuilder<CoroutineOmegaWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf("variableName" to "periodicOmegaState", "notificationID" to "5656"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .setInitialDelay(0, TimeUnit.SECONDS)
            .addTag(periodicOmegaWorkerTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(periodicOmegaWorkerTag, ExistingPeriodicWorkPolicy.KEEP, omegaRequest)

        return omegaRequest.id
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
 */
class CoroutineOmegaWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private var NOTIFICATION_ID : Int? = -1
    private val CHANNEL_ID = "alxkng5737"
    private var stateVarString: String? = null

    val scope = CoroutineScope(Dispatchers.IO)

    
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
                WorkStates.setState(WorkType.PERIODIC_OMEGA, WorkInfo.State.RUNNING)
            }
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA WORKER: Worker state variable name is wrong")
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
//        RequestWrappers.downloadData(context, repository, scope, "OMEGA")

        /**
         * Executes logs in ExecuteQueue
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA EXECUTE STARTED")
        repository.executeAll()

        /**
         * Uploads changes to server.
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA UPLOAD_CHANGE STARTED")
        RequestWrappers.uploadChange(context, repository, scope, "OMEGA")

        /**
         * TODO: Confirm that we want to upload AnalyzedNumbers here. Note that we already have a
         *  parameter that allows us to choose whether or not we want to upload
         *  AnalyzedNumbers.
         *
         * Uploads analyzedNumbers to server.
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA UPLOAD_ANALYZED STARTED")
        RequestWrappers.uploadAnalyzed(context, repository, scope, "OMEGA")

        /**
         * Uploads error logs to server.
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA UPLOAD_ERROR STARTED")
        RequestWrappers.uploadError(context, repository, scope, "OMEGA")

        when (stateVarString) {
            "oneTimeOmegaState" -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA ONE TIME ENDED")
                WorkStates.setState(WorkType.ONE_TIME_OMEGA, WorkInfo.State.SUCCEEDED)
            }
            "periodicOmegaState" -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA PERIODIC ENDED")
                WorkStates.setState(WorkType.PERIODIC_OMEGA, WorkInfo.State.SUCCEEDED)
            }
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA WORKER THREAD: Worker state variable name is wrong")
            }
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OMEGA WORKER FOREGROUND")

        return ForegroundInfoCreator.createForegroundInfo(
            applicationContext = applicationContext,
            notificationID = NOTIFICATION_ID!!,
            channelID = CHANNEL_ID,
            contextText = "TeleFender updating..."
        )
    }
}
