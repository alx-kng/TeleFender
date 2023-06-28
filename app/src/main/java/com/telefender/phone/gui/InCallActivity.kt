package com.telefender.phone.gui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import com.telefender.phone.R
import com.telefender.phone.databinding.ActivityInCallBinding
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.notifications.ActiveCallNotificationService
import timber.log.Timber

/**
 * TODO: Rare bug where call is sent even after you press hangup (particularly, when you hangup
 *  immediately after sending call), and the call connects, but the InCallActivity doesn't show.
 *
 * TODO: VERY IMPORTANT BUG!!! - If you initiate a call from default phone app (when the current
 *  default dialer is our tele phone app), then the InCallActivity closes for some reason, and
 *  you can no longer access the call.
 *
 * TODO: Make sure to request that dialer activity shows of in-call screen during add call, or
 *  at least bring up keyguard.
 */
class InCallActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private lateinit var binding: ActivityInCallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.i("$DBL: InCallActivity - onCreate()")

        binding = ActivityInCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        /**
         * Don't allow back press when in InCallFragment.
         *
         * NOTE: The isEnabled pattern prevents onBackPressed() from invoking the current callback,
         * which causes an infinite loop (more in Android - General Notes).
         */
        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.i("$DBL: Back pressed in InCallActivity!")

                if (navController.currentDestination!!.id == R.id.inCallFragment) {
                    return
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        // Lets IncomingCallActivity know that InCallActivity is already running.
        _running = true
        _contexts.add(this)

        inCallOverLockScreen()
    }

    override fun onDestroy() {
        Timber.i("$DBL: InCallActivity - onDestroy()")

        // Cleans up references
        _running = false
        _contexts.remove(this)

        super.onDestroy()
    }

    private fun inCallOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            )
        }
    }

    companion object {

        private var _running = false
        val running : Boolean
            get() = _running

        /**
         * TODO: Is it safe to just use the most recently added context?
         *
         * We use a static list of Contexts to be safe (explained in Android - General Notes).
         */
        private val _contexts : MutableList<Context> = mutableListOf()
        val context : Context?
            get() = _contexts.lastOrNull()

        fun start(context: Context) {
            Intent(context, InCallActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let(context::startActivity)

            /**
             * Launches active call notification. We put this in start() since the active
             * notification service kills itself when an incoming call occurs (to make way for the
             * incoming call notification). As a result, if the incoming call is answered, then an
             * intent is sent to InCallActivity and we need to make sure to relaunch the notification.
             *
             * NOTE: Sending an Intent to start an already started service shouldn't cause any issues?
             * NOTE: Relaunching the notification after an incoming call is declined is handled in
             *  IncomingCallActivity
             */
            val applicationContext = context.applicationContext
            applicationContext.startForegroundService(
                Intent(applicationContext, ActiveCallNotificationService::class.java)
            )
        }

        /**
         * Launches IncomingActivity into same task as InCallActivity if InCallActivity is running.
         */
        fun startIncoming(safe: Boolean) {
            if (context != null) {
                Intent(context, IncomingCallActivity::class.java)
                    .putExtra("Safe", safe)
                    .let((context as InCallActivity)::startActivity)
            }
        }

        /**
         * Launches Dialer into same task as InCallActivity if InCallActivity is running.
         */
        fun startDialer() {
            if (context != null) {
                Intent(context, MainActivity::class.java)
                    .let((context as InCallActivity)::startActivity)
            }
        }
    }
}
