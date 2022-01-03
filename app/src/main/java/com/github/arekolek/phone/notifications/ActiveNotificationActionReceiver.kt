package com.github.arekolek.phone.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.arekolek.phone.ActiveCallStates
import com.github.arekolek.phone.OngoingCall

class ActiveNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        var dummyActiveServiceIntent = Intent(context, DummyForegroundActiveCallService::class.java)

        var button_action = intent?.getStringExtra("button_value")
        Log.i("ACTIVE_NOTIFICATION", button_action)

        when (button_action) {
            "hangup" -> {
                OngoingCall.hangup()
                context?.stopService(dummyActiveServiceIntent)
            }
            "mute" -> {
                ActiveCallStates.mute_status = ActiveCallStates.mute_status == false
                ActiveCallStates.adjustMuteAudio(context, ActiveCallStates.mute_status)
            }
            "speaker" -> {
                ActiveCallStates.speaker_status.value = ActiveCallStates.speaker_status.value == false
                ActiveCallStates.toggleSpeaker(context, ActiveCallStates.speaker_status.value!!)
                Log.i("SPEAKER STATUS: ", ActiveCallStates.speaker_status.toString())
            }
            else -> {
                Log.i("ERROR_ACTIVE_NOTIFICATION", "button_action doesn't have correct value")
            }
        }
    }
}