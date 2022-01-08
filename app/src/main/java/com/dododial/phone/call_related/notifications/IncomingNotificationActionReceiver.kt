package com.dododial.phone.call_related.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dododial.phone.call_related.OngoingCall

class IncomingNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        var dummyIncomingServiceIntent = Intent(context, DummyForegroundIncomingCallService::class.java)

        var answer_action = intent?.getStringExtra("answer_value")
        Log.i("MESSAGE FROM INCOMING NOTIFICATION RECEIVER", answer_action!!)
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
                Log.i("ERROR FROM INCOMING NOTIFICATION RECEIVER", "answer_action doesn't have correct value")
            }
        }
    }
}