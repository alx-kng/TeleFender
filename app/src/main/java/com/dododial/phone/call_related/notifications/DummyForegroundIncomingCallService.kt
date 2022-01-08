package com.dododial.phone.call_related.notifications

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telecom.Call
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dododial.phone.call_related.CallActivity
import com.dododial.phone.call_related.OngoingCall
import com.dododial.phone.R

class DummyForegroundIncomingCallService : Service() {

    private val CHANNEL_ID = "alxkng5737"
    private val NOTIFICATION_ID = 12345678

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notificationCreator().build())
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
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

        val answerButton = Intent(this, IncomingNotificationActionReceiver::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("answer_value", "answer")
        }
        val pendingAnswerIntent = PendingIntent.getBroadcast(this, 1, answerButton, 0)


        val declineButton = Intent(this, IncomingNotificationActionReceiver::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("answer_value", "decline")
        }
        val pendingDeclineIntent = PendingIntent.getBroadcast(this, 2, declineButton, 0)


        val contentView = RemoteViews(packageName, R.layout.incoming_call_notification)
        contentView.setImageViewResource(R.id.image, android.R.mipmap.sym_def_app_icon)
        contentView.setTextViewText(R.id.incoming_notification_text, number)
        contentView.setOnClickPendingIntent(R.id.answer_button, pendingAnswerIntent)
        contentView.setOnClickPendingIntent(R.id.decline_button, pendingDeclineIntent)

        when {
            OngoingCall.call?.state ?: Call.STATE_DISCONNECTING == Call.STATE_RINGING -> {
                contentView.setTextViewText(R.id.incoming_notification_title, "Incoming Call")
            }
            OngoingCall.call?.state ?: Call.STATE_DISCONNECTED == Call.STATE_ACTIVE -> {
                contentView.setTextViewText(R.id.incoming_notification_title, "Active Call")
            }
            else -> contentView.setTextViewText(R.id.incoming_notification_title, "Something went wrong!")

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
        .setColor(ContextCompat.getColor(this, com.dododial.phone.R.color.design_default_color_secondary))
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