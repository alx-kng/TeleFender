package com.telefender.phone.call_related

import com.telefender.phone.helpers.MiscHelpers
import timber.log.Timber

object RuleChecker {

    fun isSafe(numberString: String?): Boolean {

        // For null conference host
        if (numberString == null) {
            return true
        }

        // Don't remember what substring was for.
        val number = numberString.toLong()
        Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: $number")

        return number != 7167102601L
    }
}
