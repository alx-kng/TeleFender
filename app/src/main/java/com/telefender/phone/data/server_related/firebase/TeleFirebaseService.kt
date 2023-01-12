package com.telefender.phone.data.server_related.firebase

import android.annotation.SuppressLint
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.telefender.phone.helpers.TeleHelpers
import timber.log.Timber

// TODO: Fix firebase
class TeleFirebaseService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.d("Firebase message recieved")
        Timber.d(remoteMessage.data.toString())

        val data : Map<String, String> = remoteMessage.data

        when {
            data.containsKey("number") -> {
                // TODO unblock number
                val number = data["number"]
            }
            data.containsKey("tokenRefresh") -> {
                //TODO send current token
            }
            else -> {
                Timber.d("message %s has bad data", remoteMessage.messageId)
            }
        }
    }

    /**
     * Called if the FCM registration token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the
     * FCM registration token is initially generated so this is where you would retrieve the token.
     */
    @SuppressLint("MissingPermission")
    override fun onNewToken(token: String) {
        Timber.d("${TeleHelpers.DEBUG_LOG_TAG}: Refreshed token: $token")

//        runBlocking {
//            (application as App).applicationScope.launch {
//                while (applicationContext.getSystemService(TelecomManager::class.java).defaultDialerPackage != applicationContext.packageName
//                    || !Permissions.hasPermissions(applicationContext, arrayOf(Manifest.permission.READ_CALL_LOG))) {
//                    delay(500)
//                    Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: INSIDE NEW TOKEN COROUTINE | HAS CALL LOG PERMISSION: %s", Permissions.hasPermissions(applicationContext, arrayOf(
//                        Manifest.permission.READ_CALL_LOG)))
//                }
//
//                val tMgr = applicationContext.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
//                val instanceNumber : String = TeleHelpers.normalizedNumber(tMgr.line1Number)!!
//                val repository : ClientRepository? = (application as App).repository
//
//                var isSetup = repository?.hasCredKey(instanceNumber!!) ?: false
//                while (!isSetup) {
//                    delay(500)
//                    Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: INSIDE GET DATABASE COROUTINE. USER SETUP = %s", isSetup)
//
//                    isSetup = repository?.hasCredKey(instanceNumber!!) ?: false
//                }
//
//                repository?.updateKey(instanceNumber, null, token)
//                //TODO CALL onetime TOKENWORKER HERE
//            }
//        }

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        //sendRegistrationToServer(token)
    }
}