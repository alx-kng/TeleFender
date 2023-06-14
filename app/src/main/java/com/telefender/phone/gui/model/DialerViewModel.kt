package com.telefender.phone.gui.model

import android.view.View
import androidx.lifecycle.*
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import timber.log.Timber

class DialerViewModel : ViewModel() {

    private val _dialNumber = MutableLiveData("")
    val dialNumber: LiveData<String> = _dialNumber

    val asterisk = '✱'
    val poundSign = '#'

    val deleteVisibility: LiveData<Int> = dialNumber.map {
        if (it == "") {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    fun typeDigit(digit: Int) {
        if (digit > 9 || digit < 0 || _dialNumber.value?.length!! >= 15) {
            Timber.i("$DBL: DialerViewModel - %s%s",
                "typeDigit() received digit not between 0 and 9 ",
                    "or the typed number has reached maximum of 15 digits!")
            return
        }

        _dialNumber.value = _dialNumber.value + digit.toString()
    }

    fun typeSymbol(symbol: Char) {
        if (symbol != asterisk && symbol != poundSign || _dialNumber.value?.length!! >= 15) {
            Timber.i("$DBL: DialerViewModel - %s%s",
                "typeSymbol() received symbol not * or # ",
                    "or the typed number has reached maximum of 15 digits! Received $symbol")
            return
        }

        _dialNumber.value = _dialNumber.value + symbol.toString()
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