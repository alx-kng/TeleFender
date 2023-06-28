package com.telefender.phone.gui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.telefender.phone.call_related.*
import com.telefender.phone.databinding.ActivityIncomingCallBinding
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.notifications.ActiveCallNotificationService
import com.telefender.phone.notifications.IncomingCallService
import kotlinx.coroutines.*
import timber.log.Timber


/**
 * TODO: Backstack management needs a little improvement. When pressing on incoming call notification
 *  to start IncomingCallActivity, a new instance / screen is created even when there is an existing
 *  screen (sometimes). The other problem is that if you destroy the current incoming call activity
 *  and re-instantiate through the notification. If you answer the notification, the InCallActivity
 *  opens up in a new screen rather than in the same one, which leaves an incoming call activity
 *  screen husk (which is bad).
 *
 *  TODO: LAST PROBLEM CASE -> active underlying call -> incoming call -> DON'T get rid of incoming
 *   call activity from recents screen -> press the incoming call notification -> double screen
 *
 * TODO: NEED TO REFACTOR FLOW. -> think we did now
 *
 * TODO: Provide way to access incoming activity from main activity in case user gets rid of this
 *  activity from the Recents screen --> done now through notification, but maybe we can add another
 *  in app way (like a floating button).
 *
 * TODO: Just saw another small bug where the original default dialer suddenly took over during
 *  incoming call (after leaving phone all night). However, our app returned after we reentered app.
 *  Don't know how big of a problem this is.
 *
 *  TODO: Occasionally undesirable keyguard dismissal for showing over lockscreen.
 */
class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding
    private val scope = CoroutineScope(Dispatchers.Default)

    private val silenceDelay: Long = 10000L
    private val incomingCall = CallManager.focusedCall
    private var answered = false
    private var unallowed : Boolean? = false
    private var rejected = false
    private var safe = true

    /**
     * Finishes activity if there is no incoming call. Used to finish activity for all cases,
     * including other user hangup, unallowed hangup, user answer / hangup.
     *
     * NOTE: In order to cancel [observer], you must use the Observer constructor with lambda,
     * other wise, removeObserver() won't be able to find a reference to the observer.
     */

    private val observer = Observer { isIncoming: Boolean->
        if (!isIncoming) {
            scope.cancel()

            /*
            Gets rid of task only if activity is in own task separate from InCallActivity. This
            prevents lingering IncomingCallActivity document husk on Recents screen and also makes
            sure that closing the IncomingCallActivity doesn't close the task containing the
            InCallActivity (if it exists).
             */
            if (isTaskRoot) {
                finishAndRemoveTask()
            } else {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.e("$DBL: IncomingCallActivity - onCreate()")

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sets created
        _running = true

        showOverLockScreen()

        // Need to observe forever so that observer still runs when activity is not showing.
        CallManager.incomingCallLiveData.observeForever(observer)

        /**
         * Don't allow back press when in IncomingActivity.
         */
        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                return
            }
        })

        binding.displayNumber.text = CallManager.focusedCall.number() ?: "Unknown number"

        binding.answerIncoming.setOnClickListener {
            val service = IncomingCallService.context
            if (service != null) {
                service.answer(fromActivity = true)
            } else {
                CallManager.answer()
            }
        }

        binding.hangupIncoming.setOnClickListener {
            val service = IncomingCallService.context
            if (service != null) {
                service.hangup(fromActivity = true)
            } else {
                CallManager.hangup()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        _showing = true
    }

    // TODO: Should showing be in onPause()?
    override fun onDestroy() {
        /**
         * Cleans up references
         * Using a runnable doesn't seem necessary.
         */
        _showing = false
        _running = false

        CallManager.incomingCallLiveData.removeObserver(observer)

        super.onDestroy()
    }

    private fun answer() {
        if (!safe) {
            Timber.i("$DBL: %s",
                "SILENCE_MODE Action: No block action taken because call was answered by user.")
        }

        CallManager.lastAnsweredCall = CallManager.focusedCall
        scope.cancel()
        answered = true
        CallManager.answer()
        InCallActivity.start(this)
    }

    private fun hangup() {
        scope.cancel()
        rejected = true
        CallManager.hangup()
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
            Timber.e("$DBL: INSIDE SILENCE HANGUP $i")
            delay(silenceDelay / 10)
        }

        Timber.i("$DBL: %s",
            "SILENCE_MODE ACTION: Block action was taken because call was not answered or disconnected by user.")

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
        private var _showing = false
        val showing : Boolean
            get() = _showing

        /**
         * Indicates whether IncomingCallActivity exists in backstack. Used to set PendingIntent
         * in IncomingCallService
         */
        private var _running = false
        val running : Boolean
            get() = _running

        /**
         * TODO: OLD DOC
         *  Launches into the same task as InCallActivity if InCallActivity is running and starts
         *  / stops notification services.
         *  -
         *  We won't be doing this for now since we observed some problems with the incoming call
         *  notification behavior when doing this. For now, we just start the incoming activity
         *  and stop the in call activity
         */
        fun start(context: Context, safe: Boolean) {
            Timber.e("$DBL: IncomingCallActivity - onStart()")

            val applicationContext = context.applicationContext

            /**
             * Stops the active call notification when there is an incoming call. We check it
             * here because using the CallManager to detect incoming calls is not fine grained
             * enough. That is, there is a split second after answering an incoming call when
             * the state of the call is still RINGING and hasn't switched to ACTIVE yet. This
             * causes the immediate destruction of the notification / other unexpected behaviors.
             *
             * NOTE: All incoming calls go through this start() function first.
             */
            if (CallManager.hasActiveConnection()) {
                applicationContext.stopService(Intent(context, ActiveCallNotificationService::class.java))
            }

            // Start incoming call notification
            applicationContext.startForegroundService(
                Intent(context, IncomingCallService::class.java)
                    .putExtra("Safe", safe)
            )

            // Starts IncomingCallActivity
            Intent(context, IncomingCallActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                .putExtra("Safe", safe)
                .let(context::startActivity)

            // Stops InCallActivity
            (InCallActivity.context as InCallActivity?)?.finishAndRemoveTask()

//            if (InCallActivity.running){
//                Timber.i("$DBL: IncomingCallActivity start() - InCallActivity running!")
//                InCallActivity.startIncoming(safe)
//            } else {
//                Timber.i("$DBL: IncomingCallActivity start() - InCallActivity not running!")
//                Intent(context, IncomingCallActivity::class.java)
//                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                    .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
//                    .putExtra("Safe", safe)
//                    .let(context::startActivity)
//            }
        }
    }
}