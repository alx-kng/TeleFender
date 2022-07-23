package com.dododial.phone.call_related

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
import android.util.Log
import androidx.core.content.ContextCompat
import com.dododial.phone.R
import com.dododial.phone.asString
import com.dododial.phone.call_related.OngoingCall.state
import com.dododial.phone.call_related.notifications.DummyForegroundActiveCallService
import timber.log.Timber


class CallActivity : AppCompatActivity() {

    private val disposables = CompositeDisposable()

    private lateinit var incomingNumber: String

    private val CHANNEL_ID = "alxkng5737"
    private val NOTIFICATION_ID = 12345678

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        // Gets the incoming number string from the intent data,
        // but could be null in some cases (I think)
        incomingNumber = intent.data?.schemeSpecificPart ?: "Conference"

        Timber.i("THIS NUMBER THING: %s", incomingNumber)

        // TODO Fix CallActivity from showing over lockscreen when the call
        //  when the user presses powerbutton after already accepting call
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        var dummyActiveServiceIntent = Intent(this, DummyForegroundActiveCallService::class.java)

        CallManager.focusedConnection.observe(this) { connection ->
            Log.i("DODOEBUG", "INSIDE OBSERVER")

            if (connection == null) {
                Log.i("DODODEBUG", "FINISH IS BEING CALLED!")
                finish()
            } else {
                updateUi(connection.state, connection.call.number())
            }
        }
    }

    override fun onStart() {
        super.onStart()


        answer.setOnClickListener {
            CallManager.answer()
        }

        hangup.setOnClickListener {
            CallManager.hangup()
            finish()
        }

        merge.setOnClickListener {
            CallManager.merge()
        }

        swap.setOnClickListener {
            CallManager.swap()
        }

//        OngoingCall.state
//            .subscribe(::updateUi)
//            .addTo(disposables)
//
//        OngoingCall.state
//            .filter { it == Call.STATE_ACTIVE}
//            .firstElement()
//            .subscribe {
//                ContextCompat.startForegroundService(this, dummyActiveServiceIntent)
//            }
//            .addTo(disposables)
//
//        OngoingCall.state
//            .filter { it == Call.STATE_DISCONNECTED }
//            .delay(1, TimeUnit.SECONDS)
//            .firstElement()
//            .subscribe {
//                finish() }
//            .addTo(disposables)
    }


    @SuppressLint("SetTextI18n")
    private fun updateUi(state: Int, number: String?) {
        callInfo.text = "${state.asString().lowercase().capitalize()}\n${number ?: "Conference"}"

        answer.isVisible = state == Call.STATE_RINGING
        hangup.isVisible = state in listOf(
            Call.STATE_DIALING,
            Call.STATE_RINGING,
            Call.STATE_ACTIVE
        )
    }

    override fun onStop() {
        super.onStop()
//        disposables.clear()

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
