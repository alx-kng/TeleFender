package com.telefender.phone.data.server_related

import android.content.Context
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import androidx.work.WorkInfo
import com.android.volley.Request
import com.telefender.phone.data.server_related.json_classes.DebugExchangeRequest
import com.telefender.phone.data.server_related.json_classes.KeyRequest
import com.telefender.phone.data.server_related.request_generators.DebugCheckRequestGen
import com.telefender.phone.data.server_related.request_generators.DebugExchangeRequestGen
import com.telefender.phone.data.server_related.request_generators.DebugSessionRequestGen
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.MutexType
import com.telefender.phone.data.tele_database.TeleLocks
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import timber.log.Timber
import java.time.Instant

object RemoteDebug {

    // TODO: Change this to a more reasonable time.
    const val maxIdlePeriod = 6000000L

    var isEnabled = false
    var remoteSessionID: String? = null
    var commandRunning = false
    var startTime = 0L
    var lastCommandTime: Long? = null
    var error: String? = null

    /**
     * Queue of data to be sent up to server.
     */
    private val dataQueue = mutableListOf<String>()

    /**
     * Add string data to [dataQueue] with lock.
     */
    suspend fun enqueueData(data: String) {
        TeleLocks.mutexLocks[MutexType.DEBUG_DATA]!!.withLock {
            dataQueue.add(data)
        }
    }

    /**
     * Add string data list to [dataQueue] with lock.
     */
    suspend fun enqueueList(dataList: List<String>) {
        TeleLocks.mutexLocks[MutexType.DEBUG_DATA]!!.withLock {
            dataQueue.addAll(dataList)
        }
    }

    /**
     * TODO: Make this send up more data in each chunk if possible (dynamically). That way, we don't
     *  have to send as many requests.
     *
     * Removes and returns a chunk of data from [dataQueue] to be sent up to the server.
     */
    suspend fun dequeueChunk(chunkSize: Int = 70) : List<String> {
        val chunk = mutableListOf<String>()

        TeleLocks.mutexLocks[MutexType.DEBUG_DATA]!!.withLock {
            for (i in 1..chunkSize) {
                if (dataQueue.size == 0) return@withLock

                chunk.add(dataQueue.removeFirst())
            }
        }

        return chunk
    }

    fun resetStates() {
        isEnabled = false
        remoteSessionID = null
        commandRunning = false
        startTime = 0L
        lastCommandTime = null
        error = null
        dataQueue.clear()
    }

    /**
     * Post request to check if remote debug should be enabled.
     */
    suspend fun debugCheckRequest(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
    ) {
        /*
        Resets RemoteDebug states for case where multiple debug sessions open and close during
        a single app session (app isn't closed). Technically, the reset should be taken care of
        when the remote session is closed (received as a command). However, we also reset here just
        in case the remote session isn't properly closed.
         */
        resetStates()

        val url = "https://dev.scribblychat.com/callbook/rjs/check"
        val instanceNumber = TeleHelpers.getUserNumberStored(context)
        val key = repository.getClientKey()

        if (instanceNumber == null || key == null) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: " +
                "VOLLEY: ERROR - INSTANCE NUMBER = $instanceNumber | CLIENT KEY = $key")

            WorkStates.setState(WorkType.DEBUG_CHECK_POST, WorkInfo.State.FAILED)
            return
        }

        val debugRequestJson = KeyRequest(instanceNumber, key).toJson()

        try {
            val stringRequest = DebugCheckRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = debugRequestJson,
                context = context,
                repository = repository,
                scope = scope,
            )

            // Adds entire string request to request queue
            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }


    /**
     * Post request to retrieve remote session ID if the remote debug is enabled.
     */
    suspend fun debugSessionRequest(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
    ) {
        if (!isEnabled) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: debugSessionRequest() - Not enabled")
            WorkStates.setState(WorkType.DEBUG_SESSION_POST, WorkInfo.State.SUCCEEDED)
            return
        }

        val url = "https://dev.scribblychat.com/callbook/rjs/getid"
        val instanceNumber = TeleHelpers.getUserNumberStored(context)
        val key = repository.getClientKey()

        if (instanceNumber == null || key == null) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: " +
                "VOLLEY: ERROR - INSTANCE NUMBER = $instanceNumber | CLIENT KEY = $key")

            WorkStates.setState(WorkType.DEBUG_SESSION_POST, WorkInfo.State.FAILED)
            return
        }

        val debugRequestJson = KeyRequest(instanceNumber, key).toJson()

        try {
            val stringRequest = DebugSessionRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = debugRequestJson,
                context = context,
                repository = repository,
                scope = scope,
            )

            // Adds entire string request to request queue
            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Post request to exchange data / commands with server if remote session ID is not null.
     */
    suspend fun debugExchangeRequest(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
    ) {
        if (remoteSessionID == null) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: debugExchangeRequest() - No remoteSessionID")
            WorkStates.setState(WorkType.DEBUG_EXCHANGE_POST, WorkInfo.State.SUCCEEDED)
            return
        }

        val url = "https://dev.scribblychat.com/callbook/rjs/exchangeData"
        val instanceNumber = TeleHelpers.getUserNumberStored(context)
        val key = repository.getClientKey()

        if (instanceNumber == null || key == null) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: " +
                "VOLLEY: ERROR - INSTANCE NUMBER = $instanceNumber | CLIENT KEY = $key")

            WorkStates.setState(WorkType.DEBUG_EXCHANGE_POST, WorkInfo.State.FAILED)
            return
        }

        val debugRequestJson = DebugExchangeRequest(
            instanceNumber = instanceNumber,
            key = key,
            remoteSessionID = remoteSessionID!!,
            data = dequeueChunk(),
            // Means that data returned from command is all sent and command is no longer running.
            commandComplete = dataQueue.size == 0 && !commandRunning,
            error = error
        ).toJson()

        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: " +
            "REMOTE: error = $error | commandComplete = ${dataQueue.size == 0 && !commandRunning}")

        try {
            val stringRequest = DebugExchangeRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = debugRequestJson,
                context = context,
                repository = repository,
                scope = scope,
            )

            // Adds entire string request to request queue
            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }
}