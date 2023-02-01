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
        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: Firebase message received - ${remoteMessage.data}")

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
     * TODO: Maybe we should make Firebase In-app notifications (like we talked about for
     *  debugging)? This way actual notifications don't show. This can be changed through Firebase
     *  website.
     *
     * TODO: Handle uploads / stuff
     *
     * When an UPDATED token is received, uploading / any additional actions happen here.
     *
     * Called if the FCM registration token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the
     * FCM registration token is initially generated so this is where you would retrieve the token.
     *
     * NOTE: Just because we only handle updated tokens here, doesn't mean onNewToken() isn't still
     * called for the initial token. However, since onNewToken() is often called before any of the
     * database checks even happen, it's cumbersome / dangerous to use for the initialization flow.
     */
    @SuppressLint("MissingPermission")
    override fun onNewToken(token: String) {
        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: Refreshed Firebase token: $token")

        /*
        Since initFirebase() is called after user setup and database initialization, no need to
        check here before inserting into database.
         */
//        (applicationContext as App).applicationScope.launch {
//            val repository = (applicationContext as App).repository
//            repository.updateStoredMap(firebaseToken = token)
//        }

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        //sendRegistrationToServer(token)
    }
}