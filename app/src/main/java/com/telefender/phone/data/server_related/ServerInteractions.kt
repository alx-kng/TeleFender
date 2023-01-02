package com.telefender.phone.data.server_related

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.*
import com.android.volley.toolbox.StringRequest
import com.telefender.phone.data.server_related.RequestHelpers.downloadRequestToJson
import com.telefender.phone.data.server_related.RequestHelpers.uploadRequestToJson
import com.telefender.phone.data.server_related.ResponseHelpers.jsonToDefaultResponse
import com.telefender.phone.data.server_related.ResponseHelpers.jsonToUploadResponse
import com.telefender.phone.data.server_related.request_generators.DownloadRequestGen
import com.telefender.phone.data.server_related.request_generators.InitialRequestGen
import com.telefender.phone.data.server_related.request_generators.TokenRequestGen
import com.telefender.phone.data.server_related.request_generators.UploadRequestGen
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkerStates
import com.telefender.phone.data.tele_database.background_tasks.WorkerType
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.data.tele_database.entities.UploadQueue
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.*
import org.json.JSONException
import timber.log.Timber
import java.io.UnsupportedEncodingException

object ServerInteractions {

    @SuppressLint("MissingPermission")
    suspend fun downloadPostRequest(context: Context, repository: ClientRepository, scope: CoroutineScope) {
        val url = "https://dev.scribblychat.com/callbook/downloadChanges"
        val instanceNumber = MiscHelpers.getInstanceNumber(context)

        val key = repository.getClientKey(instanceNumber)
        val lastServerChangeID = repository.lastServerChangeID()
        val downloadRequestJson: String

        if (key != null) {
            val downloadRequest = DownloadRequest(instanceNumber, key, lastServerChangeID)
            downloadRequestJson = downloadRequestToJson(downloadRequest)
        } else {
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: CLIENT KEY IS NULL")
            return
        }

        try {
            val stringRequest = DownloadRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = downloadRequestJson,
                context = context,
                repository = repository,
                scope = scope
            )

            // Adds entire string request to request queue
            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * TODO: Make sure to put in max retry amount.
     * TODO: Also repeating uploads (partial error). Could be server issue.
     * TODO: Can returning list with dao give null?
     *
     * Sends post request containing the string requestString to the server at url.
     * Returns whether or not post was successful
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    suspend fun uploadPostRequest(context: Context, repository: ClientRepository, scope: CoroutineScope) {
        val url = "https://dev.scribblychat.com/callbook/uploadChanges"
        val instanceNumber = MiscHelpers.getInstanceNumber(context)

        val key = repository.getClientKey(instanceNumber)
        val uploadRequestJson: String

        if (key != null) {
            withContext(Dispatchers.IO) {

                // Gets first 200 upload logs in order of rowID.
                val uploadLogs = repository.getChunkQTUByRowID()

                // Retrieves corresponding change logs for each upload log
                val changeLogs = mutableListOf<ChangeLog>()
                for (uploadLog in uploadLogs) {
                    val changeLog = repository.getChangeLogRow(uploadLog.changeID)
                    changeLogs.add(changeLog)
                }

                val uploadRequest = UploadRequest(instanceNumber, key, changeLogs)
                uploadRequestJson = uploadRequestToJson(uploadRequest)
            }
        } else {
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: CLIENT KEY IS NULL")
            return
        }

        Timber.i("${MiscHelpers.DEBUG_LOG_TAG} UPLOAD REQUEST JSON: $uploadRequestJson")

        try {
            val stringRequest = UploadRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = uploadRequestJson,
                context = context,
                repository = repository,
                scope = scope
            )

            // Adds entire string request to request queue
            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * TODO: Don't know url right now.
     * TODO: Make sure user is always setup before this (I think we already have, but double check).
     *
     * Could be used with the suggested token checkup every month, or used whenever token updates
     * in general as it is incredibly important server has access to reliable tokens for every device.
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    suspend fun tokenPostRequest(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        token: String
    ) {
        val url = ""
        val instanceNumber = MiscHelpers.getInstanceNumber(context)

        val key = repository.getClientKey(instanceNumber)
        val tokenRequestJson : String

        if (key != null) {
            val tokenRequest = TokenRequest(instanceNumber, key, token)
            tokenRequestJson = RequestHelpers.tokenRequestToJson(tokenRequest)
        } else {
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: CLIENT KEY IS NULL")
            return
        }

        try {
            val stringRequest = TokenRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = tokenRequestJson,
                context = context,
                repository = repository,
                scope = scope
            )

            // Adds entire string request to request queue
            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }
}