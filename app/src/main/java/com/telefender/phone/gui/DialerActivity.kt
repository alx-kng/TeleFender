package com.telefender.phone.gui

import android.Manifest.permission.CALL_PHONE
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.Call
import android.telecom.TelecomManager
import android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER
import android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.telefender.phone.R
import com.telefender.phone.call_related.CallManager
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.android.synthetic.main.activity_dialer.*
import timber.log.Timber


// TODO we need to make a custom notification for initiating a call

// TODO Go through all uses of runBlocking{} and see if we can just use scope.launch if run blocking
//  isn't necessary (more for optimization if app is running slow OR closer to production)


class DialerActivity : AppCompatActivity() {

    var fromDialer = false
    private val CHANNEL_ID = "alxkng5737"

    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivityForResult(intent, 120)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer)
        phoneNumberInput.setText(intent?.data?.schemeSpecificPart)
        go_back_to_call.isVisible = false
        notificationChannelCreator()

        /**
         * TODO Make dialog to explain reason for Do not disturb access.
         *
         * Offers to replace default dialer, automatically makes requesting permissions
         * separately unnecessary
         */
        offerReplacingDefaultDialer()
    }

    
    override fun onStart() {
        super.onStart()

        /**
         * Repository / database needs to call a query first in order to initialize database,
         * in which the ClientDatabase getDatabase is called
         */
//        val repository: ClientRepository? = (application as App).repository
//
//        val job = (application as App).applicationScope.launch {
//            repository?.dummyQuery()
//        }

        phoneNumberInput.setOnEditorActionListener { _, _, _ ->
            initOutgoingCall()
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: DIALED")
            true
        }
    }

    override fun onResume() {
        super.onResume()

        val focusedState = CallManager.focusedConnection.value?.state ?: Call.STATE_DISCONNECTED
        if (focusedState == Call.STATE_ACTIVE ||focusedState == Call.STATE_RINGING) {
            fromDialer = true
            go_back_to_call.isVisible = true
            go_back_to_call.setOnClickListener {
                InCallActivity.start(this)
            }
        } else {
            go_back_to_call.isVisible = false
        }
    }

    /**
    * TODO : Warning
    *  In the odd case that making a call through the dialer requires that you choose
    *  a different app to "complete an action" either notify users choose the default
    *  phone app as always or see if we can bypass that in the first place
    */
    private fun makeCall() {
        if (checkSelfPermission(this, CALL_PHONE) == PERMISSION_GRANTED) {
            val uri = "tel:${phoneNumberInput.text}".toUri()
            startActivity(Intent(Intent.ACTION_CALL, uri))
        } else {
            requestPermissions(this, arrayOf(CALL_PHONE), REQUEST_PERMISSION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun initOutgoingCall() {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val uri = "tel:${phoneNumberInput.text}".toUri()
            telecomManager.placeCall(uri, null)
        } catch (e: Exception) {
            finish()
        }
    }


    
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager : RoleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
                val intent : Intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    .putExtra(EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                startForResult.launch(intent)
            } else {
                val intent = Intent(ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                startForResult.launch(intent)
            }
        }
    }

    private fun notificationChannelCreator() {
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

    
    companion object {
        const val REQUEST_PERMISSION = 0

        fun start(context: Context) {
            Intent(context, DialerActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let(context::startActivity)
        }
    }

}
