package com.dododial.phone.call_related

import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import com.dododial.phone.RuleChecker
import com.dododial.phone.call_related.OngoingCall.call
import com.dododial.phone.call_related.notifications.DummyForegroundActiveCallService
import com.dododial.phone.call_related.notifications.DummyForegroundIncomingCallService
import timber.log.Timber


class CallService : InCallService() {

    var ruleCheck = RuleChecker()
    var placeholderMode = 2
    var silencedHangUpDelay: Long = 10000L
    private val CHANNEL_ID = "alxkng5737"
    private val NOTIFICATION_ID = 12345678

    override fun onCallAdded(call: Call) {

        CallManager.addCall(call)
        val number = call?.details?.handle?.toString() ?: "Conference"

        var dummyIncomingServiceIntent = Intent(this, DummyForegroundIncomingCallService::class.java)
        ActiveCallStates.callServiceContext = this

//        if (ruleCheck.allowChecker(number)) {
        if (true) {

            adjustAudio(false)
            CallActivity.start(this, call)
//            ContextCompat.startForegroundService(this, dummyIncomingServiceIntent)
        } else {

            if (placeholderMode == 0 || placeholderMode == 1) {
                adjustAudio(true)
            }
            when (placeholderMode) {
                // Full block identified spam
                0 -> {
                    CallManager.hangup()
                    adjustAudio(false)
                }
                // Silence identified spam (may be with vibrate if user has the "Vibrate while Ringing" option selected in settings
                1 -> {
                    CallActivity.start(this, call)
                    ContextCompat.startForegroundService(this, dummyIncomingServiceIntent)
                    val r = Runnable {
                        if (call.state == Call.STATE_CONNECTING || call.state == Call.STATE_ACTIVE) {
                            Timber.i("Silence Block Action: No block action taken because call was answered by user.")
                        } else {
                            if (call.state != Call.STATE_DISCONNECTED && call.state != Call.STATE_DISCONNECTING) {
                                OngoingCall.hangup()
                                Timber.i("Silence Block Action: Block action was taken because call was not answered or disconnected by user.")
                            }
                        }

                        var r2 = Runnable {
                            adjustAudio(false)
                        }

                        val handler = Handler()
                        handler.postDelayed(r2, 500)
                    }
                    val handler = Handler()
                    handler.postDelayed(r, silencedHangUpDelay)
                }
                // Full allow identified spam
                else -> {
                    adjustAudio(false)
                    CallActivity.start(this, call)
                    ContextCompat.startForegroundService(this, dummyIncomingServiceIntent)
                }
            }
        }
    }


    override fun onCallRemoved(call: Call) {
//        var dummyIncomingServiceIntent = Intent(this, DummyForegroundIncomingCallService::class.java)
//        var dummyActiveServiceIntent = Intent(this, DummyForegroundActiveCallService::class.java)
//
//        stopService(dummyIncomingServiceIntent)
//        stopService(dummyActiveServiceIntent)
        ActiveCallStates.callServiceContext = null

        //notificationCanceler()
        CallManager.removeCall(call)
    }

    fun adjustAudio(setMute: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val adJustMute: Int = if (setMute) {
            AudioManager.RINGER_MODE_SILENT
        } else {
            AudioManager.RINGER_MODE_NORMAL
        }
        //audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, adJustMute, 0)
        //audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, adJustMute, 0)
        //audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adJustMute, 0)
        audioManager.ringerMode = adJustMute
//            audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
        //audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, adJustMute, 0)

    }

    // Doesn't work currently. There may be another workaround to vibrate
    private fun vibrateMode(vibrate: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (vibrate) {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        } else {
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        }
    }
}