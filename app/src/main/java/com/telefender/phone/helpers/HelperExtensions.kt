package com.telefender.phone.helpers

// Converts days (given as receiver Int) to milliseconds.
fun Int.daysToMilli() : Long {
    return this * 86400000L
}