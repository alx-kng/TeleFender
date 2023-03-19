package com.telefender.phone.data.tele_database.background_tasks.workers

import android.content.Context
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.server_related.RequestWrappers
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * TODO: Enforce Application Context
 *
 * All workers NEED TO USE application context, or else the context will probably be null.
 */
object SMSVerifyScheduler{

    val smsVerifyTag = "oneTimeSMSVerifyWorker"

    fun initiateSMSVerifyWorker(context : Context, number: String) : UUID? {
        WorkStates.setState(WorkType.ONE_TIME_SMS_VERIFY, WorkInfo.State.RUNNING)

        val smsVerifyRequest = OneTimeWorkRequestBuilder<CoroutineSMSVerifyWorker>()
            .setInputData(workDataOf("notificationID" to "7171", "number" to number))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(smsVerifyTag)
            .build()

        // ExistingWorkPolicy set to REPLACE so that sync observing period starts fresh after call.
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(smsVerifyTag, ExistingWorkPolicy.REPLACE, smsVerifyRequest)

        return smsVerifyRequest.id
    }
}

/**
 * TODO: If ParametersWrapper is null, we are just falling back on 60 seconds. Should we do this or
 *  should we just fail out?
 */
class CoroutineSMSVerifyWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private var NOTIFICATION_ID : Int? = -1
    private val CHANNEL_ID = "alxkng5737"

    val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun doWork() : Result {
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        try {
            setForeground(getForegroundInfo())
        } catch(e: Exception) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: %s", e.message)
        }

        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SMS VERIFY WORKER STARTED")

        val repository = (applicationContext as App).repository
        val number = inputData.getString("number")!!
        val analyzed = repository.getAnalyzedNum(number)?.getAnalyzed()
        val waitTime = repository.getParameters()?.smsDeferredWaitTime ?: 60

        /*
        Wait around a minute and send another SMS verify request if the number isn't already
        verified by then (for server load optimization).
         */
        delay(waitTime * 1000L)
        if (analyzed?.smsVerified == false) {
            RequestWrappers.smsVerify(context, repository, scope, number)
        }

        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SMS VERIFY WORKER ENDED")

        WorkStates.setState(WorkType.ONE_TIME_SMS_VERIFY, WorkInfo.State.SUCCEEDED)
        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SMS VERIFY WORKER FOREGROUND")

        return ForegroundInfoCreator.createForegroundInfo(
            applicationContext = applicationContext,
            notificationID = NOTIFICATION_ID!!,
            channelID = CHANNEL_ID,
            contextText = "Verifying Calls..."
        )
    }
}