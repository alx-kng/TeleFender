package com.telefender.phone.data.tele_database.background_tasks

import android.content.Context
import androidx.work.WorkInfo
import com.telefender.phone.data.tele_database.background_tasks.workers.WorkManagerHelper
import com.telefender.phone.misc_helpers.DBL
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport


enum class WorkType(val isWorker : Boolean = false) {
    // Workers
    ONE_TIME_EXEC(true),
    PERIODIC_EXEC(true),

    ONE_TIME_UPLOAD(true),
    PERIODIC_UPLOAD(true),

    ONE_TIME_DOWNLOAD(true),
    PERIODIC_DOWNLOAD(true),

    ONE_TIME_SYNC(true),
    PERIODIC_SYNC(true),
    CATCH_SYNC(true),

    ONE_TIME_OMEGA(true),
    PERIODIC_OMEGA(true),

    ONE_TIME_TOKEN(true),
    PERIODIC_TOKEN(true),

    ONE_TIME_SMS_VERIFY(true),

    ONE_TIME_DEBUG(true),
    PERIODIC_DEBUG(true),

    // Post requests
    SETUP,

    DOWNLOAD_POST,
    UPLOAD_CHANGE_POST,
    UPLOAD_ANALYZED_POST,
    UPLOAD_LOG_POST,
    UPLOAD_ERROR_POST,
    UPLOAD_TOKEN,
    SMS_VERIFY_POST,

    DEBUG_CHECK_POST,
    DEBUG_SESSION_POST,
    DEBUG_EXCHANGE_POST,
    DEBUG_CALL_STATE_POST
}


/**
 * TODO: Maybe convert to promises or jobs OR MAYBE NOT.
 *
 * TODO: Fix this to use WorkManagerHelper
 */
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
     * For now, we'll stick to using one mutex, since the number of waiters doesn't usually change
     * that quickly.
     */
    private val waiterMutex = Mutex()

    /**
     * Used by mutuallyExclusiveWork().
     */
    private val workMutex = Mutex()

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
            Timber.e("$DBL: ${workType.name} waiter called when worker is not running.")
            setState(workType, null)
            return false
        }

        waiterMutex.withLock { changeNumWaiters(workType, 1) }

        state = getState(workType)
        while(state != WorkInfo.State.SUCCEEDED && state != null) {
            if (state == WorkInfo.State.FAILED && stopOnFail) {
                break
            }
            if (runningMsg != null) {
                Timber.i("$DBL: $runningMsg: %s", state.toString())
            }
            delay(500)
            state = getState(workType)
        }

        /**
         * Basically, you want to clean state in the following cases:
         * 1: if success, then wait for last waiter to clean state
         * 2: if failure but worker is fully stopped (given by [certainFinish]),
         *      then stop all waiters by cleaning state (prevents waiters who don't stop on fail
         *      from running indefinitely).
         */
        val success = state == WorkInfo.State.SUCCEEDED
        waiterMutex.withLock { changeNumWaiters(workType, -1) }
        if (getNumWaiters(workType) == 0 || (!success && certainFinish)) {
            setState(workType, null)
        }
        return success
    }

    /**
     * Used if you want two different types of work to not run at the same time (e.g.,
     * ONE_TIME_DEBUG and PERIODIC_DEBUG). [originalWork] is the work that you think might already
     * running, and [newWork] is the work that you want to stop if [originalWork] is already running.
     * Returns true if [originalWork] is already running.
     */
    suspend fun mutuallyExclusiveWork(originalWork: WorkType, newWork: WorkType) : Boolean {
        /*
        Mutex prevents the following scenario. Say you have one-time and periodic workers that
        should not run at the same time (e.g., debug workers). Their states are both set to RUNNING
        before the workers actually begin. In the very rare case that both make it past the "if"
        statement (since both see that the other is running), both workers will be canceled. By
        locking the code, we can make sure that the states are accurate when checked.
         */
        workMutex.withLock {
            Timber.e("$DBL: mutuallyExclusiveWork() - %s | %s",
                "originalWork = $originalWork, state = ${getState(originalWork)}",
                "newWork = $newWork, state = ${getState(newWork)}")

            if (getState(originalWork) == WorkInfo.State.RUNNING) {
                Timber.i("$DBL: $newWork ENDED - $originalWork RUNNING")
                setState(newWork, WorkInfo.State.SUCCEEDED)
                return true
            }

            return false
        }
    }

    /**
     * TODO: Seems to be some issues here.
     *
     * Sets the state of a certain WorkType.
     */
    fun setState(
        workType: WorkType,
        workState: WorkInfo.State?,
        context: Context? = null,
        tag: String? = null
    ) {
        if (workState !in allowedStates && workState != null) {
            Timber.e("$DBL: setState() for $workType - $workState is an invalid workState")
            return
        }

        /*
        Safety measure to prevent setting WorkState to running if Worker is already running or
        enqueued. Although it may seem redundant, it actually prevents waiters from waiting for
        work that isn't technically running (e.g., periodic worker during repeat interval).
         */
        if (workType.isWorker
            && workState == WorkInfo.State.RUNNING
            && context != null
            && tag != null
        ) {
            val actualState = WorkManagerHelper.getUniqueWorkerState(context, tag)
            if (actualState == WorkInfo.State.RUNNING || actualState == WorkInfo.State.ENQUEUED) {
                Timber.i("$DBL: %s | %s",
                    "setState() for $workType - State not set! Worker already running / enqueued!",
                    "If this coming from a periodic worker, the worker is most likely in it's ENQUEUED state (interval down time)")
                return
            }
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