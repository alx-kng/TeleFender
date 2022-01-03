package com.github.arekolek.phone.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.telecom.Call
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.github.arekolek.phone.ActiveCallStates
import com.github.arekolek.phone.CallActivity
import com.github.arekolek.phone.OngoingCall
import com.github.arekolek.phone.R

class DummyForegroundActiveCallService: Service() {

    private val CHANNEL_ID = "alxkng5737"
    private val NOTIFICATION_ID = 12345678
    private val _speakerToggle = MutableLiveData<MutableLiveData<Boolean>> (ActiveCallStates.speaker_status)
    //var speakerToggle: LiveData<Boolean> = ActiveCallStates.speaker_status
    val observer = Observer<LiveData<Boolean>> { this.updateNotification() }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notificationCreator().build())

        _speakerToggle.observeForever { observer }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        _speakerToggle.removeObserver(observer)
        stopForeground(true)
    }

    fun updateNotification() {
        val notification: Notification = notificationCreator().build()
        val mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun notificationCreator(): NotificationCompat.Builder {
        val callWithExtra: String = OngoingCall.call?.details?.handle.toString()
        val number = callWithExtra.substring(4)

        val fullScreenIntent = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            data = OngoingCall.call?.details?.handle
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(this, 0,
            fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val speakerButton = Intent(this, ActiveNotificationActionReceiver::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("button_value", "speaker")
        }
        val pendingSpeakerIntent = PendingIntent.getBroadcast(this, 3, speakerButton, 0)

        val muteButton = Intent(this, ActiveNotificationActionReceiver::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("button_value", "mute")
        }
        val pendingMuteIntent = PendingIntent.getBroadcast(this, 4, muteButton, 0)


        val hangupButton = Intent(this, ActiveNotificationActionReceiver::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("button_value", "hangup")
        }
        val pendingHangupIntent = PendingIntent.getBroadcast(this, 5, hangupButton, 0)


        var contentView = RemoteViews(packageName, R.layout.active_call_notification)
        contentView.setImageViewResource(R.id.image, android.R.mipmap.sym_def_app_icon)
        contentView.setTextViewText(R.id.active_notification_text, number)
        contentView.setOnClickPendingIntent(R.id.speaker_button, pendingSpeakerIntent)
        contentView.setOnClickPendingIntent(R.id.mute_button, pendingMuteIntent)
        contentView.setOnClickPendingIntent(R.id.hangup_button, pendingHangupIntent)

        if (ActiveCallStates.speaker_status.value!!) {
            contentView.setImageViewResource(
                R.id.speaker_button,
                R.drawable.ic_baseline_volume_off_24
            )
        } else {
            contentView.setImageViewResource(
                R.id.speaker_button,
                R.drawable.ic_baseline_volume_off_24
            )
        }

        when {
            OngoingCall.call?.state ?: Call.STATE_DISCONNECTING == Call.STATE_RINGING -> {
                contentView.setTextViewText(R.id.active_notification_title, "Incoming Call")
            }
            OngoingCall.call?.state ?: Call.STATE_DISCONNECTED == Call.STATE_ACTIVE -> {
                contentView.setTextViewText(R.id.active_notification_title, "Active Call")
            }
            else -> contentView.setTextViewText(R.id.active_notification_title, "Something went wrong!")

        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(fullScreenPendingIntent)
            .setCustomContentView(contentView)
            .setColor(ContextCompat.getColor(this, R.color.notificationGreen))
            .setColorized(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        /*.setContentText(number)
        //.setPriority(NotificationCompat.PRIORITY_MAX)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setColor(ContextCompat.getColor(this, com.github.arekolek.phone.R.color.design_default_color_secondary))
        .setColorized(true)
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        //.setCustomContentView(contentView)
        .addAction(android.R.drawable.sym_action_call, "Answer", pendingAnswerIntent)
        .addAction(android.R.drawable.sym_call_missed, "Decline", pendingDeclineIntent)*/

        /*when {
            OngoingCall.call?.state ?: Call.STATE_DISCONNECTING == Call.STATE_RINGING -> {
                notificationBuilder.setContentTitle("Incoming Call")
            }
            OngoingCall.call?.state ?: Call.STATE_DISCONNECTED == Call.STATE_ACTIVE -> {
                notificationBuilder.setContentTitle("Active Call")
            }
            else -> notificationBuilder.setContentTitle("Something went wrong")
        }*/

        return notificationBuilder
    }
}