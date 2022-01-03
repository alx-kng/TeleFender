package com.github.arekolek.phone

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_call.*
import java.util.concurrent.TimeUnit

import android.view.WindowManager
import android.app.KeyguardManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.github.arekolek.phone.notifications.DummyForegroundActiveCallService


class CallActivity : AppCompatActivity() {

    private val disposables = CompositeDisposable()

    private lateinit var number: String

    private val CHANNEL_ID = "alxkng5737"
    private val NOTIFICATION_ID = 12345678

    override fun onCreate(savedInstanceState: Bundle?) {

        /*//modify window flags so as to display it on lock screen
        /*val window: Window = window
        with(window) {
            addFlags(
                    R.attr.showWhenLocked or
                        R.attr.turnScreenOn or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }*/

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                R.attr.showWhenLocked or
                R.attr.turnScreenOn
        )

        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            var keyguard = km.newKeyguardLock("simple-phone app")
            keyguard.disableKeyguard()
            //requestDismissKeyguard(this, null)
        }*/

        // to wake up screen
        val pm = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        var wakeFlags = PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP
        /*if (Build.VERSION.SDK_INT <= 15) {
            wakeFlags = wakeFlags or PowerManager.SCREEN_BRIGHT_WAKE_LOCK
        }*/

        val wakeLock = pm.newWakeLock(wakeFlags, "Simple Phone:Call")
        wakeLock.acquire() //wakelock

        /*val wakeLock: PowerManager.WakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Simple Phone::MyWakelockTag").apply {
                    acquire(4000)
                }
            }*/*/

        super.onCreate(savedInstanceState)
        setContentView(com.github.arekolek.phone.R.layout.activity_call)
        number = intent.data.schemeSpecificPart

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    override fun onStart() {
        super.onStart()

        var dummyActiveServiceIntent = Intent(this, DummyForegroundActiveCallService::class.java)

        answer.setOnClickListener {
            OngoingCall.answer()
        }

        hangup.setOnClickListener {
            OngoingCall.hangup()
        }

        OngoingCall.state
            .subscribe(::updateUi)
            .addTo(disposables)

        OngoingCall.state
            .filter { it == Call.STATE_ACTIVE}
            .firstElement()
            .subscribe {
                ContextCompat.startForegroundService(this, dummyActiveServiceIntent)
            }
            .addTo(disposables)

        OngoingCall.state
            .filter { it == Call.STATE_DISCONNECTED }
            .delay(1, TimeUnit.SECONDS)
            .firstElement()
            .subscribe {
                finish() }
            .addTo(disposables)
    }


    @SuppressLint("SetTextI18n")
    private fun updateUi(state: Int) {
        callInfo.text = "${state.asString().toLowerCase().capitalize()}\n$number"

        answer.isVisible = state == Call.STATE_RINGING
        hangup.isVisible = state in listOf(
            Call.STATE_DIALING,
            Call.STATE_RINGING,
            Call.STATE_ACTIVE
        )
    }

    override fun onStop() {
        super.onStop()
        disposables.clear()

        /*val intent = Intent(this, TestActivity::class.java)
        startActivity(intent)*/
    }


    companion object {
        fun start(context: Context, call: Call) {
            Intent(context, CallActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(call.details.handle)
                .let(context::startActivity)
        }
    }

}
