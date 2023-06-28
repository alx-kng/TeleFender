package com.telefender.phone.permissions

import android.Manifest.permission.*
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import timber.log.Timber

enum class PermissionRequestType(val requestCode: Int) {
    CORE_ALT(1), PHONE_STATE(2), DO_NOT_DISTURB(3),
    NOTIFICATIONS(4)
}

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
     * TODO: Currently do two sets of permissions for core-alt in the case of Android 9. Try to
     *  find a better solution (for more info look at comment on requestDefaultDialer() in
     *  MainActivity).
     *
     * Requests core permissions (e.g., READ_CONTACTS, READ_CALL_LOG, READ_PHONE_STATE). Used when
     * the default dialer permissions aren't granted. Request code is used in
     * onRequestPermissionsResult() so that you know which permission the result is for.
     */
    fun coreAltPermissions(activity: Activity) {
        Timber.i("$DBL: coreAltPermissions() called")

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                phoneStatePermission,
                READ_CALL_LOG,
                READ_CONTACTS,
            )
        }  else {
            arrayOf(
                phoneStatePermission,
                READ_CALL_LOG,
                READ_CONTACTS,
                WRITE_CONTACTS,
                READ_PHONE_NUMBERS,
                CALL_PHONE
            )
        }

        if (!hasPermissions(activity, permissions)) {
            ActivityCompat.requestPermissions(
                activity,
                permissions,
                PermissionRequestType.CORE_ALT.requestCode
            )
        } 
    }

    fun hasLogPermissions(context: Context) : Boolean {
        return isDefaultDialer(context)
            || hasPermissions(context, arrayOf(READ_CALL_LOG))
    }

    fun hasContactPermissions(context: Context) : Boolean {
        return isDefaultDialer(context)
            || hasPermissions(context, arrayOf(READ_CONTACTS))
    }

    fun phoneStatePermissions(activity: Activity) {
        Timber.i("$DBL: phoneStatePermissions() called")

        if (!hasPermissions(activity, arrayOf(phoneStatePermission))) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(phoneStatePermission),
                PermissionRequestType.PHONE_STATE.requestCode
            )
        }
    }

    /**
     * Unlike the other permissions, we need to check if the app is both the default dialer and has
     * the phone state permission. This is due to a bug in Android that prevents the default dialer
     * from immediately receiving the READ_PHONE_NUMBERS permission (SDK > 29 = Android 10).
     */
    fun hasPhoneStatePermissions(context: Context) : Boolean {
        return isDefaultDialer(context)
            && hasPermissions(context, arrayOf(phoneStatePermission))
    }

    /**
     * Requests Do Not Disturb permissions. Technically not a normal permission request; however,
     * we still classify Do Not Disturb and its requestCode in PermissionType.
     */
    fun doNotDisturbPermission(activity: Activity) {
        val notificationManager = activity
            .getSystemService(AppCompatActivity.NOTIFICATION_SERVICE)
            as NotificationManager

        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            activity.startActivityForResult(intent, PermissionRequestType.DO_NOT_DISTURB.requestCode)
        }
    }

    /**
     * Checks if app has Do Not Disturb permission, which allows us to set the ringer mode to
     * silent OR vibrate OR normal. Note that this permission is needed FOR ANY ringer mode change.
     */
    fun hasDoNotDisturbPermission(context: Context) : Boolean {
        // Use applicationContext to access notification manager.
        val notificationManager = context
            .applicationContext
            .getSystemService(AppCompatActivity.NOTIFICATION_SERVICE)
            as NotificationManager

        return notificationManager.isNotificationPolicyAccessGranted
    }

    /**
     * Requests the POST_NOTIFICATIONS permission if the app doesn't currently have it and needs it.
     */
    fun notificationPermission(context: Context, activity: Activity) {
        if (!hasNotificationPermission(context)) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(POST_NOTIFICATIONS),
                PermissionRequestType.NOTIFICATIONS.requestCode
            )
        }
    }

    /**
     * Checks if the app can post notifications. When the SDK < 33 (Android 13), any app can
     * post notifications, but starting from Android 13, apps need to request the POST_NOTIFICATIONS
     * permission first. However, if the app is the default dialer, the app doesn't need to request
     * the permissions (regardless of build version).
     */
    fun hasNotificationPermission(context: Context) : Boolean {
        return isDefaultDialer(context)
            || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            || hasPermissions(context, arrayOf(POST_NOTIFICATIONS))
    }
}