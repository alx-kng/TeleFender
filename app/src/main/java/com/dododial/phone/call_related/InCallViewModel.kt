package com.dododial.phone.call_related

import android.os.Build.VERSION_CODES.S
import android.telecom.Call
import androidx.lifecycle.*
import kotlinx.coroutines.*

class InCallViewModel : ViewModel() {

    private val _duration = MutableLiveData(0)
    val duration : LiveData<String> = Transformations.map(_duration) { seconds ->
        secondsToTime(seconds)
    }

    init {
        callDurationUpdater()
    }

    /**
     * Updates call duration.
     */
    fun callDurationUpdater() {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                while (CallManager.focusedCall != null) {
                    delay(500)
                    _duration.postValue(CallManager.focusedCall.getCallDuration())
                }
            }
        }
    }

    private fun secondsToTime(totalSeconds: Int): String{

        // Don't display time if call hasn't connected yet.
        if (CallManager.focusedCall.getStateCompat() == Call.STATE_CONNECTING
            || CallManager.focusedCall.getStateCompat() == Call.STATE_DIALING) {
            return "Calling..."
        }

        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val hoursString = if (hours == 0) {
            ""
        } else if (hours / 10 == 0) {
            "0${hours}:"
        } else {
            "${hours}:"
        }

        val minutesString = if (minutes / 10 == 0) {
            "0${minutes}:"
        } else {
            "${minutes}:"
        }

        val secondsString = if (seconds / 10 == 0) {
            "0${seconds}"
        } else {
            "$seconds"
        }

        return "${hoursString}${minutesString}${secondsString}"
    }
}