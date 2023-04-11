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
import com.telefender.phone.misc_helpers.TeleHelpers
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

    private val CHANNEL_ID = "alxkng5737"
    private val NOTIFICATION_ID = 12345678

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private lateinit var binding: ActivityInCallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: Back pressed in InCallActivity!")

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
        _context = this

        inCallOverLockScreen()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Lets IncomingCallActivity know that InCallActivity is already running.
        _running = false
        _context = null
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: IN CALL DESTROYED")
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

    // TODO: Maybe get rid of static Context and find better solution.
    companion object {

        private var _running = false
        val running : Boolean
            get() = _running

        private var _context: Context? = null
        val context : Context?
            get() = _context

        fun start(context: Context) {
            Intent(context, InCallActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let(context::startActivity)
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
