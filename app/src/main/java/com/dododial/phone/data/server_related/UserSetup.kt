package com.dododial.phone.data.server_related

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.telephony.TelephonyManager
import androidx.work.WorkInfo
import com.android.volley.AuthFailureError
import com.android.volley.Response
import com.android.volley.VolleyLog
import com.android.volley.toolbox.StringRequest
import com.dododial.phone.data.dodo_database.ClientRepository
import com.dododial.phone.data.dodo_database.MiscHelpers
import com.dododial.phone.data.dodo_database.background_tasks.WorkerStates
import com.dododial.phone.data.server_related.RequestHelpers.defaultRequestToJson
import com.dododial.phone.data.server_related.ResponseHelpers.jsonToDefaultResponse
import com.dododial.phone.data.server_related.ResponseHelpers.jsonToKeyResponse
import com.dododial.phone.data.server_related.ResponseHelpers.jsonToSessionResponse
import com.dododial.phone.data.dodo_database.entities.KeyStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import timber.log.Timber
import java.io.UnsupportedEncodingException

object UserSetup {

    // TODO do Volley check for not being able to connect to server (e.g., 404)
    @SuppressLint("MissingPermission", "HardwareIds")
    fun initialPostRequest(context : Context, repository: ClientRepository, scope: CoroutineScope) {
        val tMgr = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val instanceNumber : String = MiscHelpers.cleanNumber(tMgr.line1Number)!!

        val installationRequest = DefaultRequest(instanceNumber)
        var installationRequestJson : String = defaultRequestToJson(installationRequest)

        val url = "https://dev.scribblychat.com/callbook/requestInstallation" // TODO finalize?

        try {
            val stringRequest: StringRequest =

                object : StringRequest(
                    Method.POST, url,

                    Response.Listener { response ->
                        Timber.i("VOLLEY: FIRST RESPONSE %s", response!!)
                        val sessionResponse : DefaultResponse? =
                            try {
                                jsonToSessionResponse(response)
                            } catch (e: Exception) {
                                try {
                                    jsonToDefaultResponse(response)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        
                        if (sessionResponse != null && sessionResponse.status == "ok" && sessionResponse is SessionResponse) {
                            /**
                             * Create KeyStorage row containing instance number and sessionID
                             */
                            runBlocking {
                                scope.launch {
                                    val keyStorage = KeyStorage(instanceNumber, sessionResponse.sessionID!!, null, null)
                                    repository.insertKey(keyStorage)

                                    verifyPostRequest(context, repository, scope)
                                }
                            }

                        } else {
                            WorkerStates.setupState = WorkInfo.State.FAILED

                            if (sessionResponse != null) {
                                Timber.i("DODODEBUG: VOLLEY: ERROR WHEN REQUEST INSTALLATION: %s", sessionResponse.error)
                            } else {
                                Timber.i("DODODEBUG: VOLLEY: ERROR WHEN REQUEST INSTALLATION: SESSION RESPONSE IS NULL")
                            }
                        }
                    }, Response.ErrorListener { error ->
                        if (error.toString() != "null") {
                            Timber.e("VOLLEY %s", error.toString())
                            WorkerStates.setupState = WorkInfo.State.FAILED
                        }
                        })
                {
                    @SuppressLint("HardwareIds")
                    @Throws(AuthFailureError::class)
                    override fun getBody(): ByteArray? {
                        try {
                            return installationRequestJson.toByteArray(charset("utf-8"))
                        } catch (uee: UnsupportedEncodingException) {
                            VolleyLog.wtf(
                                "Unsupported Encoding while trying to get the bytes of %s using %s",
                                installationRequestJson,
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

    @SuppressLint("MissingPermission", "HardwareIds")
    fun verifyPostRequest(context : Context, repository: ClientRepository, scope: CoroutineScope) {
        val tMgr = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val instanceNumber = MiscHelpers.cleanNumber(tMgr.line1Number)!!

        // TODO integrate OTP with notifications, for now, it's hardcoded
        val OTP = 111111
        val url = "https://dev.scribblychat.com/callbook/verifyInstallation"  // TODO finalize?
        runBlocking {
            scope.launch {
                val sessionID = repository.getSessionID(instanceNumber)

                val verifyRequest = VerifyRequest(instanceNumber, sessionID, OTP)
                var verifyRequestJson : String? = RequestHelpers.verifyRequestToJson(verifyRequest)

                try {
                    val stringRequest: StringRequest =

                        object : StringRequest(
                            Method.POST, url,

                            Response.Listener { response ->


                                Timber.i("VOLLEY %s", response!!)
                                val keyResponse: DefaultResponse? =
                                    try {
                                        jsonToKeyResponse(response)
                                    } catch (e: Exception) {
                                        try {
                                            jsonToDefaultResponse(response)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }

                                if (keyResponse != null && keyResponse.status == "ok" && keyResponse is KeyResponse) {
                                    /**
                                     * Update KeyStorage row with clientKey
                                     */
                                    runBlocking {
                                        scope.launch {
                                            repository.updateKey(instanceNumber, keyResponse.key, null)
                                        }
                                    }

                                    WorkerStates.setupState = WorkInfo.State.SUCCEEDED

                                } else {
                                    WorkerStates.setupState = WorkInfo.State.FAILED

                                    if (keyResponse != null) {
                                        Timber.i("DODODEBUG: VOLLEY: ERROR WHEN VERIFY INSTALLATION: %s", keyResponse?.error)
                                    } else {
                                        Timber.i("DODODEBUG: VOLLEY: ERROR WHEN VERIFY INSTALLATION: SESSION RESPONSE IS NULL")
                                    }
                                }
                            }, Response.ErrorListener { error ->
                                if (error.toString() != "null") {
                                    Timber.e("VOLLEY %s", error.toString())
                                    WorkerStates.setupState = WorkInfo.State.FAILED
                                }
                            }) 
                        {
                            @SuppressLint("HardwareIds")
                            @Throws(AuthFailureError::class)
                            override fun getBody(): ByteArray? {
                                try {
                                    Timber.i(
                                        "DODODEBUG: VerifyRequestJson = %s",
                                        verifyRequestJson ?: "NULL"
                                    )
                                    return verifyRequestJson?.toByteArray(charset("utf-8"))
                                } catch (uee: UnsupportedEncodingException) {
                                    VolleyLog.wtf(
                                        "Unsupported Encoding while trying to get the bytes of %s using %s",
                                        verifyRequestJson,
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
}