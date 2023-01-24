package com.telefender.phone.data.server_related.firebase

import android.annotation.SuppressLint
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.telefender.phone.App
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.launch
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
        Timber.d("${TeleHelpers.DEBUG_LOG_TAG}: Refreshed Firebase token: $token")

        /*
        Since initFirebase() is called after user setup and database initialization, no need to
        check here before inserting into database.
         */
        (applicationContext as App).applicationScope.launch {
            val repository = (applicationContext as App).repository
            repository.updateStoredMap(firebaseToken = token)


        }

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        //sendRegistrationToServer(token)
    }
}