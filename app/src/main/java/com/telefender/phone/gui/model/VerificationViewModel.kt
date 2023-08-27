package com.telefender.phone.gui.model

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class VerificationViewModel(app: Application) : AndroidViewModel(app) {

    @SuppressLint("StaticFieldLeak")
    private val applicationContext = getApplication<Application>().applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)

    private var _manualInstanceNumber: String? = null
    val manualInstanceNumber: String?
        get() = _manualInstanceNumber

    fun setManualInstanceNumber(number: String) {
        _manualInstanceNumber = number
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