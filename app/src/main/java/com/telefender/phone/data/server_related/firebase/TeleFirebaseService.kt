package com.telefender.phone.data.server_related.firebase

import android.annotation.SuppressLint
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import timber.log.Timber

/**
 * TODO: We actually might not use push notifications due to the limitations of receiving a push
 *  notification message in the background. However, we are keeping this stuff here in case we ever
 *  do need it.
 */
class TeleFirebaseService : FirebaseMessagingService() {

    /**
     * Receives Firebase messages when the app is in the foreground.
     *
     * NOTE: Firebase messages received when the app is in the background are passed as extras in
     * an Activity intent when the user clicks on the push notification (in the notification drawer).
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.e("$DBL: Firebase message received - ${remoteMessage.data}")

        val data : Map<String, String> = remoteMessage.data
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
        Timber.e("$DBL: Refreshed Firebase token: $token")

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        //sendRegistrationToServer(token)
    }
}