package com.dododial.phone.database.background_tasks.firebase

import android.Manifest
import android.annotation.SuppressLint
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.work.WorkInfo
import com.dododial.phone.App
import com.dododial.phone.database.ClientRepository
import com.dododial.phone.database.MiscHelpers
import com.dododial.phone.database.background_tasks.WorkerStates
import com.example.actualfinaldatabase.permissions.PermissionsRequester
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.ktx.remoteMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class DodoFirebaseService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Timber.d("From: ${remoteMessage.from}")

        // TODO: Step 3.5 check messages for data
        // Check if the message contains a data payload.
        remoteMessage.data.let {
            Timber.d("Message data payload: %s", remoteMessage.data)
        }

    }

    /**
     * Called if the FCM registration token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the
     * FCM registration token is initially generated so this is where you would retrieve the token.
     */
    @SuppressLint("MissingPermission")
    override fun onNewToken(token: String) {
        Timber.d("DODODEBUG: Refreshed token: $token")

        runBlocking {
            (application as App).applicationScope.launch {
                while (applicationContext.getSystemService(TelecomManager::class.java).defaultDialerPackage != applicationContext.packageName
                    || !PermissionsRequester.hasPermissions(applicationContext, arrayOf(Manifest.permission.READ_CALL_LOG))) {
                    delay(500)
                    Timber.i("DODODEBUG: INSIDE NEW TOKEN COROUTINE | HAS CALL LOG PERMISSION: %s", PermissionsRequester.hasPermissions(applicationContext, arrayOf(
                        Manifest.permission.READ_CALL_LOG)))
                }

                WorkerStates.setupState = WorkInfo.State.RUNNING
                while(WorkerStates.setupState != WorkInfo.State.SUCCEEDED) {
                    delay(500)
                    Timber.i("DODODEBUG: SETUP WORK STATE: %s", WorkerStates.setupState.toString())
                }
                WorkerStates.setupState = null


                val tMgr = applicationContext.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                val instanceNumber : String = MiscHelpers.cleanNumber(tMgr.line1Number)!!

                val repository : ClientRepository? = (application as App).repository
                repository?.updateKey(instanceNumber, null, token)
            }
        }
        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        //sendRegistrationToServer(token)
    }
}