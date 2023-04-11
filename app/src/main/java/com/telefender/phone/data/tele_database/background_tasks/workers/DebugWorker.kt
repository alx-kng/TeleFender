package com.telefender.phone.data.tele_database.background_tasks.workers


import android.content.Context
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.server_related.RequestWrappers
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit



/**
 * TODO: Enforce Application Context
 *
 * TODO: Find better way to make sure that only one of the one-time debug or periodic debug is
 *  working at one time. ACTUALLY WE CAN GET RID OF ONE-TIME DEBUG LATER. THIS IS ONLY FOR TESTING
 *  RIGHT NOW. PLUS I'M PRETTY SURE ONE-TIME WORK CAN ONLY LAST SO LONG.
 *  --> Maybe we can make a switch for debugging.
 *
 * TODO: Should probably automatically close debug after like 5 min of inactivity to prevent
 *  infinite loop. -> pretty sure this is done
 */
object DebugScheduler {

    const val oneTimeDebugTag = "oneTimeDebugTag"
    const val periodicDebugTag = "periodicDebugTag"

    fun initiateOneTimeDebugWorker(context : Context) : UUID {
        WorkStates.setState(
            workType = WorkType.ONE_TIME_DEBUG,
            workState = WorkInfo.State.RUNNING,
            context = context,
            tag = oneTimeDebugTag
        )

        val debugRequest = OneTimeWorkRequestBuilder<CoroutineDebugWorker>()
            .setInputData(workDataOf("variableName" to "oneTimeDebugState", "notificationID" to "7654"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .setInitialDelay(0, TimeUnit.SECONDS)
            .addTag(oneTimeDebugTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(oneTimeDebugTag, ExistingWorkPolicy.KEEP, debugRequest)

        return debugRequest.id
    }

    fun initiatePeriodicDebugWorker(context : Context) : UUID {
        WorkStates.setState(
            workType = WorkType.PERIODIC_DEBUG,
            workState = WorkInfo.State.RUNNING,
            context = context,
            tag = periodicDebugTag
        )

        val debugRequest = PeriodicWorkRequestBuilder<CoroutineDebugWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf("variableName" to "periodicDebugState", "notificationID" to "7654"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .setInitialDelay(0, TimeUnit.SECONDS)
            .addTag(periodicDebugTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(periodicDebugTag, ExistingPeriodicWorkPolicy.KEEP, debugRequest)

        return debugRequest.id
    }
}

class CoroutineDebugWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private var NOTIFICATION_ID : Int? = -1
    private val CHANNEL_ID = "alxkng5737"
    private var stateVarString: String? = null

    val scope = CoroutineScope(Dispatchers.IO)

    /**
     * TODO: Still have problem with mutually exclusive work.
     */
    override suspend fun doWork() : Result {
        stateVarString = inputData.getString("variableName")
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        when (stateVarString) {
            "oneTimeDebugState" -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: ONE TIME DEBUG STARTED")
                // No need to set the state again for one time workers.

                // Makes sure that both debug workers aren't running at the same time.
                if (WorkStates.mutuallyExclusiveWork(WorkType.PERIODIC_DEBUG, WorkType.ONE_TIME_DEBUG)) {
                    return Result.success()
                }
            }
            "periodicDebugState" -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: PERIODIC DEBUG STARTED")

                /**
                 * Although this may seem redundant, we need to set the state to running here,
                 * because if we call initiatePeriodicWorker() and the worker is in its
                 * enqueued state (interval down time), then the setState() called there will not
                 * set the state to RUNNING due to its safety measures. However, when the periodic
                 * worker DOES start, we still need to make sure the state accurately reflects the
                 * actually RUNNING state of the worker.
                 */
                WorkStates.setState(WorkType.PERIODIC_DEBUG, WorkInfo.State.RUNNING)

                // Makes sure that both debug workers aren't running at the same time.
                if (WorkStates.mutuallyExclusiveWork(WorkType.ONE_TIME_DEBUG, WorkType.PERIODIC_DEBUG)) {
                    return Result.success()
                }
            }
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DEBUG WORKER: Worker state variable name is wrong")
            }
        }

        try {
            setForeground(getForegroundInfo())
        } catch(e: Exception) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: %s", e.message!!)
        }

        val repository: ClientRepository = (applicationContext as App).repository

        /**
         * Checks if should start debug from server.
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DEBUG CHECK STARTED")
        RequestWrappers.debugCheck(context, repository, scope, "DEBUG")

        /**
         * Retrieves if remote session ID from server if the debug is enabled.
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: GET DEBUG SESSION STARTED")
        RequestWrappers.debugSession(context, repository, scope, "DEBUG")

        /**
         * Starts command / data exchange between client and server if remote session ID exists.
         * This is the final wait point before the worker ends. If a debug exchange is occurring,
         * then the code just waits here, otherwise the code continues and the worker ends.
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DEBUG EXCHANGE STARTED")
        RequestWrappers.debugExchange(context, repository, scope, "DEBUG")

        when (stateVarString) {
            "oneTimeDebugState" -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: ONE TIME DEBUG ENDED")
                WorkStates.setState(WorkType.ONE_TIME_DEBUG, WorkInfo.State.SUCCEEDED)
            }
            "periodicDebugState" -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: PERIODIC DEBUG ENDED")
                WorkStates.setState(WorkType.PERIODIC_DEBUG, WorkInfo.State.SUCCEEDED)
            }
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DEBUG WORKER: Worker state variable name is wrong")
            }
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DEBUG WORKER FOREGROUND")

        return ForegroundInfoCreator.createForegroundInfo(
            applicationContext = applicationContext,
            notificationID = NOTIFICATION_ID!!,
            channelID = CHANNEL_ID,
            contextText = "TeleFender updating..."
        )
    }
}