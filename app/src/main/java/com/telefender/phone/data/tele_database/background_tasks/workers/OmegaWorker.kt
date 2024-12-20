package com.telefender.phone.data.tele_database.background_tasks.workers

import android.content.Context
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.ClientDatabase
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.TableSynchronizer
import com.telefender.phone.data.server_related.RequestWrappers
import com.telefender.phone.data.tele_database.background_tasks.ExperimentalWorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
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

    suspend fun initiateOneTimeOmegaWorker(context : Context) : UUID {
        ExperimentalWorkStates.generalizedSetState(
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

    suspend fun initiatePeriodicOmegaWorker(context : Context) : UUID {
        ExperimentalWorkStates.generalizedSetState(
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

    suspend fun cancelOneTimeOmegaWorker(context: Context) {
        ExperimentalWorkStates.generalizedSetState(WorkType.ONE_TIME_OMEGA, null)

        WorkManager
            .getInstance(context)
            .cancelAllWorkByTag(oneTimeOmegaWorkerTag)
    }

    suspend fun cancelPeriodicOmegaWorker(context: Context) {
        ExperimentalWorkStates.generalizedSetState(WorkType.PERIODIC_OMEGA, null)

        WorkManager
            .getInstance(context)
            .cancelAllWorkByTag(periodicOmegaWorkerTag)
    }
}

/**
 * TODO: Test OmegaWorker more
 *
 * TODO: Is it really necessary for periodic workers to setForeground? --> Think probably not?
 *
 * TODO: Uncomment download.
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
            Timber.i("$DBL: %s", e.message!!)
        }

        when (stateVarString) {
            "oneTimeOmegaState" -> {
                Timber.i("$DBL: OMEGA ONE TIME STARTED")
                // No need to set the state again for one time workers.
            }
            "periodicOmegaState" -> {
                Timber.i("$DBL: OMEGA PERIODIC STARTED")

                /**
                 * Although this may seem redundant, we need to set the state to running here,
                 * because if we call initiatePeriodicWorker() and the worker is in its
                 * enqueued state (interval down time), then the setState() called there will not
                 * set the state to RUNNING due to its safety measures. However, when the periodic
                 * worker DOES start, we still need to make sure the state accurately reflects the
                 * actually RUNNING state of the worker.
                 */
                ExperimentalWorkStates.generalizedSetState(WorkType.PERIODIC_OMEGA, WorkInfo.State.RUNNING)
            }
            else -> {
                Timber.i("$DBL: OMEGA WORKER: Worker state variable name is wrong")
            }
        }

        val repository: ClientRepository = (applicationContext as App).repository
        val database: ClientDatabase = (applicationContext as App).database

        /**
         * Sync with contacts and call logs
         */
        Timber.i("$DBL: OMEGA SYNC STARTED")
        TableSynchronizer.syncContacts(context, database, context.contentResolver)
        TableSynchronizer.syncCallLogs(context, repository, context.contentResolver)

        /**
         * Downloads changes from server
         */
        Timber.i("$DBL: OMEGA DOWNLOAD STARTED")
        RequestWrappers.downloadData(context, repository, scope, "OMEGA")

        /**
         * Executes logs in ExecuteQueue
         */
        Timber.i("$DBL: OMEGA EXECUTE_CHANGES STARTED")
        repository.executeAll()

        /**
         * Uploads changes to server.
         */
        Timber.i("$DBL: OMEGA UPLOAD_CHANGE STARTED")
        RequestWrappers.uploadChange(context, repository, scope, "OMEGA")

        /**
         * TODO: Confirm that we want to upload AnalyzedNumbers here. Note that we already have a
         *  parameter that allows us to choose whether or not we want to upload
         *  AnalyzedNumbers.
         *
         * Uploads analyzedNumbers to server.
         */
        Timber.i("$DBL: OMEGA UPLOAD_ANALYZED STARTED")
        RequestWrappers.uploadAnalyzed(context, repository, scope, "OMEGA")

        /**
         * Uploads error logs to server.
         */
        Timber.i("$DBL: OMEGA UPLOAD_ERROR STARTED")
        RequestWrappers.uploadError(context, repository, scope, "OMEGA")

        when (stateVarString) {
            "oneTimeOmegaState" -> {
                Timber.i("$DBL: OMEGA ONE TIME ENDED")
                ExperimentalWorkStates.generalizedSetState(WorkType.ONE_TIME_OMEGA, null)
            }
            "periodicOmegaState" -> {
                Timber.i("$DBL: OMEGA PERIODIC ENDED")
                ExperimentalWorkStates.generalizedSetState(WorkType.PERIODIC_OMEGA, null)
            }
            else -> {
                Timber.i("$DBL: OMEGA WORKER THREAD: Worker state variable name is wrong")
            }
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("$DBL: OMEGA WORKER FOREGROUND")

        return ForegroundInfoCreator.createForegroundInfo(
            applicationContext = applicationContext,
            notificationID = NOTIFICATION_ID!!,
            channelID = CHANNEL_ID,
            contextText = "TeleFender updating..."
        )
    }
}
