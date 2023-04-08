package com.telefender.phone.data.tele_database.background_tasks.workers

import android.content.Context
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.background_tasks.TableSynchronizer
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * TODO: Enforce Application Context
 *
 * All workers NEED TO USE application context, or else the context will probably be null.
 */
object RegularSyncScheduler{

    val syncOneTag = "oneTimeSyncWorker"
    val syncPeriodicTag = "periodicSyncWorker"

    fun initiateOneTimeSyncWorker(context : Context) : UUID? {
        if (!TeleHelpers.hasValidStatus(context, logRequired = true)) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: Invalid status in initiateOneTimeSyncWorker()")
            return null
        }

        WorkStates.setState(
            workType = WorkType.ONE_TIME_SYNC,
            workState = WorkInfo.State.RUNNING,
            context = context,
            tag = syncOneTag
        )

        val syncRequest = OneTimeWorkRequestBuilder<CoroutineSyncWorker>()
            .setInputData(workDataOf("variableName" to "oneTimeSyncState", "notificationID" to "5555"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(syncOneTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(syncOneTag, ExistingWorkPolicy.KEEP, syncRequest)

        return syncRequest.id
    }

    fun initiatePeriodicSyncWorker(context : Context) : UUID? {
        if (!TeleHelpers.hasValidStatus(context, logRequired = true)) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: Invalid status in initiatePeriodicSyncWorker()")
            return null
        }

        WorkStates.setState(
            workType = WorkType.PERIODIC_SYNC,
            workState = WorkInfo.State.RUNNING,
            context = context,
            tag = syncPeriodicTag
        )

        val syncRequest = PeriodicWorkRequestBuilder<CoroutineSyncWorker>(1, TimeUnit.HOURS)
            .setInputData(workDataOf("variableName" to "periodicSyncState", "notificationID" to "6666"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .setInitialDelay(10, TimeUnit.SECONDS)
            .addTag(syncPeriodicTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(syncPeriodicTag, ExistingPeriodicWorkPolicy.KEEP, syncRequest)

        return syncRequest.id
    }
}

class CoroutineSyncWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    var NOTIFICATION_ID : Int? = -1
    val CHANNEL_ID = "alxkng5737"
    var stateVarString: String? = null

    
    override suspend fun doWork() : Result {
        stateVarString = inputData.getString("variableName")
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        try {
            setForeground(getForegroundInfo())
        } catch(e: Exception) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: %s", e.message!!)
        }

        when (stateVarString) {
            "oneTimeSyncState" ->  {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SYNC ONE TIME STARTED")
                // No need to set the state again for one time workers.
            }
            "periodicSyncState" -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SYNC PERIODIC STARTED")

                /**
                 * Although this may seem redundant, we need to set the state to running here,
                 * because if we call initiatePeriodicWorker() and the worker is in its
                 * enqueued state (interval down time), then the setState() called there will not
                 * set the state to RUNNING due to its safety measures. However, when the periodic
                 * worker DOES start, we still need to make sure the state accurately reflects the
                 * actually RUNNING state of the worker.
                 */
                WorkStates.setState(WorkType.PERIODIC_SYNC, WorkInfo.State.RUNNING)
            }
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SYNC WORKER THREAD: Worker state variable name is wrong")
            }
        }

        val repository = (applicationContext as App).repository
        val database = (applicationContext as App).database

        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SYNC STARTED")

        TableSynchronizer.syncContacts(context, database, context.contentResolver)
        TableSynchronizer.syncCallLogs(context, repository, context.contentResolver)

        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SYNC ENDED")

        when (stateVarString) {
            "oneTimeSyncState" ->  WorkStates.setState(WorkType.ONE_TIME_SYNC, WorkInfo.State.SUCCEEDED)
            "periodicSyncState" -> WorkStates.setState(WorkType.PERIODIC_SYNC, WorkInfo.State.SUCCEEDED)
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SYNC WORKER THREAD: Worker state variable name is wrong")
            }
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SYNC WORKER FOREGROUND")

        return ForegroundInfoCreator.createForegroundInfo(
            applicationContext = applicationContext,
            notificationID = NOTIFICATION_ID!!,
            channelID = CHANNEL_ID,
            contextText = "Syncing..."
        )
    }
}