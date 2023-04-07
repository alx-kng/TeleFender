package com.telefender.phone.data.tele_database.background_tasks.workers

import android.content.Context
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.server_related.UserSetup
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
import com.telefender.phone.permissions.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

object SetupScheduler {

    val setupTag = "setupWorker"

    fun initiateSetupWorker(context: Context): UUID? {
        if (!Permissions.hasLogPermissions(context)) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: No log permissions in initiateSetupWorker()")
            return null
        }

        /*
        Probably no need to check that worker is running, as initiateSetupWorker already has quite
        a few state checks in ClientDatabase.
         */
        WorkStates.setState(WorkType.SETUP, WorkInfo.State.RUNNING)

        val setupRequest = OneTimeWorkRequestBuilder<CoroutineSetupWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(setupTag)
            .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(setupTag, ExistingWorkPolicy.KEEP, setupRequest)

        return setupRequest.id
    }
}

class CoroutineSetupWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    var NOTIFICATION_ID = 4324
    val CHANNEL_ID = "alxkng5737"
    val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun doWork() : Result {
        /*
        This may seem like a repeat to the one in initiateSetupWorker(), but it's actually used
        when the worker retries. Additionally, we don't need to do the worker check for setState()
        since we already know this is a valid worker.
         */
        WorkStates.setState(WorkType.SETUP, WorkInfo.State.RUNNING)

        try {
            setForeground(getForegroundInfo())
        } catch(e: Exception) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: %s", e.message!!)
        }

        val repository : ClientRepository = (applicationContext as App).repository

        // Stops worker if user is already setup.
        if (repository.hasClientKey()) {
            return Result.success()
        }

        UserSetup.initialPostRequest(context, repository, scope)

        if(!WorkStates.workWaiter(WorkType.SETUP, "SETUP WORKER", stopOnFail = true)) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: SETUP WORKER RETRYING...")
            return Result.retry()
        }

        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SETUP WORKER DONE")
        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SETUP WORKER FOREGROUND")

        return ForegroundInfoCreator.createForegroundInfo(
            applicationContext = applicationContext,
            notificationID = NOTIFICATION_ID,
            channelID = CHANNEL_ID,
            contextText = "Connecting to Server..."
        )
    }
}