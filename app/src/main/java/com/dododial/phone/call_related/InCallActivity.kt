package com.dododial.phone.call_related

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import com.dododial.phone.R
import com.dododial.phone.databinding.ActivityInCallBinding
import timber.log.Timber

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
    }

    /**
     * Don't allow back press when in InCallFragment.
     */
    override fun onBackPressed() {
        if (navController.currentDestination!!.id == R.id.inCallFragment) {
            return
        } else {
            super.onBackPressed()
        }
    }

    /**
     * TODO: When the InCallActivity has already started, and the user closes the phone. The
     *  InCallActivity should not show over again.
     */
    fun inCallOverLockScreen() {
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
    }
}
