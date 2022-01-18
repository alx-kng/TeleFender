package com.dododial.phone.call_related

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.lifecycle.MutableLiveData
import timber.log.Timber


@SuppressLint("StaticFieldLeak")
object ActiveCallStates {

    //var speaker_status: Boolean = false
    val speaker_status: MutableLiveData<Boolean> = MutableLiveData(false)
    val mute_status : MutableLiveData<Boolean> = MutableLiveData(false)
    var callServiceContext: InCallService? = null //tiny imperceptible small not a problem memory leak?

    fun adjustMuteAudio(context: Context?, setMute: Boolean) {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = setMute
        audioManager.mode = AudioManager.MODE_NORMAL //Look at this. Not for in call "supposedly"
    }

    fun adjustSpeakerAudio(context: Context?, setSpeaker: Boolean) { // AINT WORK NOW, BUT JUST IN CASE
        Timber.i("Receiver setSpeaker: %s", setSpeaker.toString())
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_CALL
        audioManager.isSpeakerphoneOn = setSpeaker
        Timber.i("TRUE SPEAKER STATUS: %s", audioManager.isSpeakerphoneOn.toString())
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    fun toggleSpeaker(context: Context?, setSpeaker: Boolean) {
        //Log.i("Receiver setSpeaker 1:", setSpeaker.toString())
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val earpiece = CallAudioState.ROUTE_WIRED_OR_EARPIECE
        val speaker = CallAudioState.ROUTE_SPEAKER
        //Log.i("Receiver setSpeaker 2 :", setSpeaker.toString())
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            if (setSpeaker) {
                //getSystemService(context.applicationContext, CallService::class.java)?.setAudioRoute(speaker)
                callServiceContext?.setAudioRoute(speaker)
            } else {
                //getSystemService(context.applicationContext, CallService::class.java)?.setAudioRoute(earpiece)
                callServiceContext?.setAudioRoute(earpiece)
            }
        } else {
            audioManager.isSpeakerphoneOn = setSpeaker
        }
        Timber.i("TRUE SPEAKER STATUS: %s", audioManager.isSpeakerphoneOn.toString())

    }

    fun audioCallMode(context: Context?, setMode: Boolean) {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (setMode) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } else {
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }
}