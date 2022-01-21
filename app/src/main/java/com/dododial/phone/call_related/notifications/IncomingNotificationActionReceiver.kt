package com.dododial.phone.call_related.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dododial.phone.call_related.OngoingCall
import timber.log.Timber

class IncomingNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        var dummyIncomingServiceIntent =
            Intent(context, DummyForegroundIncomingCallService::class.java)

        var answer_action = intent?.getStringExtra("answer_value")
        Timber.i(answer_action!!)
        when (answer_action) {
            "answer" -> {
                OngoingCall.answer()
                context?.stopService(dummyIncomingServiceIntent)
            }
            "decline" -> {
                OngoingCall.hangup()
                context?.stopService(dummyIncomingServiceIntent)
            }
            else -> {
                Timber.i(
                    "answer_action doesn't have correct value"
                )
            }
        }
    }
}