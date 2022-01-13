package com.dododial.phone.database.background_tasks.server_related

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.volley.*
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.dododial.phone.database.ClientRepository
import com.dododial.phone.database.MiscHelpers
import com.dododial.phone.database.background_tasks.server_related.RequestHelpers.downloadRequestToJson
import com.dododial.phone.database.background_tasks.server_related.RequestHelpers.uploadRequestToJson
import com.dododial.phone.database.background_tasks.server_related.ResponseHelpers.jsonToChangeResponse
import com.dododial.phone.database.background_tasks.server_related.ResponseHelpers.jsonToDefaultResponse
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
        val instanceNumber = MiscHelpers.cleanNumber(tMgr.line1Number)!!

        var downloadRequestJson : String? = null
        val url = " " // TODO finalize?

        // TODO does runblocking block the whole request thread
        /**
         * Used to create download request json body, as we need the instanceNumber, key, and last
         * server changeID to communicate with server correctly
         */
        runBlocking {
            scope.launch {
                val key = repository.getClientKey(instanceNumber)
                val lastChangeID = repository.getLastChangeID()

                val downloadRequest = DownloadRequest(instanceNumber, key, lastChangeID)
                downloadRequestJson = downloadRequestToJson(downloadRequest)
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
                        val changeResponse : ChangeResponse? = jsonToChangeResponse(response)

                        /**
                         * Retrieves ChangeResponse object containing (status, error, List<ChangeLogs>)
                         * and inserts each change log into our database using changeFromServer()
                         * defined in ChangeAgentDao.
                         */
                        runBlocking {
                            scope.launch {
                                /**
                                 * Guarantees that response has the right status before trying to
                                 * iterate through change logs stored in it.
                                 */
                                if (changeResponse != null && changeResponse.status == "OK") {
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
                                } else {
                                    Log.i("DODODEBUG: VOLLEY: ", "ERROR WHEN DOWNLOAD: " + changeResponse?.error)
                                }
                            }
                        }
                    }, Response.ErrorListener { error -> Log.e("VOLLEY", error.toString()) })
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
        val instanceNumber = MiscHelpers.cleanNumber(tMgr.line1Number)!!

        var uploadLogs : List<QueueToUpload>? = null
        var uploadRequestJson : String? = null
        val url = " " // TODO finalize

        runBlocking {
            scope.launch {
                val key = repository.getClientKey(instanceNumber)

                val temp = mutableListOf<ChangeLog>()
                uploadLogs = repository.getAllQTU()
                if (uploadLogs != null) {
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
            }
        }


        try {
            val stringRequest: StringRequest = object : StringRequest(
                Method.POST, url,
                Response.Listener { response ->
                    Log.i("VOLLEY", response!!)
                    val defaultResponse: DefaultResponse? = jsonToDefaultResponse(response)

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
                            if (defaultResponse != null && defaultResponse.status == "OK" && uploadLogs != null) {
                                for (uploadLog in uploadLogs!!) {
                                    repository.deleteQTU(uploadLog.changeID)
                                }
                            } else {
                                if (uploadLogs == null) {
                                    Log.i("DODODEBUG: VOLLEY: ", "uploadLogs was null")
                                } else {
                                    Log.i("DODODEBUG: VOLLEY: ", "ERROR WHEN UPLOAD: " + defaultResponse?.error)
                                }
                            }
                        }
                    }
                }, Response.ErrorListener { error -> Log.e("VOLLEY", error.toString()) })
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