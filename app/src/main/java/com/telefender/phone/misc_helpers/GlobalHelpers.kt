package com.telefender.phone.misc_helpers

import android.content.Context
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.random.Random


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

// Gets a UUID-like Long. Mostly used for RecyclerView adapters.
fun getUniqueLong() : Long =
    (Instant.now().toEpochMilli() shl 20) or (Random.nextLong() and ((1L shl 20) - 1))