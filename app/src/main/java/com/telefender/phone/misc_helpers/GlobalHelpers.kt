package com.telefender.phone.misc_helpers

import android.content.Context
import kotlin.math.roundToInt


/***********************************************************************************************
 * Helper functions / extensions that are basic / unique / commonly used enough to be globally
 * available.
 **********************************************************************************************/

// Converts months (given as receiver Int) to milliseconds.
fun Int.monthsToMilli() : Long {
    return this * 2629800000L
}

// Converts days (given as receiver Int) to milliseconds.
fun Int.daysToMilli() : Long {
    return this * 86400000L
}

// Converts hours (given as receiver Int) to milliseconds.
fun Int.hoursToMilli() : Long {
    return this * 3600000L
}

// Converts minutes (given as receiver Int) to milliseconds.
fun Int.minutesToMilli() : Long {
    return this * 60000L
}

// Converts dp to pixels
fun Context.dpToPx(dp: Int): Int {
    val density: Float = resources.displayMetrics.density
    return (dp * density).roundToInt()
}