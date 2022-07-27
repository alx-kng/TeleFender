package com.dododial.phone

import android.icu.util.UniversalTimeScale.toLong
import android.util.Log

object RuleChecker {

    fun isSafe(numberString: String?): Boolean {

        // For null conference host
        if (numberString == null) {
            return true
        }

        // Don't remember what substring was for.
        val number = numberString.toLong()
        Log.i("DODODEBUG", number.toString())

        return number != 7167102601L
    }
}
