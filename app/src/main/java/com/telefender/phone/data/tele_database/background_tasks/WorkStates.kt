package com.telefender.phone.data.tele_database.background_tasks

import androidx.work.WorkInfo
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport

enum class WorkType {
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
    UPLOAD_CHANGE_POST,
    UPLOAD_ANALYZED_POST,
    UPLOAD_LOG_POST,
    ONE_TIME_TOKEN,
    PERIODIC_TOKEN,
    UPLOAD_TOKEN
}

// TODO Maybe convert to promises or jobs OR MAYBE NOT.
object WorkStates {

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
    private var uploadChangeState : WorkInfo.State? = null
    private var uploadAnalyzedState : WorkInfo.State? = null
    private var uploadLogState : WorkInfo.State? = null

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
    private var numUploadChangeWaiters = 0
    private var numUploadAnalyzedWaiters = 0
    private var numUploadLogWaiters = 0

    private var numOneTimeTokenWaiters = 0
    private var numPeriodicTokenWaiters = 0
    private var numUploadTokenWaiters = 0

    /***********************************************************************************************
     * TODO: Clean up edge cases. Ex) what if worker hasn't started yet before waiter???
     *  ?? Fix inside workers initialize functions. Also, Polish up system for multiple
     *  waiters of one type.
     *
     * Work wait function that only return when the corresponding work is finished. Acts as a
     * roadblock of sorts so that the code following the waiter isn't run until corresponding
     * work finishes. Returns whether or not the work succeeded.
     *
     * Requires that worker is running, otherwise the waiter returns (in order to
     * prevent infinite loop). Also, it's not really meant to be used for periodic workers,
     * although no large error will arise from such a usage.
     **********************************************************************************************/
    suspend fun workWaiter(
        workType: WorkType,
        runningMsg: String? = null,
        stopOnFail: Boolean = false,
        certainFinish: Boolean = false
    ) : Boolean {
        var state = getState(workType)

        if (state !in allowedStates) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: ${workType.name} waiter called when worker is not running.")
            setState(workType, null)
            return false
        }

        mutex.withLock { changeNumWaiters(workType, 1) }

        state = getState(workType)
        while(state != WorkInfo.State.SUCCEEDED && state != null) {
            if (state == WorkInfo.State.FAILED && stopOnFail) {
                break
            }
            if (runningMsg != null) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: $runningMsg: %s", state.toString())
            }
            delay(500)
            state = getState(workType)
        }

        /**
         * Basically, you want to clean state in the following cases:
         * 1: if success, then wait for last waiter to clean state
         * 2: if failure but worker is fully stopped (given by [certainFinish]),
         *      then stop all waiters by cleaning state (prevents waiters who don't stop on fail
         *      from running indefinitely.
         */
        val success = state == WorkInfo.State.SUCCEEDED
        mutex.withLock { changeNumWaiters(workType, -1) }
        if (getNumWaiters(workType) == 0 || (!success && certainFinish)) {
            setState(workType, null)
        }
        return success
    }

    fun setState(workType: WorkType, workState: WorkInfo.State?) {
        if (workState !in allowedStates && workState != null) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: setState() - invalid workState")
            return
        }

        when(workType) {
            WorkType.ONE_TIME_EXEC -> oneTimeExecState = workState
            WorkType.PERIODIC_EXEC -> periodicExecState = workState
            WorkType.ONE_TIME_UPLOAD -> oneTimeUploadState = workState
            WorkType.PERIODIC_UPLOAD -> periodicUploadState = workState
            WorkType.ONE_TIME_DOWNLOAD -> oneTimeDownloadState = workState
            WorkType.PERIODIC_DOWNLOAD -> periodicDownloadState = workState
            WorkType.ONE_TIME_SYNC -> oneTimeSyncState = workState
            WorkType.PERIODIC_SYNC -> periodicSyncState = workState
            WorkType.CATCH_SYNC -> catchSyncState = workState
            WorkType.ONE_TIME_OMEGA -> oneTimeOmegaState = workState
            WorkType.PERIODIC_OMEGA -> periodicOmegaState = workState
            WorkType.SETUP -> setupState = workState
            WorkType.DOWNLOAD_POST -> downloadPostState = workState
            WorkType.UPLOAD_CHANGE_POST -> uploadChangeState = workState
            WorkType.UPLOAD_ANALYZED_POST -> uploadAnalyzedState = workState
            WorkType.UPLOAD_LOG_POST -> uploadLogState = workState
            WorkType.ONE_TIME_TOKEN -> oneTimeTokenState = workState
            WorkType.PERIODIC_TOKEN -> periodicTokenState = workState
            WorkType.UPLOAD_TOKEN -> uploadTokenState = workState
        }

        /**
         * Notifies property listeners. oldValue is workState while newValue is workType since
         * propertyChangeSupport only notifies workers if it sees a change in oldValue and newValue.
         *
         * Technically supposed to pass in old and new value of actual workerState. However, since
         * we have so many worker states, we just pass in the workerState and workType in place
         * of those values.
         */
        this.propertyChangeSupport.firePropertyChange("setState", workState, workType)
    }

    fun addPropertyChangeListener(pcl: PropertyChangeListener?) {
        propertyChangeSupport.addPropertyChangeListener(pcl)
    }

    fun removePropertyChangeListener(pcl: PropertyChangeListener?) {
        propertyChangeSupport.removePropertyChangeListener(pcl)
    }

    fun getState(workType: WorkType) : WorkInfo.State? {
        return when(workType) {
            WorkType.ONE_TIME_EXEC -> oneTimeExecState
            WorkType.PERIODIC_EXEC -> periodicExecState
            WorkType.ONE_TIME_UPLOAD -> oneTimeUploadState
            WorkType.PERIODIC_UPLOAD -> periodicUploadState
            WorkType.ONE_TIME_DOWNLOAD -> oneTimeDownloadState
            WorkType.PERIODIC_DOWNLOAD -> periodicDownloadState
            WorkType.ONE_TIME_SYNC -> oneTimeSyncState
            WorkType.PERIODIC_SYNC -> periodicSyncState
            WorkType.CATCH_SYNC -> catchSyncState
            WorkType.ONE_TIME_OMEGA -> oneTimeOmegaState
            WorkType.PERIODIC_OMEGA -> periodicOmegaState
            WorkType.SETUP -> setupState
            WorkType.DOWNLOAD_POST -> downloadPostState
            WorkType.UPLOAD_CHANGE_POST -> uploadChangeState
            WorkType.UPLOAD_ANALYZED_POST -> uploadAnalyzedState
            WorkType.UPLOAD_LOG_POST -> uploadLogState
            WorkType.ONE_TIME_TOKEN -> oneTimeTokenState
            WorkType.PERIODIC_TOKEN -> periodicTokenState
            WorkType.UPLOAD_TOKEN -> uploadTokenState
        }
    }

    private fun changeNumWaiters(workType: WorkType, changeAmount: Int) {
        when(workType) {
            WorkType.ONE_TIME_EXEC -> numOneTimeExecWaiters += changeAmount
            WorkType.PERIODIC_EXEC -> numPeriodicExecWaiters += changeAmount
            WorkType.ONE_TIME_UPLOAD -> numOneTimeUploadWaiters += changeAmount
            WorkType.PERIODIC_UPLOAD -> numPeriodicUploadWaiters += changeAmount
            WorkType.ONE_TIME_DOWNLOAD -> numOneTimeDownloadWaiters += changeAmount
            WorkType.PERIODIC_DOWNLOAD -> numPeriodicDownloadWaiters += changeAmount
            WorkType.ONE_TIME_SYNC -> numOneTimeSyncWaiters += changeAmount
            WorkType.PERIODIC_SYNC -> numPeriodicSyncWaiters += changeAmount
            WorkType.CATCH_SYNC -> numSyncCatchWaiters += changeAmount
            WorkType.ONE_TIME_OMEGA -> numOneTimeOmegaWaiters += changeAmount
            WorkType.PERIODIC_OMEGA -> numPeriodicOmegaWaiters += changeAmount
            WorkType.SETUP -> numSetupWaiters += changeAmount
            WorkType.DOWNLOAD_POST -> numDownloadPostWaiters += changeAmount
            WorkType.UPLOAD_CHANGE_POST -> numUploadChangeWaiters += changeAmount
            WorkType.UPLOAD_ANALYZED_POST -> numUploadAnalyzedWaiters += changeAmount
            WorkType.UPLOAD_LOG_POST -> numUploadLogWaiters += changeAmount
            WorkType.ONE_TIME_TOKEN -> numOneTimeTokenWaiters += changeAmount
            WorkType.PERIODIC_TOKEN -> numPeriodicTokenWaiters += changeAmount
            WorkType.UPLOAD_TOKEN -> numUploadTokenWaiters += changeAmount
        }
    }

    private fun getNumWaiters(workType: WorkType) : Int {
        return when(workType) {
            WorkType.ONE_TIME_EXEC -> numOneTimeExecWaiters
            WorkType.PERIODIC_EXEC -> numPeriodicExecWaiters
            WorkType.ONE_TIME_UPLOAD -> numOneTimeUploadWaiters
            WorkType.PERIODIC_UPLOAD -> numPeriodicUploadWaiters
            WorkType.ONE_TIME_DOWNLOAD -> numOneTimeDownloadWaiters
            WorkType.PERIODIC_DOWNLOAD -> numPeriodicDownloadWaiters
            WorkType.ONE_TIME_SYNC -> numOneTimeSyncWaiters
            WorkType.PERIODIC_SYNC -> numPeriodicSyncWaiters
            WorkType.CATCH_SYNC -> numSyncCatchWaiters
            WorkType.ONE_TIME_OMEGA -> numOneTimeOmegaWaiters
            WorkType.PERIODIC_OMEGA -> numPeriodicOmegaWaiters
            WorkType.SETUP -> numSetupWaiters
            WorkType.DOWNLOAD_POST -> numDownloadPostWaiters
            WorkType.UPLOAD_CHANGE_POST -> numUploadChangeWaiters
            WorkType.UPLOAD_ANALYZED_POST -> numUploadAnalyzedWaiters
            WorkType.UPLOAD_LOG_POST -> numUploadLogWaiters
            WorkType.ONE_TIME_TOKEN -> numOneTimeTokenWaiters
            WorkType.PERIODIC_TOKEN -> numPeriodicTokenWaiters
            WorkType.UPLOAD_TOKEN -> numUploadTokenWaiters
        }
    }
}