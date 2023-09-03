package com.telefender.phone.gui.model

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.*


class VerificationViewModel(app: Application) : AndroidViewModel(app) {

    @SuppressLint("StaticFieldLeak")
    private val applicationContext = getApplication<Application>().applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)

    private val resendStartCount = 20

    private var _manualInstanceNumber: String? = null
    val manualInstanceNumber: String?
        get() = _manualInstanceNumber

    private val _resendCountDown = MutableLiveData(resendStartCount)
    val resendCountDown : LiveData<String> = _resendCountDown.map { seconds ->
        "${seconds}s"
    }

    fun setManualInstanceNumber(number: String) {
        _manualInstanceNumber = number
    }

    suspend fun startCountDown() {
        _resendCountDown.postValue(resendStartCount)

        var pseudoCounter = resendStartCount
        while (pseudoCounter > 0) {
            delay(1000)
            pseudoCounter--
            _resendCountDown.postValue(pseudoCounter)
        }
    }
}

class VerificationViewModelFactory(
    private val app: Application,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VerificationViewModel::class.java)) {
            return VerificationViewModel(app) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}