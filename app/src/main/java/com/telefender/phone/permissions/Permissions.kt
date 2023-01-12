package com.telefender.phone.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import androidx.core.app.ActivityCompat
import com.telefender.phone.helpers.TeleHelpers
import timber.log.Timber


object Permissions {
    /**
     * Starts dialer to request permissions in PERMISSIONS array, using hasPermissions as a helper.
     */
    fun multiplePermissions(context: Context, activity: Activity) {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: multiple permissions called")
        // The request code is used in ActivityCompat.requestPermissions()
        // and returned in the Activity's onRequestPermissionsResult()
        val PERMISSION_ALL = 1
        val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ADD_VOICEMAIL
        )

        if (!hasPermissions(context, permissions)) {
            ActivityCompat.requestPermissions(activity, permissions, PERMISSION_ALL)
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

    fun hasLogPermissions(context: Context) : Boolean {
        return context.getSystemService(TelecomManager::class.java).defaultDialerPackage == context.packageName
            || hasPermissions(context, arrayOf(Manifest.permission.READ_CALL_LOG))
    }

    fun hasContactPermissions(context: Context) : Boolean {
        return context.getSystemService(TelecomManager::class.java).defaultDialerPackage == context.packageName
            || hasPermissions(context, arrayOf(Manifest.permission.READ_CONTACTS))
    }
}