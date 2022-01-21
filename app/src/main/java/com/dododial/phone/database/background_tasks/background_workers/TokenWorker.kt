package com.dododial.phone.database.background_tasks.background_workers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.work.*
import com.dododial.phone.App
import com.dododial.phone.database.ClientDatabase
import com.dododial.phone.database.ClientRepository
import com.dododial.phone.database.MiscHelpers
import com.dododial.phone.database.background_tasks.WorkerStates
import com.dododial.phone.database.background_tasks.server_related.ServerHelpers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

object TokenScheduler{

    val tokenOneTag = "oneTimeTokenWorker"
    val tokenPeriodTag = "periodicTokenWorker"

    fun initiateOneTimeTokenWorker(context : Context) : UUID {
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

    fun initiatePeriodicTokenWorker(context : Context) : UUID { //TODO for all workers with work that can wait a bit, maybe we set constraints so it begins when charging/full battery
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
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    var stateVarString: String? = null
    val context: Context = context

    @SuppressLint("MissingPermission", "HardwareIds")
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork() : Result {

        val repository: ClientRepository = (applicationContext as App).repository
        val database : ClientDatabase = (applicationContext as App).database
        val scope = (applicationContext as App).applicationScope

        val tMgr = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val instanceNumber : String = MiscHelpers.cleanNumber(tMgr.line1Number)!!

        val key = repository.getClientKey(instanceNumber)
        val token = repository.getFireBaseToken(instanceNumber)
        ServerHelpers.tokenPostRequest(context, repository, scope, token!!)

        when (stateVarString) {
            "oneTimeTokenState" ->  WorkerStates.oneTimeTokenState = WorkInfo.State.SUCCEEDED
            "periodicTokenState" -> WorkerStates.periodicTokenState = WorkInfo.State.SUCCEEDED
            else -> {
                Timber.i("DODODEBUG: TOKEN WORKER THREAD: Worker state variable name is wrong")
            }
        }
        return Result.success()
    }
}