package com.telefender.phone.permissions

import android.Manifest.permission.*
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import androidx.core.app.ActivityCompat
import com.telefender.phone.helpers.TeleHelpers
import timber.log.Timber


object Permissions {

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
            READ_PHONE_STATE,
            READ_CALL_LOG,
            READ_CONTACTS,
        )

        if (!hasPermissions(context, permissions)) {
            ActivityCompat.requestPermissions(activity, permissions, requestCode)
        } 
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

    fun hasPhoneStatePermissions(context: Context) : Boolean {
        return isDefaultDialer(context)
            || hasPermissions(context, arrayOf(READ_PHONE_STATE))
    }

    fun hasLogPermissions(context: Context) : Boolean {
        return isDefaultDialer(context)
            || hasPermissions(context, arrayOf(READ_CALL_LOG))
    }

    fun hasContactPermissions(context: Context) : Boolean {
        return isDefaultDialer(context)
            || hasPermissions(context, arrayOf(READ_CONTACTS))
    }
}