package com.telefender.phone.gui.model

import android.telecom.Call
import androidx.lifecycle.*
import com.telefender.phone.call_related.CallManager
import com.telefender.phone.call_related.callDurationSEC
import com.telefender.phone.call_related.getStateCompat
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class InCallViewModel : ViewModel() {

    var singleMode = true

    private val _firstDuration = MutableLiveData(0)
    val firstDuration : LiveData<String> = Transformations.map(_firstDuration) { seconds ->
        secondsToTime(seconds)
    }

    private val _secondDuration = MutableLiveData(0)
    val secondDuration : LiveData<String> = Transformations.map(_secondDuration) { seconds ->
        secondsToTime(seconds)
    }

    init {
        callDurationUpdater()
    }

    /**
     * TODO: Sometimes duration doesn't update.
     *
     * Updates call duration.
     */
    fun callDurationUpdater() {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                while (CallManager.focusedCall != null) {
                    delay(250)

                    if (singleMode) {
                        _firstDuration.postValue(CallManager.focusedCall.callDurationSEC())
                    } else {
                        val orderedConnections = CallManager.orderedConnections()

                        if (orderedConnections.isNotEmpty()) {
                            _firstDuration.postValue(orderedConnections[0]?.call.callDurationSEC())
                        }

                        if (orderedConnections.size == 2) {
                            _secondDuration.postValue(orderedConnections[1]?.call.callDurationSEC())
                        }
                    }
                }
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: OUT OF CALL DURATION!")
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