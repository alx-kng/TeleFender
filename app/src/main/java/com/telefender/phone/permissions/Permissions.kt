package com.telefender.phone.permissions

import android.Manifest.permission.*
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.app.ActivityCompat
import com.telefender.phone.helpers.TeleHelpers
import timber.log.Timber


/**
 * TODO: Eventually include shouldShowRequestPermissionRationale()
 *
 * TODO: Detect if user pressed "Don't ask again" on default dialer / other permission request.
 */
object Permissions {

    /**
     * READ_PHONE_STATE is used for SDK <= 29 and READ_PHONE_NUMBERS is used for SDK > 29. These
     * permissions are used to get stuff like the user's number.
     */
    private val phoneStatePermission = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        READ_PHONE_STATE
    } else {
        READ_PHONE_NUMBERS
    }

    /**
     * Returns whether or not App has all the permissions in provided permission array
     */
    fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(context, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Checks if app is default dialer by seeing if the default dialer package name is the same as
     * our app's package name.
     */
    fun isDefaultDialer(context: Context) : Boolean {
        return context.getSystemService(TelecomManager::class.java).defaultDialerPackage == context.packageName
    }

    /**
     * TODO: Find out more about this "request code" bullshit.
     *
     * Requests core permissions (e.g., READ_CONTACTS, READ_CALL_LOG, READ_PHONE_STATE). Used when
     * the default dialer permissions aren't granted.
     */
    fun coreAltPermissions(context: Context, activity: Activity) {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: coreAltPermissions() called")

        /*
        Old guy's comment -> The request code is used in ActivityCompat.requestPermissions() and
        returned in the Activity's onRequestPermissionsResult().
         */
        val requestCode = 1
        val permissions = arrayOf(
            phoneStatePermission,
            READ_CALL_LOG,
            READ_CONTACTS,
        )

        if (!hasPermissions(context, permissions)) {
            ActivityCompat.requestPermissions(activity, permissions, requestCode)
        } 
    }

    fun hasPhoneStatePermissions(context: Context) : Boolean {
        return isDefaultDialer(context)
            || hasPermissions(context, arrayOf(phoneStatePermission))
    }

    fun hasLogPermissions(context: Context) : Boolean {
        return isDefaultDialer(context)
            || hasPermissions(context, arrayOf(READ_CALL_LOG))
    }

    fun hasContactPermissions(context: Context) : Boolean {
        return isDefaultDialer(context)
            || hasPermissions(context, arrayOf(READ_CONTACTS))
    }

    /**
     * Requests the POST_NOTIFICATIONS permission if the app doesn't currently have it and needs it.
     */
    fun notificationPermission(context: Context, activity: Activity) {
        if (!hasNotificationPermission(context)) {
            ActivityCompat.requestPermissions(activity, arrayOf(POST_NOTIFICATIONS), 1)
        }
    }

    /**
     * Checks if the app can post notifications. When the SDK < 33 (Android 13), any app can
     * post notifications, but starting from Android 13, apps need to request the POST_NOTIFICATIONS
     * permission first. However, if the app is the default dialer, the app doesn't need to request
     * the permissions (regardless of build version).
     */
    fun hasNotificationPermission(context: Context) : Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            || hasPermissions(context, arrayOf(POST_NOTIFICATIONS))
    }
}