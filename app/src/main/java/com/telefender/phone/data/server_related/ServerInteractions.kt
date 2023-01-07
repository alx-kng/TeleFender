package com.telefender.phone.data.server_related

import android.annotation.SuppressLint
import android.content.Context
import com.android.volley.*
import com.telefender.phone.data.server_related.request_generators.DownloadRequestGen
import com.telefender.phone.data.server_related.request_generators.TokenRequestGen
import com.telefender.phone.data.server_related.request_generators.UploadAnalyzedRequestGen
import com.telefender.phone.data.server_related.request_generators.UploadChangeRequestGen
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.entities.AnalyzedNumber
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.*
import org.json.JSONException
import timber.log.Timber

object ServerInteractions {

    @SuppressLint("MissingPermission")
    suspend fun downloadPostRequest(context: Context, repository: ClientRepository, scope: CoroutineScope) {
        val url = "https://dev.scribblychat.com/callbook/downloadChanges"
        val instanceNumber = MiscHelpers.getInstanceNumber(context)

        val key = repository.getClientKey(instanceNumber)
        val lastServerChangeID = repository.lastServerChangeID()
        val downloadRequestJson: String

        if (key != null) {
            downloadRequestJson = DownloadRequest(instanceNumber, key, lastServerChangeID).toJson()
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
     * TODO: Make sure to put in max retry amount. Also, prevent partial error uploads from
     *  repeating forever, as they aren't crucial.
     * TODO: Can returning list with dao give null?
     * TODO: GET RID OF PLURAL CHANGES
     *
     * Sends a post request containing the ChangeLogs to the server.
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    suspend fun uploadChangeRequest(context: Context, repository: ClientRepository, scope: CoroutineScope) {
        val url = "https://dev.scribblychat.com/callbook/uploadChanges"
        val instanceNumber = MiscHelpers.getInstanceNumber(context)

        val key = repository.getClientKey(instanceNumber)
        val uploadRequestJson: String

        if (key == null) {
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: CLIENT KEY IS NULL")
            return
        }

        try {
            withContext(Dispatchers.IO) {

                // Gets first 200 upload logs in order of linkedRowID.
                val uploadLogs = repository.getChunkChangeQTU(200)

                // Retrieves corresponding change logs for each upload log
                val changeLogs = mutableListOf<ChangeLog>()
                for (uploadLog in uploadLogs) {
                    // If no associated changeLog, will fail out into try-catch
                    val changeLog = repository.getChangeLog(uploadLog.linkedRowID)
                        ?: throw Exception("linkedRowID = ${uploadLog.linkedRowID} has no associated ChangeLog!")

                    changeLogs.add(changeLog)
                }

                uploadRequestJson = UploadChangeRequest(instanceNumber, key, changeLogs).toJson()
            }

            Timber.i("${MiscHelpers.DEBUG_LOG_TAG} UPLOAD_CHANGE REQUEST JSON: $uploadRequestJson")

            val stringRequest = UploadChangeRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = uploadRequestJson,
                context = context,
                repository = repository,
                scope = scope
            )

            // Adds entire string request to request queue
            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: Exception) {
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * TODO: Find better control follow for adding to UploadAnalyzedQueue.
     *
     * Sends a post request containing the AnalyzedNumbers to the server.
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    suspend fun uploadAnalyzedRequest(context: Context, repository: ClientRepository, scope: CoroutineScope) {
        val url = "https://dev.scribblychat.com/callbook/uploadAnalyzedNumbers"
        val instanceNumber = MiscHelpers.getInstanceNumber(context)

        val key = repository.getClientKey(instanceNumber)
        val uploadRequestJson: String

        if (key == null) {
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: CLIENT KEY IS NULL")
            return
        }

        try {
            withContext(Dispatchers.IO) {

                // Gets first 200 upload logs in order of linkedRowID.
                val uploadLogs = repository.getChunkAnalyzedQTU(200)

                // Retrieves corresponding AnalyzedNumber for each upload log
                val analyzedNumbers = mutableListOf<AnalyzedNumber>()
                for (uploadLog in uploadLogs) {
                    // If no associated changeLog, will fail out into try-catch
                    val analyzedNumber = repository.getAnalyzedNum(uploadLog.linkedRowID)
                        ?: throw Exception("linkedRowID = ${uploadLog.linkedRowID} has no associated AnalyzedNumber!")

                    analyzedNumbers.add(analyzedNumber)
                }

                uploadRequestJson = UploadAnalyzedRequest(instanceNumber, key, analyzedNumbers).toJson()
            }

            Timber.i("${MiscHelpers.DEBUG_LOG_TAG} UPLOAD_ANALYZED REQUEST JSON: $uploadRequestJson")

            val stringRequest = UploadAnalyzedRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = uploadRequestJson,
                context = context,
                repository = repository,
                scope = scope
            )

            // Adds entire string request to request queue
            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: Exception) {
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ${e.message}")
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
            tokenRequestJson = TokenRequest(instanceNumber, key, token).toJson()
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