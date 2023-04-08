package com.telefender.phone.data.tele_database.background_tasks.workers

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.server_related.DataRequests
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

object TokenScheduler{

    val tokenOneTag = "oneTimeTokenWorker"
    val tokenPeriodTag = "periodicTokenWorker"

    fun initiateOneTimeTokenWorker(context : Context) : UUID {
        WorkStates.setState(
            workType = WorkType.ONE_TIME_TOKEN,
            workState = WorkInfo.State.RUNNING,
            context = context,
            tag = tokenOneTag
        )

        val tokenRequest = OneTimeWorkRequestBuilder<CoroutineTokenWorker>()
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(tokenOneTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(tokenOneTag, ExistingWorkPolicy.KEEP, tokenRequest)

        return tokenRequest.id
    }

    fun initiatePeriodicTokenWorker(context : Context) : UUID {
        WorkStates.setState(
            workType = WorkType.PERIODIC_TOKEN,
            workState = WorkInfo.State.RUNNING,
            context = context,
            tag = tokenPeriodTag
        )

        val syncRequest = PeriodicWorkRequestBuilder<CoroutineTokenWorker>(1, TimeUnit.DAYS)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .setInitialDelay(10, TimeUnit.SECONDS)
            .addTag(tokenPeriodTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(tokenPeriodTag, ExistingPeriodicWorkPolicy.KEEP, syncRequest)

        return syncRequest.id
    }
}

/**
 * TODO: When / If we do use this one day, don't forget to implement getForegroundInfo().
 */
class CoroutineTokenWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    var stateVarString: String? = null

    @SuppressLint("MissingPermission", "HardwareIds")
    
    override suspend fun doWork() : Result {

        when (stateVarString) {
            "oneTimeTokenState" ->  {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: TOKEN ONE TIME STARTED")
                // No need to set the state again for one time workers.
            }
            "periodicTokenState" -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: TOKEN PERIODIC STARTED")

                /**
                 * Although this may seem redundant, we need to set the state to running here,
                 * because if we call initiatePeriodicWorker() and the worker is in its
                 * enqueued state (interval down time), then the setState() called there will not
                 * set the state to RUNNING due to its safety measures. However, when the periodic
                 * worker DOES start, we still need to make sure the state accurately reflects the
                 * actually RUNNING state of the worker.
                 */
                WorkStates.setState(WorkType.PERIODIC_TOKEN, WorkInfo.State.RUNNING)
            }
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: TOKEN WORKER THREAD: Worker state variable name is wrong")
            }
        }

        val repository: ClientRepository = (applicationContext as App).repository
        val scope = (applicationContext as App).applicationScope

        val key = repository.getClientKey()
        val token = repository.getFireBaseToken()
        DataRequests.tokenPostRequest(context, repository, scope, token!!)

        when (stateVarString) {
            "oneTimeTokenState" ->  WorkStates.setState(WorkType.ONE_TIME_TOKEN, WorkInfo.State.SUCCEEDED)
            "periodicTokenState" -> WorkStates.setState(WorkType.PERIODIC_TOKEN, WorkInfo.State.SUCCEEDED)
            else -> {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: TOKEN WORKER THREAD: Worker state variable name is wrong")
            }
        }
        return Result.success()
    }
}