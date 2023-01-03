package com.telefender.phone.data.tele_database.background_tasks

import androidx.work.WorkInfo
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport

enum class WorkerType {
    ONE_TIME_EXEC,
    PERIODIC_EXEC,
    ONE_TIME_UPLOAD,
    PERIODIC_UPLOAD,
    ONE_TIME_DOWNLOAD,
    PERIODIC_DOWNLOAD,
    ONE_TIME_SYNC,
    PERIODIC_SYNC,
    CATCH_SYNC,
    ONE_TIME_OMEGA,
    PERIODIC_OMEGA,
    SETUP,
    DOWNLOAD_POST,
    UPLOAD_POST,
    ONE_TIME_TOKEN,
    PERIODIC_TOKEN,
    UPLOAD_TOKEN
}

// TODO Maybe convert to promises or jobs OR MAYBE NOT.
object WorkerStates {

    private val propertyChangeSupport = PropertyChangeSupport(this)

    /**
     * As of now, any workers should / can only use the following states in their control flow.
     */
    private val allowedStates = listOf(
        WorkInfo.State.RUNNING,
        WorkInfo.State.FAILED,
        WorkInfo.State.SUCCEEDED
    )

    private var oneTimeExecState : WorkInfo.State? = null
    private var periodicExecState : WorkInfo.State? = null

    private var oneTimeUploadState : WorkInfo.State? = null
    private var periodicUploadState : WorkInfo.State? = null

    private var oneTimeDownloadState : WorkInfo.State? = null
    private var periodicDownloadState : WorkInfo.State? = null

    private var oneTimeSyncState : WorkInfo.State? = null
    private var periodicSyncState : WorkInfo.State? = null
    private var catchSyncState : WorkInfo.State? = null

    private var oneTimeOmegaState : WorkInfo.State? = null
    private var periodicOmegaState : WorkInfo.State? = null

    private var setupState : WorkInfo.State? = null
    private var downloadPostState : WorkInfo.State? = null
    private var uploadPostState : WorkInfo.State? = null

    private var oneTimeTokenState : WorkInfo.State? = null
    private var periodicTokenState : WorkInfo.State? = null
    private var uploadTokenState : WorkInfo.State? = null

    /**
     * For now, we'll stick to using one mutex, since only one worker should really be working at
     * a time.
     */
    private val mutex = Mutex()

    /**
     * Waiter counters used in workerWaiter() to decide what state to set upon finishing.
     */
    private var numOneTimeExecWaiters = 0
    private var numPeriodicExecWaiters = 0

    private var numOneTimeUploadWaiters = 0
    private var numPeriodicUploadWaiters = 0

    private var numOneTimeDownloadWaiters = 0
    private var numPeriodicDownloadWaiters = 0

    private var numOneTimeSyncWaiters = 0
    private var numPeriodicSyncWaiters = 0
    private var numSyncCatchWaiters = 0

    private var numOneTimeOmegaWaiters = 0
    private var numPeriodicOmegaWaiters = 0

    private var numSetupWaiters = 0
    private var numDownloadPostWaiters = 0
    private var numUploadPostWaiters = 0

    private var numOneTimeTokenWaiters = 0
    private var numPeriodicTokenWaiters = 0
    private var numUploadTokenWaiters = 0

    /***********************************************************************************************
     * TODO: Clean up edge cases. Ex) what if worker hasn't started yet before waiter???
     *  ?? Fix inside workers initialize functions. Also, Polish up system for multiple
     *  waiters of one type.
     *
     * Worker wait function that only return when the corresponding worker is finished. Acts as a
     * roadblock of sorts so that the code following the waiter isn't run until corresponding
     * worker finishes. Returns whether or not the worker succeeded.
     *
     * Requires that worker is running, otherwise the waiter returns (in order to
     * prevent infinite loop). Also, it's not really meant to be used for periodic workers,
     * although no large error will arise from such a usage.
     **********************************************************************************************/
    suspend fun workerWaiter(
        workerType: WorkerType,
        runningMsg: String? = null,
        stopOnFail: Boolean = false,
        certainFinish: Boolean = false
    ) : Boolean {
        var state = getState(workerType)

        if (state !in allowedStates) {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: ${workerType.name} waiter called when worker is not running.")
            setState(workerType, null)
            return false
        }

        mutex.withLock { changeNumWaiters(workerType, 1) }

        state = getState(workerType)
        while(state != WorkInfo.State.SUCCEEDED && state != null) {
            if (state == WorkInfo.State.FAILED && stopOnFail) {
                break
            }
            if (runningMsg != null) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: $runningMsg: %s", state.toString())
            }
            delay(500)
            state = getState(workerType)
        }

        /**
         * Basically, you want to clean state in the following cases:
         * 1: if success, then wait for last waiter to clean state
         * 2: if failure but worker is fully stopped (given by [certainFinish]),
         *      then stop all waiters by cleaning state (prevents waiters who don't stop on fail
         *      from running indefinitely.
         */
        val success = state == WorkInfo.State.SUCCEEDED
        mutex.withLock { changeNumWaiters(workerType, -1) }
        if (getNumWaiters(workerType) == 0 || (!success && certainFinish)) {
            setState(workerType, null)
        }
        return success
    }

    fun setState(workerType: WorkerType, workState: WorkInfo.State?) {
        if (workState !in allowedStates && workState != null) {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: setState() - invalid workState")
            return
        }

        when(workerType) {
            WorkerType.ONE_TIME_EXEC -> oneTimeExecState = workState
            WorkerType.PERIODIC_EXEC -> periodicExecState = workState
            WorkerType.ONE_TIME_UPLOAD -> oneTimeUploadState = workState
            WorkerType.PERIODIC_UPLOAD -> periodicUploadState = workState
            WorkerType.ONE_TIME_DOWNLOAD -> oneTimeDownloadState = workState
            WorkerType.PERIODIC_DOWNLOAD -> periodicDownloadState = workState
            WorkerType.ONE_TIME_SYNC -> oneTimeSyncState = workState
            WorkerType.PERIODIC_SYNC -> periodicSyncState = workState
            WorkerType.CATCH_SYNC -> catchSyncState = workState
            WorkerType.ONE_TIME_OMEGA -> oneTimeOmegaState = workState
            WorkerType.PERIODIC_OMEGA -> periodicOmegaState = workState
            WorkerType.SETUP -> setupState = workState
            WorkerType.DOWNLOAD_POST -> downloadPostState = workState
            WorkerType.UPLOAD_POST -> uploadPostState = workState
            WorkerType.ONE_TIME_TOKEN -> oneTimeTokenState = workState
            WorkerType.PERIODIC_TOKEN -> periodicTokenState = workState
            WorkerType.UPLOAD_TOKEN -> uploadTokenState = workState
        }

        /**
         * Notifies property listeners. oldValue is workState while newValue is workerType since
         * propertyChangeSupport only notifies workers if it sees a change in oldValue and newValue.
         *
         * Technically supposed to pass in old and new value of actual workerState. However, since
         * we have so many worker states, we just pass in the workerState and workerType in place
         * of those values.
         */
        this.propertyChangeSupport.firePropertyChange("setState", workState, workerType)
    }

    fun addPropertyChangeListener(pcl: PropertyChangeListener?) {
        propertyChangeSupport.addPropertyChangeListener(pcl)
    }

    fun removePropertyChangeListener(pcl: PropertyChangeListener?) {
        propertyChangeSupport.removePropertyChangeListener(pcl)
    }

    fun getState(workerType: WorkerType) : WorkInfo.State? {
        return when(workerType) {
            WorkerType.ONE_TIME_EXEC -> oneTimeExecState
            WorkerType.PERIODIC_EXEC -> periodicExecState
            WorkerType.ONE_TIME_UPLOAD -> oneTimeUploadState
            WorkerType.PERIODIC_UPLOAD -> periodicUploadState
            WorkerType.ONE_TIME_DOWNLOAD -> oneTimeDownloadState
            WorkerType.PERIODIC_DOWNLOAD -> periodicDownloadState
            WorkerType.ONE_TIME_SYNC -> oneTimeSyncState
            WorkerType.PERIODIC_SYNC -> periodicSyncState
            WorkerType.CATCH_SYNC -> catchSyncState
            WorkerType.ONE_TIME_OMEGA -> oneTimeOmegaState
            WorkerType.PERIODIC_OMEGA -> periodicOmegaState
            WorkerType.SETUP -> setupState
            WorkerType.DOWNLOAD_POST -> downloadPostState
            WorkerType.UPLOAD_POST -> uploadPostState
            WorkerType.ONE_TIME_TOKEN -> oneTimeTokenState
            WorkerType.PERIODIC_TOKEN -> periodicTokenState
            WorkerType.UPLOAD_TOKEN -> uploadTokenState
        }
    }

    private fun changeNumWaiters(workerType: WorkerType, changeAmount: Int) {
        when(workerType) {
            WorkerType.ONE_TIME_EXEC -> numOneTimeExecWaiters += changeAmount
            WorkerType.PERIODIC_EXEC -> numPeriodicExecWaiters += changeAmount
            WorkerType.ONE_TIME_UPLOAD -> numOneTimeUploadWaiters += changeAmount
            WorkerType.PERIODIC_UPLOAD -> numPeriodicUploadWaiters += changeAmount
            WorkerType.ONE_TIME_DOWNLOAD -> numOneTimeDownloadWaiters += changeAmount
            WorkerType.PERIODIC_DOWNLOAD -> numPeriodicDownloadWaiters += changeAmount
            WorkerType.ONE_TIME_SYNC -> numOneTimeSyncWaiters += changeAmount
            WorkerType.PERIODIC_SYNC -> numPeriodicSyncWaiters += changeAmount
            WorkerType.CATCH_SYNC -> numSyncCatchWaiters += changeAmount
            WorkerType.ONE_TIME_OMEGA -> numOneTimeOmegaWaiters += changeAmount
            WorkerType.PERIODIC_OMEGA -> numPeriodicOmegaWaiters += changeAmount
            WorkerType.SETUP -> numSetupWaiters += changeAmount
            WorkerType.DOWNLOAD_POST -> numDownloadPostWaiters += changeAmount
            WorkerType.UPLOAD_POST -> numUploadPostWaiters += changeAmount
            WorkerType.ONE_TIME_TOKEN -> numOneTimeTokenWaiters += changeAmount
            WorkerType.PERIODIC_TOKEN -> numPeriodicTokenWaiters += changeAmount
            WorkerType.UPLOAD_TOKEN -> numUploadTokenWaiters += changeAmount
        }
    }

    private fun getNumWaiters(workerType: WorkerType) : Int {
        return when(workerType) {
            WorkerType.ONE_TIME_EXEC -> numOneTimeExecWaiters
            WorkerType.PERIODIC_EXEC -> numPeriodicExecWaiters
            WorkerType.ONE_TIME_UPLOAD -> numOneTimeUploadWaiters
            WorkerType.PERIODIC_UPLOAD -> numPeriodicUploadWaiters
            WorkerType.ONE_TIME_DOWNLOAD -> numOneTimeDownloadWaiters
            WorkerType.PERIODIC_DOWNLOAD -> numPeriodicDownloadWaiters
            WorkerType.ONE_TIME_SYNC -> numOneTimeSyncWaiters
            WorkerType.PERIODIC_SYNC -> numPeriodicSyncWaiters
            WorkerType.CATCH_SYNC -> numSyncCatchWaiters
            WorkerType.ONE_TIME_OMEGA -> numOneTimeOmegaWaiters
            WorkerType.PERIODIC_OMEGA -> numPeriodicOmegaWaiters
            WorkerType.SETUP -> numSetupWaiters
            WorkerType.DOWNLOAD_POST -> numDownloadPostWaiters
            WorkerType.UPLOAD_POST -> numUploadPostWaiters
            WorkerType.ONE_TIME_TOKEN -> numOneTimeTokenWaiters
            WorkerType.PERIODIC_TOKEN -> numPeriodicTokenWaiters
            WorkerType.UPLOAD_TOKEN -> numUploadTokenWaiters
        }
    }
}