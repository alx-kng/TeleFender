package com.dododial.phone.data.dodo_database

object MiscHelpers {
    open fun cleanNumber(number : String?) : String? {
        return number?.replace("(\\s|\\(|\\)|-|\\.|,)".toRegex(), "")
    }
}