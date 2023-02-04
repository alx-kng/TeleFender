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

    ONE_TIME_TOKEN,
    PERIODIC_TOKEN,

    SETUP,
    DOWNLOAD_POST,
    UPLOAD_CHANGE_POST,
    UPLOAD_ANALYZED_POST,
    UPLOAD_LOG_POST,
    UPLOAD_ERROR_POST,
    UPLOAD_TOKEN,
    SMS_VERIFY_POST,
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

    /**
     * Work states
     */
    private val states: Array<WorkInfo.State?> = Array(WorkType.values().size) {null}

    /**
     * Waiter counters used in workerWaiter() to decide what state to set upon finishing.
     */
    private val waiters: Array<Int> = Array(WorkType.values().size) {0}

    /**
     * For now, we'll stick to using one mutex, since only one worker should really be working at
     * a time.
     */
    private val mutex = Mutex()

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

        states[workType.ordinal] = workState

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
        return states[workType.ordinal]
    }

    private fun changeNumWaiters(workType: WorkType, changeAmount: Int) {
        waiters[workType.ordinal] += changeAmount
    }

    private fun getNumWaiters(workType: WorkType) : Int {
        return waiters[workType.ordinal]
    }
}