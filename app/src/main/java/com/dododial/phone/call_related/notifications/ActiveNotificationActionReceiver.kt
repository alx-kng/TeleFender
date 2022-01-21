package com.dododial.phone.call_related.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import com.dododial.phone.call_related.ActiveCallStates
import com.dododial.phone.call_related.OngoingCall
import timber.log.Timber

class ActiveNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        var dummyActiveServiceIntent = Intent(context, DummyForegroundActiveCallService::class.java)

        var button_action = intent?.getStringExtra("button_value")
        Timber.i(button_action!!)

        when (button_action) {
            "hangup" -> {
                OngoingCall.hangup()
                context?.stopService(dummyActiveServiceIntent)
            }
            "mute" -> {
                ActiveCallStates.mute_status.value = ActiveCallStates.mute_status.value == false
                ActiveCallStates.adjustMuteAudio(context, ActiveCallStates.mute_status.value!!)
                Timber.i("MUTE STATUS: %s", ActiveCallStates.mute_status.toString())
            }
            "speaker" -> {
                ActiveCallStates.speaker_status.value =
                    ActiveCallStates.speaker_status.value == false
                ActiveCallStates.toggleSpeaker(context, ActiveCallStates.speaker_status.value!!)
                Timber.i("SPEAKER STATUS: %s", ActiveCallStates.speaker_status.toString())
            }
            else -> {
                Timber.i("ERROR_ACTIVE_NOTIFICATION: button_action doesn't have correct value")
            }
        }
    }
}