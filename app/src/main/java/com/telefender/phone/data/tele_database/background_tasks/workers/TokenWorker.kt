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
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
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
                PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
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