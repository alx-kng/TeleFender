package com.telefender.phone.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import com.telefender.phone.R
import com.telefender.phone.call_related.*
import com.telefender.phone.gui.InCallActivity
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.notifications.NotificationChannels.IN_CALL_CHANNEL_ID
import timber.log.Timber

/**
 * TODO: See if startForeground() should be in onCreate() or onStartCommand() -> might not be that
 *  big of a deal
 *
 * TODO: Why does heads-up notification still show for Android 9 and 10 (SDK 29 & 30)? No problem
 *  on Android 12 but not sure about Android 11 or 13.
 */
class ActiveCallNotificationService : LifecycleService() {

    private val callObserver =  Observer<Connection?> {
        /**
         * Stops the service (which ends the notification) if no more calls. Refer to
         * IncomingCallActivity.start() for more info on stopping the service when there is ean
         * incoming call.
         */
        if (it == null) {
            applicationContext.stopService(
                Intent(applicationContext, ActiveCallNotificationService::class.java)
            )
        } else {
            updateNotification(applicationContext)
        }
    }

    private val audioObserver =  Observer<Boolean> {
        updateNotification(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()

        Timber.i("$DBL: ActiveCallNotificationService - onCreate()")
        startForeground(notificationID, createNotification(applicationContext).build())

        CallManager.focusedConnection.observe(this, callObserver)
        AudioHelpers.speakerStatus.observe(this, audioObserver)
        AudioHelpers.muteStatus.observe(this, audioObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("$DBL: ActiveCallNotificationService - onDestroy()")
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    companion object {
        private const val notificationID = 1
        const val SPEAKER_ACTION = "speaker"
        const val MUTE_ACTION = "mute"
        const val HANGUP_ACTION = "hangup"

        /**
         * TODO: Check if this causes problems
         *
         * We use separateIncoming to detect whether there is an incoming call or not. Although it
         * doesn't perfectly determine incoming calls (in the case of incoming call with no
         * underlying active call), it does seem to detect incoming calls with an underlying active
         * call. However, it is a double edged sword, as even after answering an incoming call, the
         * Call object sometimes takes a split second to switch from incoming to active, which
         * gives a faulty output here. We might not need to worry about that though, as the
         * notification behavior seems to be correct as of now.
         */
        private fun separateIncoming() : Boolean {
            return CallManager.incomingCall() && CallManager.hasActiveConnection()
        }

        fun updateNotification(applicationContext: Context) {
            val notification = createNotification(applicationContext).build()
            val notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationID, notification)
        }

        fun createNotification(applicationContext: Context) : NotificationCompat.Builder {
            // Safer to use CallService context if possible (which should be available)
            val activityLaunchContext = CallService.context ?: applicationContext

            val activityIntent = Intent(activityLaunchContext, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val activityPendingIntent = PendingIntent.getActivity(
                activityLaunchContext,
                1,
                activityIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val speakerIntent = Intent(applicationContext, ActiveCallNotificationReceiver::class.java).apply {
                putExtra("action", SPEAKER_ACTION)
            }
            val speakerPendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                2,
                speakerIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val muteIntent = Intent(applicationContext, ActiveCallNotificationReceiver::class.java).apply {
                putExtra("action", MUTE_ACTION)
            }
            val mutePendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                3,
                muteIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val hangupIntent = Intent(applicationContext, ActiveCallNotificationReceiver::class.java).apply {
                putExtra("action", HANGUP_ACTION)
            }
            val hangupPendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                4,
                hangupIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val currentSpeakerStatus = AudioHelpers.speakerStatus.value!!
            val currentSpeakerDrawable = if (currentSpeakerStatus) {
                R.drawable.ic_baseline_volume_up_24_blue
            } else {
                R.drawable.ic_baseline_volume_up_24
            }

            val currentMuteStatus = AudioHelpers.muteStatus.value!!
            val currentMuteDrawable = if (currentMuteStatus) {
                R.drawable.ic_baseline_mic_off_24_blue
            } else {
                R.drawable.ic_baseline_mic_24
            }

            val contentView = RemoteViews(
                applicationContext.packageName,
                R.layout.notification_active_call
            ).apply {
                setOnClickPendingIntent(R.id.active_notification_speaker_button, speakerPendingIntent)
                setOnClickPendingIntent(R.id.active_notification_mute_button, mutePendingIntent)
                setOnClickPendingIntent(R.id.active_notification_hangup_button, hangupPendingIntent)
                setImageViewResource(R.id.active_notification_speaker_button, currentSpeakerDrawable)
                setImageViewResource(R.id.active_notification_mute_button, currentMuteDrawable)
            }

            val notificationTitle = CallManager.focusedCall.number()
                ?: "Conference call (${CallManager.focusedCall?.children?.size ?: 0})"
            val notificationText = "Active call"

            /*
            TODO: Using separateIncoming() here may or may not be a problem. -> MIGHT BE -> Not
             even entirely sure if we need it or not. For now, I've been trying removing it.
             -> seems to work well.

            Updates the title / text of the notification. We first make sure that the
            focusedConnection is not null (null means the notification is about to close) and that
            there isn't any incoming call (the active call notification shouldn't show the
            incoming call notification UI) to prevent the last update from being wonky. Although
            the service self-stops if such an end case occurs, this a safety check in case the
            notification updates due an audio state change at the last second.
             */
            if (CallManager.focusedConnection.value != null) {
                contentView.apply {
                    setTextViewText(R.id.active_notification_title, notificationTitle)
                    setTextViewText(R.id.active_notification_text, notificationText)
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