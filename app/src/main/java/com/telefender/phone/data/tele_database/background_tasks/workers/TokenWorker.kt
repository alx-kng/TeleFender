package com.telefender.phone.data.tele_database.background_tasks.workers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.server_related.ServerHelpers
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkerStates
import com.telefender.phone.data.tele_database.background_tasks.WorkerType
import com.telefender.phone.helpers.MiscHelpers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

object TokenScheduler{

    val tokenOneTag = "oneTimeTokenWorker"
    val tokenPeriodTag = "periodicTokenWorker"

    fun initiateOneTimeTokenWorker(context : Context) : UUID {
        WorkerStates.setState(WorkerType.ONE_TIME_TOKEN, WorkInfo.State.RUNNING)

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

    //TODO for all workers with work that can wait a bit, maybe we set constraints so it begins when charging/full battery
    fun initiatePeriodicTokenWorker(context : Context) : UUID {
        WorkerStates.setState(WorkerType.PERIODIC_TOKEN, WorkInfo.State.RUNNING)

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

class CoroutineTokenWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    var stateVarString: String? = null

    @SuppressLint("MissingPermission", "HardwareIds")
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork() : Result {

        val repository: ClientRepository = (applicationContext as App).repository
        val scope = (applicationContext as App).applicationScope

        val instanceNumber = MiscHelpers.getInstanceNumber(context)!!

        val key = repository.getClientKey(instanceNumber)
        val token = repository.getFireBaseToken(instanceNumber)
        ServerHelpers.tokenPostRequest(context, repository, scope, token!!)

        when (stateVarString) {
            "oneTimeTokenState" ->  WorkerStates.setState(WorkerType.ONE_TIME_TOKEN, WorkInfo.State.SUCCEEDED)
            "periodicTokenState" -> WorkerStates.setState(WorkerType.PERIODIC_TOKEN, WorkInfo.State.SUCCEEDED)
            else -> {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: TOKEN WORKER THREAD: Worker state variable name is wrong")
            }
        }
        return Result.success()
    }
}