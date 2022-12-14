package com.telefender.phone.data.server_related

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.telephony.TelephonyManager
import androidx.work.WorkInfo
import com.android.volley.*
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.telefender.phone.data.server_related.RequestHelpers.downloadRequestToJson
import com.telefender.phone.data.server_related.RequestHelpers.uploadRequestToJson
import com.telefender.phone.data.server_related.ResponseHelpers.jsonToChangeResponse
import com.telefender.phone.data.server_related.ResponseHelpers.jsonToDefaultResponse
import com.telefender.phone.data.server_related.ResponseHelpers.jsonToUploadResponse
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkerStates
import com.telefender.phone.data.tele_database.background_tasks.WorkerType
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.data.tele_database.entities.QueueToUpload
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.*
import org.json.JSONException
import timber.log.Timber
import java.io.UnsupportedEncodingException

object ServerHelpers {

    
    @SuppressLint("MissingPermission")
    suspend fun downloadPostRequest(context: Context, repository: ClientRepository, scope: CoroutineScope) {
        val tMgr = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val instanceNumber = MiscHelpers.cleanNumber(tMgr.line1Number)!!

        var downloadRequestJson: String? = null
        val url = "https://dev.scribblychat.com/callbook/downloadChanges"

        /**
         * Used to create download request json body, as we need the instanceNumber, key, and last
         * server changeID to communicate with server correctly
         */
        withContext(Dispatchers.IO) {
            val key = repository.getClientKey(instanceNumber)
            val lastChangeID = repository.getLastChangeID()

            if (key != null) {
                val downloadRequest = DownloadRequest(instanceNumber, key, lastChangeID)
                downloadRequestJson = downloadRequestToJson(downloadRequest)
            } else {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG} VOLLEY key was null")
            }

            try {
                val stringRequest: StringRequest =

                    object : StringRequest(
                        Method.POST, url,
                        /**
                         * A lambda that is called when the response is received from the server after
                         * we send the POST request
                         */
                        Response.Listener { response ->
                            Timber.i("VOLLEY %s", response!!)

                            /**
                             * Retrieves ChangeResponse object containing (status, error, List<ChangeLogs>)
                             * and inserts each change log into our database using changeFromServer()
                             * defined in ChangeAgentDao.
                             */
                            val changeResponse: DefaultResponse? =
                                try {
                                    jsonToChangeResponse(response)
                                } catch (e: Exception) {
                                    try {
                                        jsonToDefaultResponse(response)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }

                            /**
                             * Guarantees that response has the right status before trying to
                             * iterate through change logs stored in it.
                             */
                            if (changeResponse != null && changeResponse.status == "ok" && (changeResponse is ChangeResponse)) {

                                runBlocking {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            for (changeLog in changeResponse.changeLogs) {
                                                /*
                                                Makes sure that serverChangeID is not null since it is needed
                                                future download requests
                                                 */
                                                if (changeLog.serverChangeID != null) {

                                                    // Inserts each change log into right tables
                                                    repository.changeFromServer(
                                                        changeLog.changeID,
                                                        changeLog.instanceNumber,
                                                        changeLog.changeTime,
                                                        changeLog.type,
                                                        changeLog.CID,
                                                        changeLog.oldNumber,
                                                        changeLog.number,
                                                        changeLog.parentNumber,
                                                        changeLog.trustability,
                                                        changeLog.counterValue,
                                                        changeLog.serverChangeID
                                                    )

                                                } else {
                                                    Timber.i(
                                                        "${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN DOWNLOAD: serverChangeID is null"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (changeResponse.changeLogs.isNotEmpty()) {
                                    scope.launch {
                                        downloadPostRequest(context, repository, scope)
                                    }
                                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: MORE TO DOWNLOAD")
                                } else {
                                    WorkerStates.setState(WorkerType.DOWNLOAD_POST, WorkInfo.State.SUCCEEDED)
                                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: All DOWNLOADS COMPLETE")
                                }
                            } else {
                                WorkerStates.setState(WorkerType.DOWNLOAD_POST, WorkInfo.State.FAILED)
                                if (changeResponse != null) {
                                    Timber.i(
                                        "${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN DOWNLOAD: %s",
                                        changeResponse.error
                                    )
                                } else {
                                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN DOWNLOAD: CHANGE RESPONSE IS NULL")
                                }
                            }
                        }, Response.ErrorListener { error ->
                            if (error.toString() != "null") {
                                Timber.e("VOLLEY %s", error.toString())
                                WorkerStates.setState(WorkerType.DOWNLOAD_POST, WorkInfo.State.FAILED)
                            }
                        }) {

                        @SuppressLint("HardwareIds")
                        @Throws(AuthFailureError::class)
                        override fun getBody(): ByteArray? {
                            try {
                                return downloadRequestJson?.toByteArray(charset("utf-8"))
                            } catch (uee: UnsupportedEncodingException) {
                                VolleyLog.wtf(
                                    "Unsupported Encoding while trying to get the bytes of %s using %s",
                                    downloadRequestJson,
                                    "utf-8"
                                )
                                return null
                            }
                        }
                        override fun getBodyContentType(): String {
                            return "application/json; charset=utf-8"
                        }
                    }

                /**
                 * Adds entire string request to request queue
                 */
                RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Sends post request containing the string requestString to the server at url.
     * Returns whether or not post was successful
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    suspend fun uploadPostRequest(context: Context, repository: ClientRepository, scope: CoroutineScope) {
        val tMgr: TelephonyManager = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val instanceNumber = MiscHelpers.cleanNumber(tMgr.line1Number)!!

        var uploadLogs: List<QueueToUpload>?
        var uploadRequestJson: String? = null
        val url = "https://dev.scribblychat.com/callbook/uploadChanges"

        withContext(Dispatchers.IO) {
            val key = repository.getClientKey(instanceNumber)
            if (key != null) {
                val temp = mutableListOf<ChangeLog>()

                /**
                 * Gets first 200 upload logs in order of rowID
                 */
                uploadLogs = repository.getChunkQTUByRowID()
                if (uploadLogs != null) {
                    /**
                     * Retrieves corresponding change logs for each upload log
                     */
                    for (uploadLog in uploadLogs!!) {
                        var changeLog = repository.getChangeLogRow(uploadLog.changeID)
                        temp.add(changeLog)
                    }
                    val changeLogs = temp.toList()

                    val uploadRequest = UploadRequest(instanceNumber, key, changeLogs)
                    uploadRequestJson = uploadRequestToJson(uploadRequest)
                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG} UPLOAD REQUEST JSON %s ", uploadRequestJson)
                } else {
                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: RETRIEVED UPlOAD LOGS ARE NULL")
                }
            } else {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: CLIENT KEY IS NULL")
            }

            try {
                val stringRequest: StringRequest = object : StringRequest(
                    Method.POST, url,
                    Response.Listener { response ->
                        Timber.i("VOLLEY %s", response!!)
                        val uploadResponse: DefaultResponse? =
                            try {
                                jsonToUploadResponse(response)
                            } catch (e: Exception) {
                                try {
                                    jsonToDefaultResponse(response)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        /**
                         * If all upload logs are uploaded to the server successfully, we will delete
                         * the corresponding upload logs from the QueueToUpload table
                         */
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                /**
                                 * Guarantees that response has the right status before trying to
                                 * iterate through upload logs. Also upload logs shouldn't be null.
                                 */
                                if (uploadResponse != null && uploadResponse is UploadResponse) {
                                    /**
                                     * coroutineScope{} ensures that delete finishes before checking
                                     * if there are more Upload logs.
                                     */
                                    coroutineScope {
                                        when (uploadResponse.status) {
                                            "ok" -> {
                                                repository.deleteUploadInclusive(uploadResponse.lastUploadRow)
                                            }
                                            else -> {
                                                Timber.i(
                                                    "${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: PARTIALLY UPLOADED WITH ERROR: %s",
                                                    uploadResponse.error
                                                )
                                                repository.deleteUploadExclusive(uploadResponse.lastUploadRow)
                                            }

                                        }
                                    }

                                    if (repository.hasQTUs()) {
                                        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: MORE TO UPLOAD")
                                        uploadPostRequest(context, repository, scope)
                                    } else {
                                        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: All UPLOAD COMPLETE")
                                        WorkerStates.setState(WorkerType.UPLOAD_POST, WorkInfo.State.SUCCEEDED)
                                    }
                                } else {
                                    WorkerStates.setState(WorkerType.UPLOAD_POST, WorkInfo.State.FAILED)

                                    if (uploadResponse != null) {
                                        Timber.i(
                                            "${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN UPLOAD: %s",
                                            uploadResponse.error
                                        )
                                    } else {
                                        Timber.i(
                                            "${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN UPLOAD: UPLOAD RESPONSE IS NULL"
                                        )
                                    }
                                }
                            }
                        }
                    }, Response.ErrorListener { error ->
                        if (error.toString() != "null") {
                            Timber.e("VOLLEY %s", error.toString())
                            WorkerStates.setState(WorkerType.UPLOAD_POST, WorkInfo.State.FAILED)
                        }
                    }) {
                    @Throws(AuthFailureError::class)
                    override fun getBody(): ByteArray? {
                        try {
                            return uploadRequestJson?.toByteArray(charset("utf-8"))
                        } catch (uee: UnsupportedEncodingException) {
                            VolleyLog.wtf(
                                "Unsupported Encoding while trying to get the bytes of %s using %s",
                                uploadRequestJson,
                                "utf-8"
                            )
                            return null
                        }
                    }

                    override fun getBodyContentType(): String {
                        return "application/json; charset=utf-8"
                    }
                }

                RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Could be used with the suggested token checkup every month, or used whenever token updates in general as it is incredibly important
     * server has access to reliable tokens for every device
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    suspend fun tokenPostRequest(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        token: String
    ) {
        val tMgr = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val instanceNumber: String = MiscHelpers.cleanNumber(tMgr.line1Number)!!


        val url = "" // TODO unknown

        withContext(Dispatchers.IO) {
            val key : String?
            coroutineScope {
               key = repository.getClientKey(instanceNumber)
            }

            val tokenRequest = TokenRequest(instanceNumber, key!!, token) // TODO make sure we are always set up by this point
            var tokenRequestJson: String = RequestHelpers.tokenRequestToJson(tokenRequest)

            try {
                val stringRequest: StringRequest =
                    object : StringRequest(
                        Method.POST, url,
                        Response.Listener { response ->
                            Timber.i("TOKEN RESPONSE: %s", response!!)
                            val defaultResponse: DefaultResponse? = jsonToDefaultResponse(response)

                            if (defaultResponse != null && defaultResponse.status == "ok") {
                                WorkerStates.setState(WorkerType.UPLOAD_TOKEN, WorkInfo.State.SUCCEEDED)

                            } else {
                                WorkerStates.setState(WorkerType.UPLOAD_TOKEN, WorkInfo.State.FAILED)

                                if (defaultResponse != null) {
                                    Timber.i(
                                        "${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN TOKEN UPLOAD: %s",
                                        defaultResponse.error
                                    )
                                } else {
                                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN TOKEN UPLOAD: SESSION RESPONSE IS NULL")
                                }
                            }
                        }, Response.ErrorListener { error ->
                            if (error.toString() != "null") {
                                Timber.e("VOLLEY %s", error.toString())
                                WorkerStates.setState(WorkerType.UPLOAD_TOKEN, WorkInfo.State.FAILED)
                            }
                        }) {
                        @SuppressLint("HardwareIds")
                        @Throws(AuthFailureError::class)
                        override fun getBody(): ByteArray? {
                            try {
                                return tokenRequestJson.toByteArray(charset("utf-8"))
                            } catch (uee: UnsupportedEncodingException) {
                                VolleyLog.wtf(
                                    "Unsupported Encoding while trying to get the bytes of %s using %s",
                                    tokenRequestJson,
                                    "utf-8"
                                )
                                return null
                            }
                        }
                        override fun getBodyContentType(): String {
                            return "application/json; charset=utf-8"
                        }
                    }

                /**
                 * Adds entire string request to request queue
                 */
                RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)

            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }
}

class RequestQueueSingleton constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: RequestQueueSingleton? = null
        fun getInstance(context: Context) =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RequestQueueSingleton(context).also {
                    INSTANCE = it
                }
            }
    }

    val requestQueue: RequestQueue by lazy {
        // applicationContext is key, it keeps you from leaking the
        // Activity or BroadcastReceiver if someone passes one in.
        Volley.newRequestQueue(context.applicationContext)
    }
    fun <T> addToRequestQueue(req: Request<T>) {
        requestQueue.add(req)
    }
}