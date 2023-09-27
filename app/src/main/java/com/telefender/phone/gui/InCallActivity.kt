package com.telefender.phone.gui

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import java.lang.ref.WeakReference


/**
 * TODO: SOMETIMES IN-CALL ACTIVITY DOESN'T CLOSE CORRECTLY!! Probably some observer related
 *  problem in InCallFragment.
 *
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

    private lateinit var sensorManager: SensorManager
    private lateinit var proximitySensor: Sensor
    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var proximitySensorListener: SensorEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.i("$DBL: InCallActivity - onCreate()")

        binding = ActivityInCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Lets IncomingCallActivity know that InCallActivity is already running.
        _running = true
        _contexts.add(WeakReference(this))

        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "tag:proximity")

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

        setSupportActionBar(binding.topAppBarInCall)

        inCallOverLockScreen()
    }

    override fun onResume() {
        super.onResume()
        if (!wakeLock.isHeld) {
            wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)
            Timber.e("$DBL: Acquiring wake lock!")
        }
    }

    override fun onPause() {
        super.onPause()
        if (wakeLock.isHeld) {
            wakeLock.release()
            Timber.e("$DBL: Releasing wake lock!")
        }
    }

    override fun onDestroy() {
        Timber.i("$DBL: InCallActivity - onDestroy()")

        // Cleans up references
        _running = false
        _contexts.removeAll { it.get() == this }

        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_app_bar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
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

    fun displayUpButton(show: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(show)
    }

    fun setTitle(appBarTitle: String) {
        binding.topAppBarInCall.title = appBarTitle
    }

    fun displayAppBar(show: Boolean) {
        if (show) {
            if (binding.topAppBarInCall.visibility != View.VISIBLE) {
                binding.topAppBarInCall.visibility = View.VISIBLE
            }
        } else {
            if (binding.topAppBarInCall.visibility != View.GONE) {
                binding.topAppBarInCall.visibility = View.GONE
            }
        }
    }

    fun revertAppBar() {
        setTitle("")
        displayUpButton(false)
        displayAppBar(false)
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
        private val _contexts : MutableList<WeakReference<InCallActivity>> = mutableListOf()
        val context : InCallActivity?
            get() {
                cleanUp()
                return _contexts.lastOrNull()?.get()
            }

        /**
         * Remove any WeakReferences that no longer reference a CallService
         */
        private fun cleanUp() {
            _contexts.removeAll { it.get() == null }
        }

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
