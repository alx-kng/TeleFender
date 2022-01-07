package com.example.actualfinaldatabase.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class PermissionRequester {

    companion object {
        fun multiplePermissions(context: Context?, activity: Activity) {
            // The request code is used in ActivityCompat.requestPermissions()
            // and returned in the Activity's onRequestPermissionsResult()
            val PERMISSION_ALL = 1
            val PERMISSIONS = arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_CALL_LOG
            )

            if (!hasPermissions(context, PERMISSIONS)) {
                ActivityCompat.requestPermissions(activity, PERMISSIONS, PERMISSION_ALL)
            }
        }

        fun hasPermissions(context: Context?, permissions: Array<String>): Boolean {
            if (context != null && permissions != null) {
                for (permission in permissions) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            permission!!
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return false
                    }
                }
            }
            return true
        }
    }
}