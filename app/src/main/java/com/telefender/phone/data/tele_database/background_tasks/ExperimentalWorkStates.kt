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

object ExperimentalWorkStates {

    private val propertyChangeSupport = PropertyChangeSupport(this)

    /**
     * As of now, any workers should / can only use the following states in their control flow.
     */
    private val allowedStates = listOf(
        WorkInfo.State.RUNNING,
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.FAILED,
        WorkInfo.State.SUCCEEDED
    )

    private val customStateHierarchy: Map<WorkInfo.State?, Int> = mapOf(
        WorkInfo.State.RUNNING to 4,
        WorkInfo.State.ENQUEUED to 3,
        WorkInfo.State.FAILED to 2,
        WorkInfo.State.SUCCEEDED to 1,
        null to 0
    )

    /**
     * Work states that only consider one instance of work.
     */
    private val generalizedStates: Array<WorkInfo.State?> = Array(WorkType.values().size) { null }

    /**
     * Waiter counters used in generalized worker waiters to decide what state to set upon finishing.
     */
    private val generalizedWaiters: Array<Int> = Array(WorkType.values().size) { 0 }

    /**
     * Work states that allow for multiple instances of work.
     */
    private val localizedStates: Array<MutableMap<UUID, WorkInfo.State>> =
        Array(WorkType.values().size) { mutableMapOf() }

    /**
     * Waiter counters used in localized worker waiters to decide what state to set upon finishing.
     */
    private val localizedWaiters: Array<MutableMap<UUID, Int>> =
        Array(WorkType.values().size) { mutableMapOf() }

    /**
     * For now, we'll stick to using one mutex, since the number of waiters doesn't usually change
     * that quickly.
     */
    private val waiterMutex = Mutex()

    /**
     * Used to prevent race conditions when modifying work states.
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

    suspend fun generalizedWorkWaiter(
        workType: WorkType,
        runningMsg: String? = null,
        stopOnFail: Boolean = false,
        certainFinish: Boolean = false
    ) : Boolean {
        var state = generalizedGetState(workType)

        if (state !in allowedStates) {
            Timber.e("$DBL: ${workType.name} waiter called when worker is not running.")
            generalizedSetState(workType, null)
            return false
        }

        waiterMutex.withLock { generalizedChangeNumWaiters(workType, 1) }

        state = generalizedGetState(workType)
        while(state != WorkInfo.State.SUCCEEDED && state != null) {
            if (state == WorkInfo.State.FAILED && stopOnFail) {
                break
            }
            if (runningMsg != null) {
                Timber.i("$DBL: $runningMsg: %s", state.toString())
            }
            delay(500)
            state = generalizedGetState(workType)
        }

        /**
         * Basically, you want to clean state in the following cases:
         * 1: if success, then wait for last waiter to clean state
         * 2: if failure but worker is fully stopped (given by [certainFinish]),
         *      then stop all waiters by cleaning state (prevents waiters who don't stop on fail
         *      from running indefinitely).
         */
        val success = state == WorkInfo.State.SUCCEEDED
        waiterMutex.withLock { generalizedChangeNumWaiters(workType, -1) }
        if (generalizedGetNumWaiters(workType) == 0 || (!success && certainFinish)) {
            generalizedSetState(workType, null)
        }
        return success
    }

    /**
     * NOTE: This waiter requires the UUID key to the work state instance. Use
     * [localizedWorkWaiterAll] to wait for all work of a certain work type.
     */
    suspend fun localizedWorkWaiterKey(
        workType: WorkType,
        workInstanceKey: UUID,
        runningMsg: String? = null,
        stopOnFail: Boolean = false,
        certainFinish: Boolean = false
    ) : Boolean {
        var state = localizedGetState(workType, workInstanceKey)

        if (state !in allowedStates) {
            Timber.e("$DBL: ${workType.name} waiter called when worker is not running.")
            removeState(workType = workType, workInstanceKey = workInstanceKey)
            return false
        }

        waiterMutex.withLock {
            localizedChangeNumWaitersKey(
                workType = workType,
                workInstanceKey = workInstanceKey,
                changeAmount = 1
            )
        }

        state = localizedGetState(workType, workInstanceKey)
        while(state != WorkInfo.State.SUCCEEDED && state != null) {
            if (state == WorkInfo.State.FAILED && stopOnFail) {
                break
            }
            if (runningMsg != null) {
                Timber.i("$DBL: $runningMsg: %s", state.toString())
            }
            delay(500)
            state = localizedGetState(workType, workInstanceKey)
        }

        /**
         * Basically, you want to clean state in the following cases:
         * 1: if success, then wait for last waiter to clean state
         * 2: if failure but worker is fully stopped (given by [certainFinish]),
         *      then stop all waiters by cleaning state (prevents waiters who don't stop on fail
         *      from running indefinitely).
         */
        val success = state == WorkInfo.State.SUCCEEDED
        waiterMutex.withLock {
            localizedChangeNumWaitersKey(
                workType = workType,
                workInstanceKey = workInstanceKey,
                changeAmount = -1
            )
        }
        if (localizedGetNumWaitersKey(workType, workInstanceKey) == 0 || (!success && certainFinish)) {
            removeState(workType = workType, workInstanceKey = workInstanceKey)
        }
        return success
    }

    /**
     * Variation of [localizedWorkWaiterKey] where
     */
    suspend fun localizedWorkWaiterAll(
        workType: WorkType,
        runningMsg: String? = null,
        stopOnFail: Boolean = false,
        certainFinish: Boolean = false
    ) : Boolean {
        var state = localizedGetStateAll(workType)

        if (state !in allowedStates) {
            Timber.e("$DBL: ${workType.name} waiter called when worker is not running.")
            removeState(workType = workType, removeAll = true)
            return false
        }

        waiterMutex.withLock { localizedChangeNumWaitersAll(workType = workType, changeAmount = 1) }

        state = localizedGetStateAll(workType)
        while(state != WorkInfo.State.SUCCEEDED && state != null) {
            if (state == WorkInfo.State.FAILED && stopOnFail) {
                break
            }
            if (runningMsg != null) {
                Timber.i("$DBL: $runningMsg: %s", state.toString())
            }
            delay(500)
            state = localizedGetStateAll(workType)
        }

        /**
         * Basically, you want to clean state in the following cases:
         * 1: if success, then wait for last waiter to clean state
         * 2: if failure but worker is fully stopped (given by [certainFinish]),
         *      then stop all waiters by cleaning state (prevents waiters who don't stop on fail
         *      from running indefinitely).
         */
        val success = state == WorkInfo.State.SUCCEEDED
        waiterMutex.withLock { localizedChangeNumWaitersAll(workType = workType, changeAmount = -1) }
        if (localizedGetNumWaitersAll(workType) == 0 || (!success && certainFinish)) {
            removeState(workType = workType, removeAll = true)
        }
        return success
    }

    fun addPropertyChangeListener(pcl: PropertyChangeListener?) {
        propertyChangeSupport.addPropertyChangeListener(pcl)
    }

    fun removePropertyChangeListener(pcl: PropertyChangeListener?) {
        propertyChangeSupport.removePropertyChangeListener(pcl)
    }

    /**
     * TODO: Do we need mutex lock for same work.
     * TODO: Seems to be some issues here.
     * TODO: When setting state to anything but running, make sure that there are no other ones
     *  running.
     *
     * Sets the state of a certain WorkType.
     */
    fun generalizedSetState(
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

        generalizedStates[workType.ordinal] = workState

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

    /**
     * TODO: Mutex
     *
     * Sets the state of a certain WorkType. Returns the sub-key of the work state modified in
     * [states] (which is helpful for monitoring / changing a specific work state instance).
     */
    fun localizedSetState(
        workType: WorkType,
        workState: WorkInfo.State,
        context: Context? = null,
        tag: String? = null,
        workInstanceKey: UUID? = null,
        newWorkInstance: Boolean = false,
    ) : UUID? {
        val invalidWorkKey = workInstanceKey == null || workInstanceKey !in localizedStates[workType.ordinal].keys
        val invalidWorkState = workState !in allowedStates

        /*
        If you're not creating a new work instance (think duplicate of same work type) and you don't
        have a valid work instance key, then we don't know which work instance state you are setting.
         */
        if ((invalidWorkKey && !newWorkInstance) || invalidWorkState) {
            Timber.e("$DBL: setState() for $workType - %s",
                "$workState is an invalid workState OR $workInstanceKey is an invalid key!")
            return null
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
                return null
            }
        }

        val currentUUID = if (newWorkInstance) UUID.randomUUID() else workInstanceKey!!
        localizedStates[workType.ordinal][currentUUID] = workState

        /**
         * Notifies property listeners. oldValue is workState while newValue is workType since
         * propertyChangeSupport only notifies workers if it sees a change in oldValue and newValue.
         *
         * Technically supposed to pass in old and new value of actual workerState. However, since
         * we have so many worker states, we just pass in the workerState and workType in place
         * of those values.
         */
        this.propertyChangeSupport.firePropertyChange("setState", workState, workType)

        return currentUUID
    }

    /**
     * TODO: When setting state to anything but running, make sure that there are no other ones
     *  running.
     */
    fun localizedSetStateAll(
        workType: WorkType,
        workState: WorkInfo.State,
        context: Context? = null,
        tag: String? = null,
    ) : UUID? {
        /*
        If you're not creating a new work instance (think duplicate of same work type) and you don't
        have a valid work instance key, then we don't know which work instance state you are setting.
         */
        if (workState !in allowedStates) {
            Timber.e("$DBL: setState() for $workType - %s",
                "$workState is an invalid workState")
            return null
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
                return null
            }
        }

        val currentUUID: UUID?
        val workInstances = localizedStates[workType.ordinal]

        if (workInstances.isEmpty()) {
            currentUUID = UUID.randomUUID()
            workInstances[currentUUID] = workState
        } else {
            currentUUID = null
            for (workInstance in workInstances) {
                workInstance.setValue(workState)
            }
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

        return currentUUID
    }

    /**
     * TODO: Mutex
     *
     * Removes work instance from [localizedStates] given the [workInstanceKey]. Returns whether the work
     * instance was successfully removed. Removing a work instance is usually used to indicate
     * that the work is finished.
     */
    fun removeState(
        workType: WorkType,
        workInstanceKey: UUID? = null,
        removeAll: Boolean = false
    ) : Boolean {
        val invalidWorkKey = workInstanceKey == null || workInstanceKey !in localizedStates[workType.ordinal].keys
        if (invalidWorkKey && !removeAll) {
            return false
        }

        if (removeAll) {
            localizedStates[workType.ordinal].clear()
        } else {
            localizedStates[workType.ordinal].remove(workInstanceKey!!)
        }

        return true
    }

    fun generalizedGetState(workType: WorkType) : WorkInfo.State? {
        return generalizedStates[workType.ordinal]
    }

    fun localizedGetState(workType: WorkType, workInstanceKey: UUID) : WorkInfo.State? {
        return localizedStates[workType.ordinal][workInstanceKey]
    }

    fun localizedGetStateAll(workType: WorkType) : WorkInfo.State? {
        var highestLevelState: WorkInfo.State? = null
        for (pair in localizedStates[workType.ordinal]) {
            if (customStateHierarchy[pair.value]!! > customStateHierarchy[null]!!) {
                highestLevelState = pair.value
            }
        }

        return highestLevelState
    }

    private fun generalizedChangeNumWaiters(workType: WorkType, changeAmount: Int) {
        generalizedWaiters[workType.ordinal] += changeAmount
    }

    private fun localizedChangeNumWaitersKey(
        workType: WorkType,
        workInstanceKey: UUID,
        changeAmount: Int
    ) {
        localizedWaiters[workType.ordinal][workInstanceKey]?.plus(changeAmount)
    }

    private fun localizedChangeNumWaitersAll(workType: WorkType, changeAmount: Int) {
        for (pair in localizedWaiters[workType.ordinal]) {
            val finalAmount = pair.value - changeAmount
            if (finalAmount >= 0) {
                pair.setValue(finalAmount)
            } else {
                pair.setValue(0)
            }
        }
    }

    private fun generalizedGetNumWaiters(workType: WorkType) : Int {
        return generalizedWaiters[workType.ordinal]
    }

    private fun localizedGetNumWaitersKey(workType: WorkType, workInstanceKey: UUID) : Int {
        return localizedWaiters[workType.ordinal][workInstanceKey] ?: 0
    }

    private fun localizedGetNumWaitersAll(workType: WorkType) : Int {
        var sum = 0
        for (pair in localizedWaiters[workType.ordinal]) {
            sum += pair.value
        }
        return sum
    }
}