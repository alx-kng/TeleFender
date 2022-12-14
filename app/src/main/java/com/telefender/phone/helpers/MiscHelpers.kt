package com.telefender.phone.helpers

object MiscHelpers {

    const val DEBUG_LOG_TAG = "TELE_DEBUG"

    fun cleanNumber(number : String?) : String? {
        return number?.replace("(\\s|\\(|\\)|-|\\.|,)".toRegex(), "")
    }
}