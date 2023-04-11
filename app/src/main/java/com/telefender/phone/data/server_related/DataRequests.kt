package com.telefender.phone.data.server_related

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Request
import com.telefender.phone.data.server_related.json_classes.*
import com.telefender.phone.data.server_related.request_generators.*
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.data.tele_database.entities.AnalyzedNumber
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import timber.log.Timber


/**
 * TODO: We can probably combine UploadAnalyzedRequestGen and UploadChangeRequestGen into one file
 * TODO: Put in upload call logs request (probably won't be used often, but just in case).
 */
object DataRequests {

    private const val retryAmount = 3

    /**
     * TODO: Maybe we should put in error counter like the upload requests.
     */
    suspend fun downloadDataRequest(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope
    ) {
        val url = "https://dev.scribblychat.com/callbook/downloadChanges"
        val instanceNumber = TeleHelpers.getUserNumberStored(context)
        val key = repository.getClientKey()
        val lastServerRowID = repository.getLastServerRowID()

        if (instanceNumber == null || key == null) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: " +
                "VOLLEY: ERROR - INSTANCE NUMBER = $instanceNumber | CLIENT KEY = $key")

            WorkStates.setState(WorkType.DOWNLOAD_POST, WorkInfo.State.FAILED)
            return
        }

        val downloadRequestJson = DownloadRequest(instanceNumber, key, lastServerRowID).toJson()

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
     * TODO: Double check that retry strategy is correct
     * TODO: Can returning list with dao give null?
     *
     * Sends a post request containing the ChangeLogs to the server.
     */
    suspend fun uploadChangeRequest(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        errorCount: Int
    ) {
        if (errorCount == retryAmount) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: UPLOAD_CHANGE RETRY MAX")
            WorkStates.setState(WorkType.UPLOAD_CHANGE_POST, WorkInfo.State.FAILED)
            return
        }

        val url = "https://dev.scribblychat.com/callbook/uploadChanges"
        val instanceNumber = TeleHelpers.getUserNumberStored(context)
        val key = repository.getClientKey()
        val uploadRequestJson: String

        if (instanceNumber == null || key == null) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: " +
                "VOLLEY: ERROR - INSTANCE NUMBER = $instanceNumber | CLIENT KEY = $key")

            WorkStates.setState(WorkType.UPLOAD_CHANGE_POST, WorkInfo.State.FAILED)
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

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG} UPLOAD_CHANGE REQUEST JSON: $uploadRequestJson")

            val stringRequest = UploadChangeRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = uploadRequestJson,
                context = context,
                repository = repository,
                scope = scope,
                errorCount = errorCount
            )

            // Adds entire string request to request queue
            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: Exception) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * TODO: Find better control follow for adding to UploadAnalyzedQueue.
     *
     * Sends a post request containing the AnalyzedNumbers to the server.
     */
    suspend fun uploadAnalyzedRequest(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        errorCount: Int
    ) {
        if (errorCount == retryAmount) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: UPLOAD_ANALYZED RETRY MAX")
            WorkStates.setState(WorkType.UPLOAD_ANALYZED_POST, WorkInfo.State.FAILED)
            return
        }

        val url = "https://dev.scribblychat.com/callbook/uploadAnalyzedNumbers"
        val instanceNumber = TeleHelpers.getUserNumberStored(context)
        val key = repository.getClientKey()
        val uploadRequestJson: String

        if (instanceNumber == null || key == null) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: " +
                "VOLLEY: ERROR - INSTANCE NUMBER = $instanceNumber | CLIENT KEY = $key")

            WorkStates.setState(WorkType.UPLOAD_ANALYZED_POST, WorkInfo.State.FAILED)
            return
        }

        try {
            withContext(Dispatchers.IO) {

                // Gets first chunk of upload logs in order of linkedRowID.
                val uploadLogs = repository.getChunkAnalyzedQTU(70)

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

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG} UPLOAD_ANALYZED_POST REQUEST JSON: $uploadRequestJson")

            val stringRequest = UploadAnalyzedRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = uploadRequestJson,
                context = context,
                repository = repository,
                scope = scope,
                errorCount = errorCount
            )

            // Adds entire string request to request queue
            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: Exception) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * TODO: Double check that retry strategy is correct
     *
     * Sends a post request containing the ErrorLogs to the server.
     */
    suspend fun uploadErrorRequest(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        errorCount: Int
    ) {
        if (errorCount == retryAmount) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: UPLOAD_ERROR RETRY MAX")
            WorkStates.setState(WorkType.UPLOAD_ERROR_POST, WorkInfo.State.FAILED)
            return
        }

        val url = "https://dev.scribblychat.com/callbook/uploadErrorLog"
        val instanceNumber = TeleHelpers.getUserNumberStored(context)
        val key = repository.getClientKey()
        val uploadRequestJson: String

        if (instanceNumber == null || key == null) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: " +
                "VOLLEY: ERROR - INSTANCE NUMBER = $instanceNumber | CLIENT KEY = $key")

            WorkStates.setState(WorkType.UPLOAD_ERROR_POST, WorkInfo.State.FAILED)
            return
        }

        try {
            withContext(Dispatchers.IO) {
                // Gets first chunk of ErrorLogs in order of linkedRowID.
                val errorLogs = repository.getChunkErrorLog(100)
                uploadRequestJson = UploadErrorRequest(instanceNumber, key, errorLogs).toJson()
            }

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG} UPLOAD_CHANGE REQUEST JSON: $uploadRequestJson")

            val stringRequest = UploadErrorRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = uploadRequestJson,
                context = context,
                repository = repository,
                scope = scope,
                errorCount = errorCount
            )

            // Adds entire string request to request queue
            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: Exception) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Post request to ask server for SMS verification of given number.
     */
    suspend fun smsVerifyRequest(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        number: String
    ) {
        val url = "https://dev.scribblychat.com/callbook/reqNumberVerify"
        val instanceNumber = TeleHelpers.getUserNumberStored(context)
        val key = repository.getClientKey()

        if (instanceNumber == null || key == null) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: " +
                "VOLLEY: ERROR - INSTANCE NUMBER = $instanceNumber | CLIENT KEY = $key")

            WorkStates.setState(WorkType.SMS_VERIFY_POST, WorkInfo.State.FAILED)
            return
        }

        val smsVerifyRequestJson = SMSVerifyRequest(instanceNumber, key, number).toJson()

        try {
            val stringRequest = SMSVerifyRequestGen.create(
                method = Request.Method.POST,
                url = url,
                requestJson = smsVerifyRequestJson,
                context = context,
                repository = repository,
                scope = scope,
                number = number
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
    suspend fun tokenPostRequest(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        token: String
    ) {
        val url = ""
        val instanceNumber = TeleHelpers.getUserNumberStored(context)
        val key = repository.getClientKey()

        if (instanceNumber == null || key == null) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: " +
                "VOLLEY: ERROR - INSTANCE NUMBER = $instanceNumber | CLIENT KEY = $key")

            WorkStates.setState(WorkType.UPLOAD_TOKEN, WorkInfo.State.FAILED)
            return
        }

        val tokenRequestJson = TokenRequest(instanceNumber, key, token).toJson()

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