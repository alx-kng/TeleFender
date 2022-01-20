package com.dododial.phone.database.background_tasks.server_related

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.telephony.TelephonyManager
import android.util.Log
import androidx.work.WorkInfo
import com.android.volley.*
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.dododial.phone.database.ClientRepository
import com.dododial.phone.database.MiscHelpers
import com.dododial.phone.database.background_tasks.WorkerStates
import com.dododial.phone.database.background_tasks.server_related.RequestHelpers.downloadRequestToJson
import com.dododial.phone.database.background_tasks.server_related.RequestHelpers.uploadRequestToJson
import com.dododial.phone.database.background_tasks.server_related.ResponseHelpers.jsonToChangeResponse
import com.dododial.phone.database.background_tasks.server_related.ResponseHelpers.jsonToDefaultResponse
import com.dododial.phone.database.background_tasks.server_related.ResponseHelpers.jsonToUploadResponse
import com.dododial.phone.database.entities.ChangeLog
import com.dododial.phone.database.entities.QueueToUpload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import java.io.UnsupportedEncodingException

object ServerHelpers {

    @SuppressLint("MissingPermission")
    fun downloadPostRequest(context : Context,  repository: ClientRepository, scope: CoroutineScope) {
        val tMgr = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        //val instanceNumber = MiscHelpers.cleanNumber(tMgr.line1Number)!!
        val instanceNumber = "4436996212"

        var downloadRequestJson : String? = null
        val url = "https://dev.scribblychat.com/callbook/downloadChanges"

        /**
         * Used to create download request json body, as we need the instanceNumber, key, and last
         * server changeID to communicate with server correctly
         */
        runBlocking {
            scope.launch {
                val key = repository.getClientKey(instanceNumber)
                val lastChangeID = repository.getLastChangeID()
                if (key != null) {
                    val downloadRequest = DownloadRequest(instanceNumber, key, lastChangeID)
                    downloadRequestJson = downloadRequestToJson(downloadRequest)
                } else {
                    Log.i("DODODEBUG VOLLEY", "key was null")
                }
            }
        }
        try {
            val stringRequest: StringRequest =

                @SuppressLint("LogNotTimber")
                object : StringRequest(
                    Method.POST, url,
                    /**
                     * A lambda that is called when the response is received from the server after
                     * we send the POST request
                     */
                    Response.Listener { response ->
                        Log.i("VOLLEY", response!!)

                        /**
                         * Retrieves ChangeResponse object containing (status, error, List<ChangeLogs>)
                         * and inserts each change log into our database using changeFromServer()
                         * defined in ChangeAgentDao.
                         */
                        val changeResponse : DefaultResponse? =
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
                        if (changeResponse != null && changeResponse.status == "ok" && changeResponse is ChangeResponse) {

                            runBlocking {
                                scope.launch {
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
                                                changeLog.name,
                                                changeLog.oldNumber,
                                                changeLog.number,
                                                changeLog.parentNumber,
                                                changeLog.trustability,
                                                changeLog.counterValue,
                                                changeLog.serverChangeID
                                            )

                                        } else {
                                            Log.i("DODODEBUG: VOLLEY: ", "ERROR WHEN DOWNLOAD: serverChangeID is null")
                                        }
                                    }
                                }
                            }

                            if (changeResponse.changeLogs.isNotEmpty()) {
                                downloadPostRequest(context, repository, scope)
                                Log.i("DODODEBUG: VOLLEY: ", "MORE TO DOWNLOAD")
                            } else {
                                WorkerStates.downloadPostState = WorkInfo.State.SUCCEEDED
                                Log.i("DODODEBUG: VOLLEY: ", "All DOWNLOADS COMPLETE")
                            }
                        } else {
                            WorkerStates.downloadPostState = WorkInfo.State.FAILED

                            if (changeResponse != null) {
                                Log.i("DODODEBUG: VOLLEY: ", "ERROR WHEN DOWNLOAD: " + changeResponse.error)
                            } else {
                                Log.i("DODODEBUG: VOLLEY: ", "ERROR WHEN DOWNLOAD: CHANGE RESPONSE IS NULL")
                            }
                        }
                    }, Response.ErrorListener { error ->
                        Log.e("VOLLEY", error.toString())
                        WorkerStates.downloadPostState = WorkInfo.State.FAILED
                    })
                {

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

    /**
     * Sends post request containing the string requestString to the server at url.
     * Returns whether or not post was successful
     */
    @SuppressLint("MissingPermission", "HardwareIds", "LogNotTimber")
    fun uploadPostRequest(context: Context, repository: ClientRepository, scope: CoroutineScope) {
        val tMgr : TelephonyManager = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        //val instanceNumber = MiscHelpers.cleanNumber(tMgr.line1Number)!!
        val instanceNumber = "4436996212"

        var uploadLogs : List<QueueToUpload>? = null
        var uploadRequestJson : String? = null
        val url = "https://dev.scribblychat.com/callbook/uploadChanges"

        runBlocking {
            scope.launch {
                val key = repository.getClientKey(instanceNumber)
                if (key != null) {
                    val temp = mutableListOf<ChangeLog>()

                    /**
                     * Gets upload logs in order of rowID
                     */
                    uploadLogs = repository.getAllQTUByRowID()
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
                    } else {
                        Log.i("DODODEBUG: VOLLEY: ", "RETRIEVED UPlOAD LOGS ARE NULL")
                    }
                } else {
                    Log.i("DODODEBUG: VOLLEY: ", "CLIENT KEY IS NULL")
                }

            }
        }


        try {
            val stringRequest: StringRequest = object : StringRequest(
                Method.POST, url,
                Response.Listener { response ->
                    Log.i("VOLLEY", response!!)
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
                    runBlocking {
                        scope.launch {
                            /**
                             * Guarantees that response has the right status before trying to
                             * iterate through upload logs. Also upload logs shouldn't be null.
                             */
                            if (uploadResponse != null && uploadResponse is UploadResponse) {
                                when(uploadResponse.status) {
                                    "ok" -> {
                                        repository.deleteUploadInclusive(uploadResponse.lastUploadRow)
                                        WorkerStates.uploadPostState = WorkInfo.State.SUCCEEDED
                                    }
                                    else -> {
                                        Log.i("DODODEBUG: VOLLEY: ", "PARTIALLY UPLOADED WITH ERROR: " + uploadResponse.error)
                                        repository.deleteUploadExclusive(uploadResponse.lastUploadRow)
                                        WorkerStates.uploadPostState = WorkInfo.State.SUCCEEDED
                                    }

                                }
                            } else {
                                WorkerStates.uploadPostState = WorkInfo.State.FAILED

                                if (uploadResponse != null) {
                                    Log.i("DODODEBUG: VOLLEY: ", "ERROR WHEN UPLOAD: " + uploadResponse.error)
                                } else {
                                    Log.i("DODODEBUG: VOLLEY: ", "ERROR WHEN UPLOAD: UPLOAD RESPONSE IS NULL")
                                }
                            }
                        }
                    }
                }, Response.ErrorListener {
                        error -> Log.e("VOLLEY", error.toString())
                        WorkerStates.uploadPostState = WorkInfo.State.FAILED
                })
            {
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

                override fun getBodyContentType() : String {
                    return "application/json; charset=utf-8"
                }
            }

            RequestQueueSingleton.getInstance(context).addToRequestQueue(stringRequest)
        } catch (e: JSONException) {
            e.printStackTrace()
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