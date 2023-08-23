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
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.Observer
import com.telefender.phone.call_related.*
import com.telefender.phone.databinding.ActivityIncomingCallBinding
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import com.telefender.phone.notifications.ActiveCallNotificationService
import com.telefender.phone.notifications.IncomingCallService
import kotlinx.coroutines.*
import timber.log.Timber


/**
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

    /**
     * Finishes activity if there is no incoming call. Used to finish activity for all cases,
     * including other user hangup, unallowed hangup, user answer / hangup.
     *
     * NOTE: In order to cancel [observer], you must use the Observer constructor with lambda,
     * other wise, removeObserver() won't be able to find a reference to the observer.
     */

    private val observer = Observer { isIncoming: Boolean->
        if (!isIncoming) {
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

        val number = CallManager.focusedCall.number()
        binding.displayNumber.text = number?.let {
            TeleHelpers.getContactName(this, it)
        } ?: number ?: "Unknown number"

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
         *  and stop the in call activity.
         */
        fun start(context: CallService, safe: Boolean) {
            /**
             * Stops the active call notification when there is an incoming call. We check it
             * here because using the CallManager to detect incoming calls is not fine grained
             * enough. That is, there is a split second after answering an incoming call when
             * the state of the call is still RINGING and hasn't switched to ACTIVE yet. This
             * causes the immediate destruction of the notification / other unexpected behaviors.
             *
             * NOTE: All incoming calls go through this start() function first.
             * NOTE: shouldStopActiveNotification checks for holding connection in devices lower
             * than Android 10, as the following case:
             *
             * active 1 -> incoming 2 -> answer 2 -> hangup active 2 from other end -> incoming 2 again
             *
             * Causes the connection states to actually be [Holding, Ringing] instead of
             * [Active, Ringing] in Android 9.
             */
            val shouldStopActiveNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                CallManager.hasActiveConnection()
            } else {
                CallManager.hasActiveConnection() || CallManager.holdingConnection() != null
            }

            if (shouldStopActiveNotification) {
                context.stopService(Intent(context, ActiveCallNotificationService::class.java))
            }

            // Start incoming call notification
            context.startForegroundService(
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
            InCallActivity.context?.finishAndRemoveTask()
        }
    }
}