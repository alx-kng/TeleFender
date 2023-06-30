package com.telefender.phone.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CallLog
import android.telecom.Call
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import com.telefender.phone.R
import com.telefender.phone.call_related.*
import com.telefender.phone.data.tele_database.TeleCallDetails
import com.telefender.phone.gui.InCallActivity
import com.telefender.phone.gui.IncomingCallActivity
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.notifications.NotificationChannels.IN_CALL_CHANNEL_ID
import kotlinx.coroutines.*
import timber.log.Timber
import java.lang.ref.WeakReference


/**
 * TODO: See if startForeground() should be in onCreate() or onStartCommand() -> might not be that
 *  big of a deal -> seconded
 *
 * TODO: On Android 9 at least (SDK 29), the incoming notification doesn't always replace the
 *  in-call notification when there is an underlying call.
 *
 * TODO: Why does heads-up notification still show for Android 9 and 10 (SDK 29 & 30)? No problem
 *  on Android 12 but not sure about Android 11 or 13. ACTUALLY, every time notification is
 *  updated, a heads-up notification shows AND there is a notification sound.
 *
 * IncomingCallService not only represents the incoming call but also doubles as a foreground
 * service for the incoming call notification.
 */
class IncomingCallService : LifecycleService() {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val silenceDelay: Long = 10000L
    private val incomingCall = CallManager.focusedCall
    private var answered = false
    private var unallowed : Boolean? = false
    private var rejected = false
    private var safe = true

    /**
     * Indicates whether the InCallActivity was already launched by IncomingCallActivity when pressing
     * the answer button. Used to determine whether or not IncomingCallService should launch the
     * InCallActivity in onDestroy()
     */
    private var activeActivityLaunched = false

    /**
     * TODO: Only technically observes when incoming call goes away. So, I guess incoming
     *  notification isn't updated anywhere but in the beginning currently. -> not a big deal though
     */
    private val callObserver =  Observer<Connection?> {
        /**
         * Stops the service (which ends the notification) and cleans up some stuff if no more
         * incoming calls.
         */
        val hasRinging = it?.state == Call.STATE_RINGING || it?.state == Call.STATE_NEW
        val hasHolding = CallManager.holdingConnection() != null
        if (!hasRinging || hasHolding || answered) {
            Timber.i("$DBL: INCOMING FINISHED: focusedCall state: " +
                CallManager.callStateString(CallManager.focusedCall.getStateCompat())
            )

            // Cancels silent hangup
            scope.cancel()

            // Updates CallDetails with unallowed / rejected / etc.
            updateCallDetail()

            applicationContext.stopService(
                Intent(applicationContext, IncomingCallService::class.java)
            )
        } else {
            updateNotification(applicationContext)
        }
    }

    override fun onCreate() {
        super.onCreate()

        Timber.i("$DBL: IncomingCallService - onCreate()")

        // Sets context to be used by notification receiver
        _contexts.add(WeakReference(this))

        startForeground(notificationID, createNotification(applicationContext).build())
        CallManager.focusedConnection.observe(this, callObserver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        /**
         * If the call is unsafe and app is in silence mode, the call is declined after [silenceDelay]
         * if call isn't already disconnected or connected.
         */
        safe = intent?.extras?.getBoolean("Safe") ?: true
        if (!safe && CallManager.currentMode == HandleMode.SILENCE_MODE) {
            scope.launch {
                silenceHangup()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Timber.i("$DBL: IncomingCallService - onDestroy()")

        // Cleans up references
        _contexts.removeAll { it.get() == this }

        /*
        Only need to set the ringer mode back to normal if changed in the first place. Ringer mode
        could have only been changed if app has Do Not Disturb permissions.
         */
        AudioHelpers.setRingerMode(this, RingerMode.NORMAL)

        // Destroys incoming notification before launching active notification
        stopForeground(STOP_FOREGROUND_DETACH)

        /*
        We put the InCallActivity launch here if the call is answered from the notification because
        starting the activity immediately when the answer button is pressed often causes the
        incoming notification to linger a while, even when the active notification is already
        showing. Possible launch overlap with answer with fromActivity passed in as true, so extra
        check is added. We also restart the InCallActivity if there is an active underlying
        connection.
         */
        val shouldStartActivity = answered || CallManager.hasActiveConnection()
        if (shouldStartActivity && !activeActivityLaunched) {
            // Restarts InCallActivity. Requires CallService context, otherwise OS might not allow launch.
            CallService.context?.let {
                InCallActivity.start(it)
            }
        }

        super.onDestroy()
    }

    /**
     * Answers the call, updates some variables (which are used in deciding other launches and as
     * data for the database). Additionally, we start the activity immediately if the user presses
     * the answer button in the incoming call activity, as the response is faster. The reason why
     * we don't immediately start for notifications is because the active notification is sometimes
     * started too quickly (with incoming notification still lingering). However, if the user is in
     * the activity, they won't be seeing the behavior in the notifications, so its safe to fast
     * launch.
     */
    fun answer(fromActivity: Boolean) {
        if (!safe) {
            Timber.i("$DBL: SILENCE_MODE Action: No block action taken because call was answered by user.")
        }

        CallManager.lastAnsweredCall = CallManager.focusedCall
        scope.cancel()
        answered = true

        if (fromActivity) {
            activeActivityLaunched = true
            CallManager.answer()

            // Starts InCallActivity. Requires CallService context, otherwise OS might not allow launch.
            CallService.context?.let {
                InCallActivity.start(it)
            }
        } else {
            CallManager.answer()
        }
    }

    fun hangup(fromActivity: Boolean) {
        scope.cancel()
        rejected = true

        if (fromActivity && CallManager.hasActiveConnection()) {
            activeActivityLaunched = true
            CallManager.hangup()

            // Restarts InCallActivity. Requires CallService context, otherwise OS might not allow launch.
            CallService.context?.let {
                InCallActivity.start(it)
            }
        } else {
            CallManager.hangup()
        }
    }

    /**
     * TODO: If incoming call is hanged up too quickly, very rarely unallowed is null somehow?
     *  Only happened once and hasn't happened since (in hundreds of calls), but keep an eye on it.
     *  For now, we make unallowed nullable just in case.
     */
    private fun updateCallDetail() {
        if (!answered) {
            val direction = if (unallowed == true || rejected) {
                CallLog.Calls.REJECTED_TYPE
            } else {
                CallLog.Calls.MISSED_TYPE
            }

            val unallowedParam = unallowed ?: false
            TeleCallDetails.insertCallDetail(this, incomingCall!!, unallowedParam, direction)
        }
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

    companion object {

        /**
         * ID is different from active notification ID
         */
        private const val notificationID = 2
        const val ANSWER_ACTION = "answer"
        const val HANGUP_ACTION = "hangup"

        /**
         * Stores service context. Used so that IncomingCallActivity and notification Receiver can
         * call the correct hangup / answer methods. We use a list here for static safety, check
         * Android - General Notes for more info.
         */
        private val _contexts : MutableList<WeakReference<IncomingCallService>> = mutableListOf()
        val context : IncomingCallService?
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

        /**
         * TODO: Maybe choose this over separateIncoming?
         *
         * Similar to separateIncoming() in ActiveCallNotificationService. Possibly even better.
         * We use multiple different checks for incoming call since there is a tiny period after
         * answering call (with underlying call) when there is still a ringing call (e.g., holding
         * and ringing connection).
         */
        private fun hasIncoming() : Boolean {
            val hasHolding = CallManager.holdingConnection() != null
            return CallManager.incomingCall() && !hasHolding
        }

        fun updateNotification(applicationContext: Context) {
            val notification = createNotification(applicationContext).build()
            val notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationID, notification)
        }

        fun createNotification(applicationContext: Context) : NotificationCompat.Builder {
            // Safer to use CallService context if possible (which should be available)
            val activityLaunchContext = CallService.context ?: applicationContext

            val activityIntent = Intent(activityLaunchContext, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            }
            val activityPendingIntent = PendingIntent.getActivity(
                activityLaunchContext,
                5,
                activityIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val answerIntent = Intent(applicationContext, IncomingCallNotificationReceiver::class.java).apply {
                putExtra("action", ANSWER_ACTION)
            }
            val answerPendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                6,
                answerIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val hangupIntent = Intent(applicationContext, IncomingCallNotificationReceiver::class.java).apply {
                putExtra("action", HANGUP_ACTION)
            }
            val hangupPendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                7,
                hangupIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val contentView = RemoteViews(
                applicationContext.packageName,
                R.layout.notification_incoming_call
            ).apply {
                setOnClickPendingIntent(R.id.incoming_notification_answer_button, answerPendingIntent)
                setOnClickPendingIntent(R.id.incoming_notification_hangup_button, hangupPendingIntent)
            }

            val notificationTitle = CallManager.focusedCall.number()
            val notificationText = "Incoming call"

            // Updates the title / text of the notification.
            if (hasIncoming()) {
                contentView.apply {
                    setTextViewText(R.id.incoming_notification_title, notificationTitle)
                    setTextViewText(R.id.incoming_notification_text, notificationText)
                }
            }

            val notification = NotificationCompat.Builder(applicationContext, IN_CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(activityPendingIntent)
                .setCustomContentView(contentView)
                .setColorized(true)
                .setColor(ContextCompat.getColor(applicationContext, R.color.teal_700))

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S){
                notification.setStyle(NotificationCompat.DecoratedCustomViewStyle())
            }

            return notification
        }
    }
}