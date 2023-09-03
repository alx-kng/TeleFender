package com.telefender.phone.gui.model

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.*
import com.telefender.phone.permissions.Permissions
import kotlinx.coroutines.*


class PermissionViewModel(app: Application) : AndroidViewModel(app) {

    @SuppressLint("StaticFieldLeak")
    private val applicationContext = getApplication<Application>().applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)

    private var _isDefaultDialer = MutableLiveData<Boolean>()
    val isDefaultDialer : LiveData<Boolean> = _isDefaultDialer

    private var _hasDoNotDisturb = MutableLiveData<Boolean>()
    val hasDoNotDisturb : LiveData<Boolean> = _hasDoNotDisturb

    val isDefaultDialerDirect : Boolean
        get() = _isDefaultDialer.value ?: Permissions.isDefaultDialer(applicationContext)

    val hasDoNotDisturbDirect : Boolean
        get() = _hasDoNotDisturb.value ?: Permissions.hasDoNotDisturbPermission(applicationContext)

    init {
        _isDefaultDialer.value = Permissions.isDefaultDialer(applicationContext)
        _hasDoNotDisturb.value = Permissions.hasDoNotDisturbPermission(applicationContext)
    }

    fun setIsDefaultDialer(value: Boolean) {
        _isDefaultDialer.value = value
    }

    fun startCheckingDoNotDisturb() {
        scope.launch {
            while (true) {
                val newStatus = Permissions.hasDoNotDisturbPermission(applicationContext)
                if (newStatus != _hasDoNotDisturb.value) {
                    _hasDoNotDisturb.postValue(newStatus)
                }

                delay(1000)
            }
        }
    }

    fun stopCheckingDoNotDisturb() {
        scope.cancel()
    }
}

class PermissionViewModelFactory(
    private val app: Application,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PermissionViewModel::class.java)) {
            return PermissionViewModel(app) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}