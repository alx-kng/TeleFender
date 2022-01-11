package com.dododial.phone

import android.Manifest
import android.Manifest.permission.CALL_PHONE
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.TelecomManager
import android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER
import android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.dododial.phone.call_related.CallActivity
import com.dododial.phone.call_related.OngoingCall
import kotlinx.android.synthetic.main.activity_dialer.*
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.RequiresApi
import com.dododial.phone.database.entities.ChangeLog
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_INSERT
import com.dododial.phone.database.android_db.ContactDetailsHelper
import java.time.Instant
import java.util.UUID
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_INSERT
import com.dododial.phone.database.ClientDatabase
import com.dododial.phone.database.ClientRepository
import com.example.actualfinaldatabase.permissions.PermissionsRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


class DialerActivity : AppCompatActivity() {

    var fromDialer = false
    private val CHANNEL_ID = "alxkng5737"

    @SuppressLint("LogNotTimber")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer)
        phoneNumberInput.setText(intent?.data?.schemeSpecificPart)
        go_back_to_call.isVisible = false
        notificationChannelCreator()

        /**
         * Offers to replace default dialer, automatically makes requesting permissions
         * separately unnecessary
         */

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), 120)
        } else {
            offerReplacingDefaultDialer()
        }

        //PermissionsRequester.multiplePermissions(this, this)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("LogNotTimber")
    override fun onStart() {
        super.onStart()

        /**
         * Repository / database needs to call a query first in order to initialize database,
         * in which the ClientDatabase getDatabase is called
         */
        val repository: ClientRepository? = (application as App).repository

        val job = (application as App).applicationScope.launch {
            repository?.dummyQuery()
        }   

        runBlocking {
            job.join()
            Log.i("DODODEBUG: ", "REPOSITORY IS NULL = " + (repository == null))
        }

        phoneNumberInput.setOnEditorActionListener { _, _, _ ->
            makeCall()
            true
        }

        if (OngoingCall.call?.state ?: Call.STATE_DISCONNECTED == Call.STATE_ACTIVE ||
            OngoingCall.call?.state ?: Call.STATE_DISCONNECTING == Call.STATE_RINGING) {
                fromDialer = true
                go_back_to_call.isVisible = true
                go_back_to_call.setOnClickListener {
                    OngoingCall.call?.let { CallActivity.start(this, it) }
                }
        } else {
            go_back_to_call.isVisible = false
        }

    }

    private fun makeCall() {
        if (checkSelfPermission(this, CALL_PHONE) == PERMISSION_GRANTED) {
            val uri = "tel:${phoneNumberInput.text}".toUri()
            startActivity(Intent(Intent.ACTION_CALL, uri))
        } else {
            requestPermissions(this, arrayOf(CALL_PHONE), REQUEST_PERMISSION)
        }
    }

    @SuppressLint("LogNotTimber")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION && PERMISSION_GRANTED in grantResults) {
            makeCall()
        }
    }

    private fun offerReplacingDefaultDialer() {
        if (getSystemService(TelecomManager::class.java).defaultDialerPackage != packageName) {
            Intent(ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                .let(::startActivity)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")


    companion object {
        const val REQUEST_PERMISSION = 0
    }

    fun notificationChannelCreator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
            mChannel.description = descriptionText
            mChannel.setSound(null, null)
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }

}
