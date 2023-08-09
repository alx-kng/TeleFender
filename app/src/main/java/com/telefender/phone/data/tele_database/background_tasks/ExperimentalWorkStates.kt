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
import java.sql.Time
import java.util.*


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
    DEBUG_CALL_STATE_POST,

    // Other processes
    SYNC_CONTACTS,
    SYNC_LOGS,
    EXECUTE_CHANGES
}

/**
 * TODO: Get rid of SUCCEEDED. Unnecessary. -> Double check
 */
object ExperimentalWorkStates {

    private val propertyChangeSupport = PropertyChangeSupport(this)

    /**
     * As of now, any workers should / can only use the following states in their control flow.
     */
    private val allowedStates = listOf(
        WorkInfo.State.RUNNING,
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.FAILED,
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
     * Work states that allow for multiple instances of work. Mostly for non-workers.
     */
    private val localizedStates: Array<MutableMap<UUID, WorkInfo.State>> =
        Array(WorkType.values().size) { mutableMapOf() }

    /**
     * Queue for competing instances of one work type (where only one can be RUNNING).
     */
    private val localizedCompeteQueues: Array<Queue<UUID>> =
        Array(WorkType.values().size) { LinkedList() }

    /**
     * Waiter counters used in localized worker waiters to decide what state to set upon finishing.
     */
    private val localizedWaiters: Array<MutableMap<UUID, Int>> =
        Array(WorkType.values().size) { mutableMapOf() }

    /**
     * Used to prevent race conditions when modifying work states.
     */
    private val stateMutex = Mutex()

    /**
     * For now, we'll stick to using one mutex, since the number of waiters doesn't usually change
     * that quickly.
     */
    private val waiterMutex = Mutex()

    /**
     * Specifically used for modifying the [localizedCompeteQueues]
     */
    private val queueMutex = Mutex()

    /**
     * Specifically used for checking mutually exclusive work (from a work perspective rather than
     * an actual thread perspective).
     */
    private val mutexMutex = Mutex()

    /***********************************************************************************************
     * TODO: Improve the accuracy of the success return value. -> may be better now
     *
     * TODO: Maybe improve work states by allowing multiple processes to have their own version
     *  of the same work state. That way, we could support duplicate processes. -> Would have to
     *  use map. -> Think it's done but double check.
     *
     * TODO: Clean up edge cases. Ex) what if worker hasn't started yet before waiter???
     *  ?? Fix inside workers initialize functions. Also, Polish up system for multiple
     *  waiters of one type. -> Think it's done but double check.
     *
     * Work wait function that only return when the corresponding work is finished. Acts as a
     * roadblock of sorts so that the code following the waiter isn't run until corresponding
     * work finishes. Returns whether or not the work succeeded. If work is not running when waiter
     * is called, then we return null.
     *
     * Requires that worker is running, otherwise the waiter returns (in order to
     * prevent infinite loop). Also, it's not really meant to be used for periodic workers,
     * although no large error will arise from such a usage.
     **********************************************************************************************/

    suspend fun generalizedWorkWaiter(
        workType: WorkType,
        runningMsg: String? = null,
        stopOnFail: Boolean = false,
        stopOnEnqueue: Boolean = false,
        certainFinish: Boolean = false,
    ) : Boolean? {
        var state = generalizedGetState(workType)

        if (state !in allowedStates) {
            Timber.e("$DBL: ${workType.name} waiter called when work is not running.")
            generalizedSetState(workType, null)
            return null
        }

        generalizedChangeNumWaiters(workType, 1)

        state = generalizedGetState(workType)
        while(state != null) {
            if (state == WorkInfo.State.FAILED && stopOnFail) break
            if (state == WorkInfo.State.ENQUEUED && stopOnEnqueue) break

            if (runningMsg != null) {
                Timber.i("$DBL: $runningMsg: %s", state.toString())
            }
            delay(500)
            state = generalizedGetState(workType)
        }

        /**
         * Basically, you want to clean state in the following cases:
         * 1: if success (null), then slate is already clean.
         * 2: if not success (could be fail or enqueue) but work is fully stopped / inactive (given
         *  by [certainFinish]), then stop all waiters by cleaning state (prevents waiters who don't
         *  stop on fail or enqueue from running indefinitely).
         * 3: if not success but there are no more waiters, then last waiter cleans slate.
         */
        val success = state == null
        generalizedChangeNumWaiters(workType, -1)
        if (generalizedGetNumWaiters(workType) == 0 || (!success && certainFinish)) {
            generalizedSetState(workType, null)
        }
        return success
    }

    /**
     * NOTE: This waiter requires the UUID key to the work state instance.
     */
    suspend fun localizedWorkWaiterKey(
        workType: WorkType,
        workInstanceKey: UUID,
        runningMsg: String? = null,
        stopOnFail: Boolean = false,
        stopOnEnqueue: Boolean = false,
        certainFinish: Boolean = false
    ) : Boolean {
        var state = localizedGetState(workType, workInstanceKey)

        if (state !in allowedStates) {
            Timber.e("$DBL: ${workType.name} waiter called when work is not running.")
            localizedRemoveState(workType = workType, workInstanceKey = workInstanceKey)
            return false
        }

        localizedInitWaiters(workType, workInstanceKey)

        localizedChangeNumWaitersKey(
            workType = workType,
            workInstanceKey = workInstanceKey,
            changeAmount = 1
        )

        state = localizedGetState(workType, workInstanceKey)
        while(state != null) {
            if (state == WorkInfo.State.FAILED && stopOnFail) break
            if (state == WorkInfo.State.ENQUEUED && stopOnEnqueue) break

            if (runningMsg != null) {
                Timber.i("$DBL: $runningMsg: %s", state.toString())
            }
            delay(500)
            state = localizedGetState(workType, workInstanceKey)
        }

        /**
         * Basically, you want to clean state in the following cases:
         * 1: if success (null), then state is already clean.
         * 2: if not success (could be fail or enqueue) but work is fully stopped / inactive (given
         *  by [certainFinish]), then stop all waiters by cleaning state (prevents waiters who don't
         *  stop on fail or enqueue from running indefinitely).
         * 3: if not success but work is not fully stopped, then last waiter cleans state and
         *  removes waiter instance.
         */
        val success = state == null
        localizedChangeNumWaitersKey(
            workType = workType,
            workInstanceKey = workInstanceKey,
            changeAmount = -1
        )

        if (!success && certainFinish) {
            localizedRemoveState(workType = workType, workInstanceKey = workInstanceKey)
        }

        if (localizedGetNumWaitersKey(workType, workInstanceKey) == 0) {
            localizedRemoveState(workType = workType, workInstanceKey = workInstanceKey)
            localizedRemoveWaiter(workType = workType, workInstanceKey = workInstanceKey)
        }
        return success
    }

    /**
     * TODO: Maybe prevent RUNNING -> RUNNING property change? Technically, it should never happen,
     *  and it isn't a huge deal either way.
     *
     * Creates a new work instance in [localizedStates] with state set to RUNNING once it's first
     * in the [localizedCompeteQueues] and there are no other RUNNING work instances of the same
     * type. Returns the UUID of the new work instance once the state is set to RUNNING.
     *
     * NOTE: This basically works as a waiter as well.
     *
     * NOTE: You should only use the compete function for a non-worker [workType], as workers can
     * only have one instance running. If you do pass in a worker [workType], then we return null.
     */
    suspend fun localizedCompete(
        workType: WorkType,
        workInstanceKey: UUID? = null,
        newWorkInstance: Boolean = false,
        runningMsg: String? = null,
    ) : UUID? {
        if (workType.isWorker) return null

        val invalidWorkKey = workInstanceKey !in localizedStates[workType.ordinal].keys

        /*
        If you're not creating a new work instance (think duplicate of same work type) and you don't
        have a valid work instance key, then we don't know which work instance state you are setting.
         */
        if (invalidWorkKey && !newWorkInstance) {
            Timber.e("$DBL: localizedCompete() for $workType - %s",
                "$workInstanceKey is an invalid key for existing work instance compete!")
            return null
        }

        val workKey = if (newWorkInstance) UUID.randomUUID() else workInstanceKey!!
        val competeQueue = localizedCompeteQueues[workType.ordinal]

        /*
        If the work instance we're using to compete (given by workKey) already exists and is
        running, then there is no need to compete, so return. Note that if newWorkInstance = true,
        then the following check will automatically be ignored (as workKey would have no associated
        state yet).
         */
        if (localizedGetState(workType, workKey) == WorkInfo.State.RUNNING
        ) {
            Timber.i("$DBL: localizedCompete() for $workType - %s",
                "$workKey is already RUNNING, no need to compete. Probably due to retry!")
            return workKey
        }

        queueMutex.withLock {
            competeQueue.add(workKey)
        }

        while(true) {
            while(localizedHasOtherRunning(workType, workKey)) {
                if (runningMsg != null) {
                    Timber.i("$DBL: compete() - %s",
                        "workType = $workType, workKey = $workKey - $runningMsg")
                }
                delay(500)
            }

            queueMutex.withLock {
                stateMutex.withLock {
                    // If first in queue, then remove from UUID from queue, set state to RUNNING.
                    if (competeQueue.peek() == workKey || competeQueue.peek() == null) {
                        competeQueue.poll()
                        localizedStates[workType.ordinal][workKey] = WorkInfo.State.RUNNING

                        // Notifies property listeners, like in the other setState functions.
                        this.propertyChangeSupport.firePropertyChange(
                            "setState",
                            WorkInfo.State.RUNNING,
                            workType
                        )
                        return workKey
                    }
                }
            }
        }
    }

    /**
     * Used if you want two different types of work to not run at the same time (e.g.,
     * ONE_TIME_DEBUG and PERIODIC_DEBUG). [originalWork] is the work that you think might already
     * running, and [newWork] is the work that you want to stop if [originalWork] is already running.
     * Returns true if [originalWork] is already running.
     */
    suspend fun mutuallyExclusiveGeneralizedWork(originalWork: WorkType, newWork: WorkType) : Boolean {
        /*
        Mutex prevents the following scenario. Say you have one-time and periodic workers that
        should not run at the same time (e.g., debug workers). Their states are both set to RUNNING
        before the workers actually begin. In the very rare case that both make it past the "if"
        statement (since both see that the other is running), both workers will be canceled. By
        locking the code, we can make sure that the states are accurate when checked.
         */
        mutexMutex.withLock {
            Timber.e("$DBL: mutuallyExclusiveGeneralizedWork() - %s | %s",
                "originalWork = $originalWork, state = ${generalizedGetState(originalWork)}",
                "newWork = $newWork, state = ${generalizedGetState(newWork)}")

            if (generalizedGetState(originalWork) == WorkInfo.State.RUNNING) {
                Timber.i("$DBL: $newWork ENDED - $originalWork RUNNING")
                generalizedSetState(newWork, null)
                return true
            }

            return false
        }
    }

    fun addPropertyChangeListener(pcl: PropertyChangeListener?) {
        propertyChangeSupport.addPropertyChangeListener(pcl)
    }

    fun removePropertyChangeListener(pcl: PropertyChangeListener?) {
        propertyChangeSupport.removePropertyChangeListener(pcl)
    }

    /**
     * TODO: Do we need to include [workState] = ENQUEUED?
     *
     * TODO: Maybe should only allow same state change. -> seconded
     *
     * Returns whether we should allow a [workType] instance to be set to [workState] (specifically
     * used for background workers).
     */
    private fun shouldAllowSetState(
        workType: WorkType,
        workState: WorkInfo.State?,
        context: Context? = null,
        tag: String? = null
    ) : Boolean {
        if (workType.isWorker
            && (workState == WorkInfo.State.RUNNING || workState == WorkInfo.State.ENQUEUED)
            && context != null
            && tag != null
        ) {
            /*
            If workState is RUNNING and actualState is ENQUEUED or vise versa, we don't allow
            setting the state to RUNNING / ENQUEUED. Aside from ENQUEUED, we don't prevent the user
            from setting the state to RUNNING even when the actual state is not RUNNING, as the
            setState function can be called before the worker even starts.
             */
            val actualState = WorkManagerHelper.getUniqueWorkerState(context, tag)
            if (actualState == WorkInfo.State.RUNNING || actualState == WorkInfo.State.ENQUEUED) {
                return actualState == workState
            }
        }

        return true
    }

    /**
     * TODO: Seems to be some issues here. -> what specifically?
     * TODO: When setting state to anything but running, make sure that there are no other ones
     *  running. -> is this still a problem?
     *
     * Sets the state of a certain WorkType.
     */
    suspend fun generalizedSetState(
        workType: WorkType,
        workState: WorkInfo.State?,
        context: Context? = null,
        tag: String? = null,
    ) {
        if (workState !in allowedStates && workState != null) {
            Timber.e("$DBL: setState() for $workType - $workState is an invalid workState")
            return
        }

        /*
        Safety measure to prevent setting WorkState to running / enqueued if Worker is already
        running / enqueued. Although it may seem redundant, it actually prevents waiters from
        waiting for work that isn't technically running (e.g., periodic worker during repeat interval).
         */
        if (!shouldAllowSetState(workType, workState, context, tag)) {
            Timber.i("$DBL: %s | %s%s%s",
                "setState() for $workType - State not set! ",
                "Either worker state = RUNNING and $workState = ENQUEUED or vise versa. ",
                "If this coming from a periodic worker, the worker is most likely in it's ENQUEUED ",
                "state (interval down time)")
            return
        }

        /*
        No need to put workMutex around entire function, as we just change the state for the
        generalized work instance regardless.
         */
        stateMutex.withLock {
            generalizedStates[workType.ordinal] = workState
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

    /**
     * TODO: How to clean if no waiters and failed? -> Think it's fixed, but double check.
     *
     * TODO: Check if mutex is used over the correct portion of code (i.e., maybe we can make do
     *  with locking around a smaller portion?).
     *
     * Sets the state of a certain WorkType. Returns the sub-key of the work state modified in
     * [localizedStates] (which is helpful for monitoring / changing a specific work state instance).
     *
     * NOTE: We lock the entire function with [stateMutex] because without a lock, [workInstanceKey]
     * may become invalid between the time of the invalid check and the actual setting of the state.
     * Although this may not cause any huge issues, it can produce a false positive in the
     * [propertyChangeSupport].
     */
    suspend fun localizedSetStateKey(
        workType: WorkType,
        workState: WorkInfo.State,
        context: Context? = null,
        tag: String? = null,
        workInstanceKey: UUID? = null,
        newWorkInstance: Boolean = false,
    ) : UUID?
    = stateMutex.withLock {
        val invalidWorkKey = workInstanceKey !in localizedStates[workType.ordinal].keys
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
        Safety measure to prevent setting WorkState to running / enqueued if Worker is already
        running / enqueued. Although it may seem redundant, it actually prevents waiters from
        waiting for work that isn't technically running (e.g., periodic worker during repeat interval).
         */
        if (!shouldAllowSetState(workType, workState, context, tag)) {
            Timber.i("$DBL: %s | %s%s%s",
                "setState() for $workType - State not set! ",
                "Either worker state = RUNNING and $workState = ENQUEUED or vise versa. ",
                "If this coming from a periodic worker, the worker is most likely in it's ENQUEUED ",
                "state (interval down time)")
            return null
        }

        val currentUUID = if (newWorkInstance) UUID.randomUUID() else workInstanceKey!!
        val newFailedOrEnqueued = workState == WorkInfo.State.FAILED || workState == WorkInfo.State.ENQUEUED

        /*
        If the calling function is trying to set the work instance state to FAILED or ENQUEUED,
        and there are no waiters for that work instance, then there is no reason to update the work
        instance state (as the waiters won't see it anyways), and we should remove the state instead.
        Moreover, since there are no waiters to remove the state for us, if we don't remove the
        state here, the work instance will actually linger in the map.

        The reason why we don't remove the state for RUNNING when there are no waiters is because
        the lingering problem doesn't arise from setting the state to RUNNING. Also, it's can be
        helpful when viewed from the debug console.
         */
        if (newFailedOrEnqueued && localizedGetNumWaitersKey(workType, currentUUID) == 0) {
            localizedRemoveState(workType = workType, workInstanceKey = workInstanceKey, useLock = false)

            this.propertyChangeSupport.firePropertyChange("setState", workState, workType)
            return null
        } else {
            localizedStates[workType.ordinal][currentUUID] = workState

            this.propertyChangeSupport.firePropertyChange("setState", workState, workType)
            return currentUUID
        }
    }

    /**
     * Removes work instance from [localizedStates] given the [workInstanceKey]. Returns whether the
     * work instance was successfully removed. Removing a work instance is usually used to indicate
     * that the work is finished.
     *
     * NOTE: [localizedRemoveState] should be used at the end of the work instead of using
     * [localizedSetStateKey] to set the work state to null (which also doesn't work), as
     * [localizedStates] can build up instances if we don't remove them. Moreover, removing the
     * state basically sets the state to null and pretty much has the same effect on waiters.
     *
     * NOTE: [localizedRemoveState] is a suspend function, so it must be called from another suspend
     * function or coroutine.
     *
     * NOTE: We lock the entire function with [stateMutex] because without a lock, [workInstanceKey]
     * may become invalid between the time of the invalid check and the actual setting of the state.
     * Although this may not cause any huge issues, it can produce a false positive in the return
     * value of whether the work instance was removed or not.
     */
    suspend fun localizedRemoveState(
        workType: WorkType,
        workInstanceKey: UUID? = null,
        useLock: Boolean = true,
        removeAll: Boolean = false
    ) : Boolean {
        return if (useLock) {
            stateMutex.withLock {
                localizedRemoveStateHelper(workType, workInstanceKey, removeAll)
            }
        } else {
            localizedRemoveStateHelper(workType, workInstanceKey, removeAll)
        }
    }

    /**
     * Helper function for [localizedRemoveState].
     */
    private fun localizedRemoveStateHelper(
        workType: WorkType,
        workInstanceKey: UUID?,
        removeAll: Boolean,
    ) : Boolean {
        val invalidWorkKey = workInstanceKey !in localizedStates[workType.ordinal].keys
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

    fun localizedGetStatesByType(workType: WorkType) : Map<UUID, WorkInfo.State> {
        return localizedStates[workType.ordinal]
    }

    private fun localizedHasOtherRunning(workType: WorkType, workInstanceKey: UUID) : Boolean {
        return localizedStates[workType.ordinal]
            .filter { it.key != workInstanceKey }
            .containsValue(WorkInfo.State.RUNNING)
    }

    private suspend fun generalizedChangeNumWaiters(workType: WorkType, changeAmount: Int) {
        waiterMutex.withLock {
            generalizedWaiters[workType.ordinal] += changeAmount
        }
    }

    private suspend fun localizedInitWaiters(workType: WorkType, workInstanceKey: UUID) {
        waiterMutex.withLock {
            // Don't add waiter instance if corresponding work state instance doesn't exist.
            if (workInstanceKey !in localizedStates[workType.ordinal].keys) {
                return
            }

            // If waiter instance doesn't exist for key, then create one. Otherwise, don't touch.
            if (localizedWaiters[workType.ordinal][workInstanceKey] == null) {
                localizedWaiters[workType.ordinal][workInstanceKey] = 0
            }
        }
    }

    private suspend fun localizedChangeNumWaitersKey(
        workType: WorkType,
        workInstanceKey: UUID,
        changeAmount: Int
    ) {
        waiterMutex.withLock {
            // Adds changeAmount to the number of waiters if the waiter instance exists.
            localizedWaiters[workType.ordinal][workInstanceKey]
                ?.plus(changeAmount)
                ?.let { localizedWaiters[workType.ordinal][workInstanceKey] = it }
        }
    }

    private suspend fun localizedRemoveWaiter(workType: WorkType, workInstanceKey: UUID) {
        waiterMutex.withLock {
            localizedWaiters[workType.ordinal].remove(workInstanceKey)
        }
    }

    private fun generalizedGetNumWaiters(workType: WorkType) : Int {
        return generalizedWaiters[workType.ordinal]
    }

    private fun localizedGetNumWaitersKey(workType: WorkType, workInstanceKey: UUID) : Int {
        return localizedWaiters[workType.ordinal][workInstanceKey] ?: 0
    }
}