package com.telefender.phone.call_related

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telecom.CallAudioState
import androidx.lifecycle.MutableLiveData
import com.telefender.phone.helpers.TeleHelpers
import timber.log.Timber

object AudioHelpers {

    val muteStatus : MutableLiveData<Boolean> = MutableLiveData(false)
    val speakerStatus: MutableLiveData<Boolean> = MutableLiveData(false)

    fun ringerSilent(context: Context?, silent: Boolean) {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val mode = if (silent) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
        audioManager.ringerMode = mode
    }

    fun setMute(context: Context?, setMute: Boolean) {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = setMute
        audioManager.mode = AudioManager.MODE_NORMAL //Look at this. Not for in call "supposedly"

        muteStatus.value = audioManager.isMicrophoneMute
    }

    fun toggleMute(context: Context?) {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = !audioManager.isMicrophoneMute
        audioManager.mode = AudioManager.MODE_NORMAL

        muteStatus.value = audioManager.isMicrophoneMute
    }

    fun setSpeaker(context: Context?, setSpeaker: Boolean) {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val earpiece = CallAudioState.ROUTE_WIRED_OR_EARPIECE
        val speaker = CallAudioState.ROUTE_SPEAKER

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            if (setSpeaker) {
                CallService.context?.setAudioRoute(speaker)
            } else {
                CallService.context?.setAudioRoute(earpiece)
            }
        } else {
            audioManager.isSpeakerphoneOn = setSpeaker
        }

        speakerStatus.value = setSpeaker
    }

    fun toggleSpeaker(context: Context?) {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val earpiece = CallAudioState.ROUTE_WIRED_OR_EARPIECE
        val speaker = CallAudioState.ROUTE_SPEAKER

        val speakerOn : Boolean
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            if (!audioManager.isSpeakerphoneOn) {
                CallService.context?.setAudioRoute(speaker)
                speakerOn = true
            } else {
                CallService.context?.setAudioRoute(earpiece)
                speakerOn = false
            }
        } else {
            speakerOn = !audioManager.isSpeakerphoneOn
            audioManager.isSpeakerphoneOn = speakerOn
        }

        speakerStatus.value = speakerOn
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: speaker: ${speakerStatus.value}")
    }

}