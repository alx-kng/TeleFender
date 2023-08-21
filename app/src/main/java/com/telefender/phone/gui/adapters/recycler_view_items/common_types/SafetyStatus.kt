package com.telefender.phone.gui.adapters.recycler_view_items.common_types


enum class SafetyStatus(val serverString: String) {
    SPAM("Spam"), DEFAULT("Default"), SAFE("Safe")
}

/**
 * Returns the [SafetyStatus] given the [isBlocked] and [markedSafe] values for a number (stored in
 * the AnalyzedNumbers). For any valid AnalyzedNumber, [getSafetyStatus] should pretty much always
 * return a non-null [SafetyStatus], since a number cannot be both [markedSafe] and [isBlocked]
 * (this is enforced in our ExecuteAgent).
 */
fun getSafetyStatus(isBlocked: Boolean, markedSafe: Boolean) : SafetyStatus? {
    if (isBlocked && !markedSafe) return SafetyStatus.SPAM
    if (!isBlocked && markedSafe) return SafetyStatus.SAFE
    if (!isBlocked && !markedSafe) return SafetyStatus.DEFAULT

    return null
}