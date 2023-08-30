package com.telefender.phone.gui.model

import android.view.View
import androidx.lifecycle.*
import com.telefender.phone.misc_helpers.DBL
import timber.log.Timber

class DialerViewModel : ViewModel() {

    private val _dialNumber = MutableLiveData("")
    val dialNumber: LiveData<String> = _dialNumber

    private var _fromInCall = false
    val fromInCall: Boolean
        get() = _fromInCall

    val asterisk = '✱'
    val poundSign = '#'

    val deleteVisibility: LiveData<Int> = dialNumber.map {
        if (it == "") {
            View.INVISIBLE
        } else {
            View.VISIBLE
        }
    }

    fun typeDigit(digit: Int) {
        if (digit > 9 || digit < 0) {
            Timber.i("$DBL: DialerViewModel - %s",
                "typeDigit() received digit not between 0 and 9 ")
            return
        }

        // Only check for length when not from in-call (regular dialer screen).
        if (!fromInCall && _dialNumber.value?.length!! >= 15) {
            Timber.i("$DBL: DialerViewModel - %s",
                "typeDigit() - typed number has reached maximum of 15 digits!")
            return
        }

        _dialNumber.value = _dialNumber.value + digit.toString()
    }

    fun typeSymbol(symbol: Char) {
        if (symbol != asterisk && symbol != poundSign) {
            Timber.i("$DBL: DialerViewModel - %s",
                "typeSymbol() received symbol not * or #. Received $symbol")
            return
        }

        // Only check for length when not from in-call (regular dialer screen).
        if (!fromInCall && _dialNumber.value?.length!! >= 15) {
            Timber.i("$DBL: DialerViewModel - %s",
                "typeDigit() - typed number has reached maximum of 15 digits!")
            return
        }

        _dialNumber.value = _dialNumber.value + symbol.toString()
    }

    fun setDialNumber(number: String) {
        _dialNumber.postValue(number)
    }

    fun setFromInCall(fromInCall: Boolean) {
        _fromInCall = fromInCall
    }

    fun deleteDigit() {
        if ((_dialNumber.value?.length ?: 0) > 0) {
            val lastIndex = _dialNumber.value?.length?.minus(1) ?: 0
            _dialNumber.value = _dialNumber.value?.substring(0, lastIndex)
        }
    }

    fun deleteAll(): Boolean{
        _dialNumber.value = ""
        return true
    }
}