package com.dododial.phone.gui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.dododial.phone.call_related.AudioHelpers
import com.dododial.phone.call_related.CallManager
import com.dododial.phone.call_related.getStateCompat
import com.dododial.phone.call_related.number
import com.dododial.phone.databinding.ActivityIncomingCallBinding
import kotlinx.coroutines.*
import timber.log.Timber


class IncomingCallActivity : AppCompatActivity() {

    private val silenceDelay: Long = 10000L
    private lateinit var binding: ActivityIncomingCallBinding
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showOverLockScreen()

        /**
         * If the call is in silence mode and is unsafe, the call is declined after [silenceDelay]
         * if call isn't already disconnected or connected.
         */
        Timber.i("DODODEBUG: SAFE - ${intent.extras?.getBoolean("Safe")}")
        val safe = intent.extras?.getBoolean("Safe") ?: true
        if (!safe && CallManager.currentMode == CallManager.SILENCE_MODE) {
            scope.launch {
                silenceHangup()
            }
        }

        /**
         * Finishes activity if there is no incoming call. Used for case where other user hangs up.
         */
        CallManager.incomingCallLiveData.observe(this) { isIncoming ->
            if (!isIncoming) {
                Timber.i("DODODEBUG: INCOMING FINISHED: focusedCall state: ${
                    CallManager.callStateString(
                        CallManager.focusedCall.getStateCompat()
                    )
                }")
                scope.cancel()
                finish()
            }
        }

        binding.displayNumber.text = CallManager.focusedCall.number() ?: "Unknown number"

        binding.answerIncoming.setOnClickListener {
            Timber.i("DODODEBUG: ANSWER PRESSED")

            CallManager.lastAnsweredCall = CallManager.focusedCall
            CallManager.answer()
            InCallActivity.start(this)
            scope.cancel()
            finish()
        }

        binding.hangupIncoming.setOnClickListener {
            CallManager.hangup()
            scope.cancel()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        _running = true
    }

    override fun onDestroy() {
        super.onDestroy()

        _running = false

        /**
         * Sets the ringer mode back to normal. Using a runnable doesn't seem necessary.
         */
        AudioHelpers.ringerSilent(this, false)
    }

    /**
     * Don't allow back press when in IncomingActivity.
     */
    override fun onBackPressed() {
        return
    }

    suspend fun silenceHangup() {
        delay(silenceDelay)

        val callState = CallManager.focusedCall.getStateCompat()

        if (callState == Call.STATE_CONNECTING
            || callState == Call.STATE_ACTIVE) {
            Timber.i("Silence Block Action: No block action taken because call was answered by user.")
        } else if (callState != Call.STATE_DISCONNECTED
            && callState != Call.STATE_DISCONNECTING) {
            CallManager.hangup()
            Timber.i("Silence Block Action: Block action was taken because call was not answered or disconnected by user.")
        }
    }

    /**
     * TODO: Double check side cases.
     *
     * Shows activity over lock screen.
     */
    fun showOverLockScreen() {

        /**
         * Only wake screen if screen isn't on. Otherwise, just set to show over lock screen.
         */
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val screenOn = powerManager.isInteractive

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)

            if (!screenOn) {
                setTurnScreenOn(true)

                /**
                 * Makes sure to not dismiss keyguard if already dismissed. Otherwise, the keyguard
                 * pops up again. Keyguard is already dismissed if InCallActivity is running.
                 */
                if (!InCallActivity.running) {
                    val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                    keyguardManager.requestDismissKeyguard(this, null)
                }
            }
        } else {
            if (!screenOn) {
                window.addFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )

                if (!InCallActivity.running) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
                }
            } else {
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                )
            }
        }
    }

    companion object {

        /**
         * Indicates whether IncomingCallActivity is currently shown. Used to smoothly update
         * UI in InCallFragment's updateCallerDisplay().
         */
        private var _running = false
        val running : Boolean
            get() = _running

        /**
         * Launches into the same task as InCallActivity if InCallActivity is running.
         */
        fun start(context: Context, safe: Boolean) {
            if (InCallActivity.running){
                InCallActivity.startIncoming(safe)
            } else {
                Intent(context, IncomingCallActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                    .putExtra("Safe", safe)
                    .let(context::startActivity)
            }
        }
    }
}