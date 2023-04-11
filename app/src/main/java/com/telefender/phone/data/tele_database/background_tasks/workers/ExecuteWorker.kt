package com.telefender.phone.data.tele_database.background_tasks.workers

import android.content.Context
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.misc_helpers.TeleHelpers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

object ExecuteScheduler {

    const val execOneTag = "oneTimeExecuteWorker"
    const val execPeriodTag = "periodicExecuteWorker"
    
    fun initiateOneTimeExecuteWorker(context: Context) : UUID {
        WorkStates.setState(
            workType = WorkType.ONE_TIME_EXEC,
            workState = WorkInfo.State.RUNNING,
            context = context,
            tag = execOneTag
        )

        val executeRequest = OneTimeWorkRequestBuilder<CoroutineExecuteWorker>()
            .setInputData(workDataOf("variableName" to "oneTimeExecState", "notificationID" to "1111"))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(execOneTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(execOneTag, ExistingWorkPolicy.KEEP, executeRequest)

        return executeRequest.id
    }

    fun initiatePeriodicExecuteWorker(context : Context) : UUID {
        WorkStates.setState(
            workType = WorkType.PERIODIC_EXEC,
            workState = WorkInfo.State.RUNNING,
            context = context,
            tag = execPeriodTag
        )

        val executeRequest = PeriodicWorkRequestBuilder<CoroutineExecuteWorker>(1, TimeUnit.HOURS)
            .setInputData(workDataOf("variableName" to "periodicExecState", "notificationID" to "2222"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
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

    // TODO: Look into setting different WorkStates depending on result of execution.
    override suspend fun doWork(): Result {
        stateVarString = inputData.getString("variableName")
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: EXECUTE STARTED")

        try {
            setForeground(getForegroundInfo())
        } catch(e: Exception) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: %s", e.message!!)
        }

        when (stateVarString) {
            "oneTimeExecState" -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: EXECUTE ONE TIME STARTED")
                // No need to set the state again for one time workers.
            }
            "periodicExecState" -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: EXECUTE PERIODIC STARTED")

                /**
                 * Although this may seem redundant, we need to set the state to running here,
                 * because if we call initiatePeriodicWorker() and the worker is in its
                 * enqueued state (interval down time), then the setState() called there will not
                 * set the state to RUNNING due to its safety measures. However, when the periodic
                 * worker DOES start, we still need to make sure the state accurately reflects the
                 * actually RUNNING state of the worker.
                 */
                WorkStates.setState(WorkType.PERIODIC_EXEC, WorkInfo.State.RUNNING)
            }
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: EXECUTE WORKER THREAD: Worker state variable name is wrong")
            }
        }

        val repository: ClientRepository = (applicationContext as App).repository

        /**
         * Executes logs in ExecuteQueue
         */
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: EXECUTE WORKER STARTED")
        repository.executeAll()

        when (stateVarString) {
            "oneTimeExecState" -> WorkStates.setState(WorkType.ONE_TIME_EXEC, WorkInfo.State.SUCCEEDED)
            "periodicExecState" -> WorkStates.setState(WorkType.PERIODIC_EXEC, WorkInfo.State.SUCCEEDED)
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: EXECUTE WORKER THREAD: Worker state variable name is wrong")
            }
        }
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: EXECUTE ENDED")
        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: EXECUTE WORKER FOREGROUND")

        return ForegroundInfoCreator.createForegroundInfo(
            applicationContext = applicationContext,
            notificationID = NOTIFICATION_ID!!,
            channelID = CHANNEL_ID,
            contextText = "Executing..."
        )
    }
}
