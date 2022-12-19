package com.telefender.phone.gui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.CallLog
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.telefender.phone.App
import com.telefender.phone.call_related.*
import com.telefender.phone.data.tele_database.TeleCallDetails
import com.telefender.phone.databinding.ActivityIncomingCallBinding
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.*
import timber.log.Timber


/*
TODO: Provide way to access incoming activity from main activity in case user gets rid of this
 activity from the Recents screen.

TODO: Just saw another small bug where the original default dialer suddenly took over during
 incoming call (after leaving phone all night). However, our app returned after we reentered app.
 Don't know how big of a problem this is.
 */
class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding
    private val scope = CoroutineScope(Dispatchers.Default)

    private val silenceDelay: Long = 10000L
    private val incomingCall = CallManager.focusedCall
    private var answered = false
    private var unallowed : Boolean? = false
    private var rejected = false

    /**
     * Finishes activity if there is no incoming call. Used to finish activity for all cases,
     * including other user hangup, unallowed hangup, user answer / hangup.
     *
     * NOTE: In order to cancel [observer], you must use the Observer constructor with lambda,
     * other wise, removeObserver() won't be able to find a reference to the observer.
     */
    private val observer = Observer { isIncoming: Boolean->
        if (!isIncoming) {
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: INCOMING FINISHED: focusedCall state: ${
                CallManager.callStateString(CallManager.focusedCall.getStateCompat())}")

            scope.cancel()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showOverLockScreen()

        /**
         * If the call is unsafe and app is in silence mode, the call is declined after [silenceDelay]
         * if call isn't already disconnected or connected.
         */
        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: SAFE - ${intent.extras?.getBoolean("Safe")}")
        val safe = intent.extras?.getBoolean("Safe") ?: true
        if (!safe && CallManager.currentMode == HandleMode.SILENCE_MODE) {
            scope.launch {
                silenceHangup()
            }
        }

        CallManager.incomingCallLiveData.observeForever(observer)

        binding.displayNumber.text = CallManager.focusedCall.number() ?: "Unknown number"

        binding.answerIncoming.setOnClickListener {
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: Silence Block Action: No block action taken because call was answered by user.")

            CallManager.lastAnsweredCall = CallManager.focusedCall
            scope.cancel()
            answered = true
            CallManager.answer()
            InCallActivity.start(this)
        }

        binding.hangupIncoming.setOnClickListener {
            scope.cancel()
            rejected = true
            CallManager.hangup()
        }
    }

    override fun onResume() {
        super.onResume()
        _running = true
    }

    /**
     * TODO: If incoming call is hanged up too quickly, very rarely unallowed is null somehow?
     *  Only happened once and hasn't happened since, but keep an eye on it.
     *  For now, we make unallowed nullable just in case.
     */
    override fun finish() {
        CallManager.incomingCallLiveData.removeObserver(observer)

        if (!answered) {
            val repository = ((CallService.context?.applicationContext) as App).repository

            val direction = when (unallowed) {
                true -> CallLog.Calls.BLOCKED_TYPE
                false, null -> {
                    if (rejected) CallLog.Calls.REJECTED_TYPE else CallLog.Calls.MISSED_TYPE
                }
            }

            val unallowedParam = unallowed ?: false
            TeleCallDetails.insertCallDetail(repository, incomingCall!!, unallowedParam, direction)
        }

        super.finish()
    }

    override fun onDestroy() {
        /**
         * Sets the running status to false and ringer mode back to normal.
         * Using a runnable doesn't seem necessary.
         */
        _running = false
        AudioHelpers.ringerSilent(this, false)

        super.onDestroy()
    }

    /**
     * Don't allow back press when in IncomingActivity.
     */
    override fun onBackPressed() {
        return
    }

    /**
     * TODO: Double check logic
     *
     * Hangup in silence mode if unsafe call isn't already answered, declined, or disconnected.
     * If incoming unsafe call is successfully unallowed using silenceHangup() (that is, before
     * the scope is cancelled due to user answer or decline), then insert into CallDetail table
     * that the call was unallowed. If incoming unsafe call is hung up by other user, then do
     * nothing, as CallDetail was already inserted into CallDetail table inside observer.
     *
     * Don't need to check call state, since case when other user / phone disconnects is handled
     * by incomingCall observer (as well as answer and hangup cases).
     */
    private suspend fun silenceHangup() {
        for (i in 1..10) {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: INSIDE SILENCE HANGUP $i")
            delay(silenceDelay / 10)
        }

        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: " +
            "Silence Block Action: Block action was taken because call was not answered or disconnected by user.")

        unallowed = true
        CallManager.hangup()
    }

    /**
     * TODO: Double check side cases.
     *
     * Shows activity over lock screen.
     */
    private fun showOverLockScreen() {

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