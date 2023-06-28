package com.telefender.phone.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.telefender.phone.call_related.CallManager
import com.telefender.phone.misc_helpers.DBL
import timber.log.Timber


class IncomingCallNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val service = IncomingCallService.context

        when (val action = intent?.getStringExtra("action")) {
            IncomingCallService.ANSWER_ACTION -> {
                if (service != null) {
                    service.answer(fromActivity = false)
                } else {
                    CallManager.answer()
                }
            }
            IncomingCallService.HANGUP_ACTION -> {
                if (service != null) {
                    service.hangup(fromActivity = false)
                } else {
                    CallManager.hangup()
                }
            }
            else -> Timber.e("$DBL: IncomingCallNotificationReceiver - %s",
                "No action match! action = $action")
        }
    }
}