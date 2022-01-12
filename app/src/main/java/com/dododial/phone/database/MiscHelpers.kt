package com.dododial.phone.database

object MiscHelpers {
    open fun cleanNumber(number : String?) : String? {
        return number?.replace("(\\s|\\(|\\)|-|\\.|,)".toRegex(), "")
    }
}