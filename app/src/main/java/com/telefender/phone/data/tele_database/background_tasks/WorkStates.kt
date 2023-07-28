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
import java.util.*


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
        null
    )

    /**
     * Work states
     */
    private val states: Array<WorkInfo.State?> = Array(WorkType.values().size) { null }

    /**
     * Waiter counters used in workerWaiter() to decide what state to set upon finishing.
     */
    private val waiters: Array<Int> = Array(WorkType.values().size) { 0 }

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
     * TODO: Maybe improve work states by allowing multiple processes to have their own version
     *  of the same work state. That way, we could support duplicate processes. -> Would have to
     *  use map.
     *
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
        while(state != null && state != null) {
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
        val success = state == null
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
                setState(newWork, null)
                return true
            }

            return false
        }
    }

    /**
     * TODO: Do we need mutex lock for same work.
     * TODO: Seems to be some issues here.
     * TODO: When setting state to anything but running, make sure that there are no other ones
     *  running.
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