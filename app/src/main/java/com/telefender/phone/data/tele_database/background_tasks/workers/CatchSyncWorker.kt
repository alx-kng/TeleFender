package com.telefender.phone.data.tele_database.background_tasks.workers

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.work.*
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.TeleCallDetails
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * TODO: Enforce Application Context
 *
 * All workers NEED TO USE application context, or else the context will probably be null.
 */
object CatchSyncScheduler{

    val syncCatchTag = "catchSyncWorker"

    fun initiateCatchSyncWorker(context : Context) : UUID? {
        if (!TeleHelpers.hasValidStatus(context, logRequired = true)) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: Invalid status in initiateCatchSyncWorker()")
            return null
        }

        /*
        Don't check worker running here since the work is about to be replaced no matter what.
        That is, if we do the worker check here and the worker is already running, the state won't
        set, even though we're about to replace the worker. It's not actually a big deal either way,
        but this seems to be a little safer.
         */
        WorkStates.setState(WorkType.CATCH_SYNC, WorkInfo.State.RUNNING)

        val syncRequest = OneTimeWorkRequestBuilder<CoroutineCatchSyncWorker>()
            .setInputData(workDataOf("notificationID" to "5556"))
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .addTag(syncCatchTag)
            .build()

        // ExistingWorkPolicy set to REPLACE so that sync observing period starts fresh after call.
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(syncCatchTag, ExistingWorkPolicy.REPLACE, syncRequest)

        return syncRequest.id
    }
}

class CoroutineCatchSyncWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private var NOTIFICATION_ID : Int? = -1
    private val CHANNEL_ID = "alxkng5737"

    // TODO: Change sync period for observation.
    private val syncPeriod = 120000L
    private val numPrint = 20

    val scope = CoroutineScope(Dispatchers.IO)
    private var callLogObserverSync: CallLogObserverSync =
        CallLogObserverSync(Handler(Looper.getMainLooper()), context, scope)


    override suspend fun doWork() : Result {
        NOTIFICATION_ID = inputData.getString("notificationID")?.toInt()

        try {
            setForeground(getForegroundInfo())
        } catch(e: Exception) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: %s", e.message)
        }

        val syncObserver = SyncWorkerObserver(context, scope, callLogObserverSync)
        WorkStates.addPropertyChangeListener(syncObserver)

        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SYNC CATCHER OBSERVER STARTED")

        applicationContext.contentResolver.registerContentObserver(
            android.provider.CallLog.Calls.CONTENT_URI,
            false,
            callLogObserverSync
        )

        /**
         * This is more for actually immediately syncing call logs, as the call log observer in
         * CatchSyncWorker usually doesn't start in time to observe the call that was just removed.
         * This is because the worker usually isn't started immediately / in time.
         */
        val repository = (applicationContext as App).repository
        TeleCallDetails.syncCallImmediate(context, repository, scope)

        for (i in 1..numPrint) {
            delay(syncPeriod / numPrint)
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SYNC CATCHER OBSERVER RUNNING $i / $numPrint")
        }

        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SYNC CATCHER OBSERVER ENDED")

        WorkStates.removePropertyChangeListener(syncObserver)
        applicationContext.contentResolver.unregisterContentObserver(callLogObserverSync)

        // TODO: We set state to state to null here at one point. Maybe there was a reason?
        WorkStates.setState(WorkType.CATCH_SYNC, WorkInfo.State.SUCCEEDED)
        return Result.success()
    }

    override suspend fun getForegroundInfo() : ForegroundInfo {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: SYNC CATCHER OBSERVER WORKER FOREGROUND")

        return ForegroundInfoCreator.createForegroundInfo(
            applicationContext = applicationContext,
            notificationID = NOTIFICATION_ID!!,
            channelID = CHANNEL_ID,
            contextText = "Syncing Calls..."
        )
    }

    /**
     * TODO: Observer doesn't always catch the first call right after it ends. That is, SyncWorker
     *  doesn't always start in time to observe call. We get around this for now by doing a
     *  sync operations anyways, but see if there's a better way.
     *
     * TODO: Double check observer / cancellation logic.
     *
     * Observes default call logs for changes and syncs with Tele database.
     */
    class CallLogObserverSync(
        handler: Handler,
        val context: Context,
        val scope: CoroutineScope
    ) : ContentObserver(handler) {

        /*
        TODO: Remove id from CallLogObserverSync. Currently used to check if observer is correctly
         unregistered or not.
         */
        private var id = (0..10000).random()

        override fun deliverSelfNotifications(): Boolean {
            return false
        }

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: OBSERVED NEW CALL LOG - SYNC - ID = $id")

            val repository = ((context.applicationContext) as App).repository
            TeleCallDetails.syncCallImmediate(context.applicationContext, repository, scope)
        }
    }

    /**
     * Observes catchSyncState in WorkStates. On first change (can change if more complicated use
     * is needed later) it unregisters CallLogObserverSync object from observing default call logs
     * and unregisters itself as an observer of catchSyncState. Needed because CallLogObserverSync
     * doesn't properly unregister if CatchSyncWorker is restarted / replaced midway.
     */
    class SyncWorkerObserver(
        val context: Context,
        val scope: CoroutineScope,
        private val logObserver: CallLogObserverSync
    ): PropertyChangeListener {

        override fun propertyChange(p0: PropertyChangeEvent?) {
            if (p0?.newValue == WorkType.CATCH_SYNC) {
                scope.launch {
                    Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: CANCELLING SYNC CATCHER OBSERVER - Probably due to restart / end in worker.")

                    val contentResolver = context.applicationContext.contentResolver
                    contentResolver.unregisterContentObserver(logObserver)

                    // If we unregister logObserver, remove this sync observer from WorkStates.
                    WorkStates.removePropertyChangeListener(this@SyncWorkerObserver)
                }
            }
        }
    }
}
