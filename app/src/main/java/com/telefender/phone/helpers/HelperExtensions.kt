package com.telefender.phone.helpers

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