package com.telefender.phone.call_related

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telecom.CallAudioState
import androidx.lifecycle.MutableLiveData
import com.telefender.phone.helpers.TeleHelpers
import timber.log.Timber

enum class RingerMode {
    NORMAL, SILENT, VIBRATE
}

object AudioHelpers {

    val muteStatus : MutableLiveData<Boolean> = MutableLiveData(false)
    val speakerStatus: MutableLiveData<Boolean> = MutableLiveData(false)

    /**
     * TODO: Check for if user is premium and has do not disturb permissions before using ringer
     *  silent.
     *
     * Sets the ringer mode of the phone. Apparently, setting the ringer to ANY mode requires the
     * Do Not Disturb permission.
     */
    fun setRingerMode(context: Context?, ringerMode: RingerMode) {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioManager.ringerMode = when(ringerMode) {
            RingerMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
            RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
            RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
        }
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