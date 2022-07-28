package com.dododial.phone.call_related

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import com.dododial.phone.R
import com.dododial.phone.databinding.ActivityInCallBinding

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
        running = true

        inCallOverLockScreen()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Lets IncomingCallActivity know that InCallActivity is already running.
        running = false
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
        var running = false

        fun start(context: Context) {
            Intent(context, InCallActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let(context::startActivity)
        }
    }
}
