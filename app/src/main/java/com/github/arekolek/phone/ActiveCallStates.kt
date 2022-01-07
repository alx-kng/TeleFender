package com.github.arekolek.phone

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telecom.Call
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.security.AccessController.getContext


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
        Log.i("Receiver setSpeaker:", setSpeaker.toString())
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_CALL
        audioManager.isSpeakerphoneOn = setSpeaker
        Log.i("TRUE SPEAKER STATUS: ", audioManager.isSpeakerphoneOn.toString())
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
        Log.i("TRUE SPEAKER STATUS: ", audioManager.isSpeakerphoneOn.toString())

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