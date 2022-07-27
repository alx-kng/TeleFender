package com.dododial.phone.call_related

import android.os.Build
import android.telecom.Call


private val OUTGOING_CALL_STATES = arrayOf(
    Call.STATE_CONNECTING,
    Call.STATE_DIALING,
    Call.STATE_SELECT_PHONE_ACCOUNT
)

fun Call?.getStateCompat(): Int {
    return when {
        this == null -> Call.STATE_DISCONNECTED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> details.state
        else -> state
    }
}

fun Call?.getCallDuration(): Int {
    return if (this != null) {
        val connectTimeMillis = details.connectTimeMillis
        if (connectTimeMillis == 0L) {
            return 0
        }
        ((System.currentTimeMillis() - connectTimeMillis) / 1000).toInt()
    } else {
        0
    }
}

fun Call?.number(): String? {
    return this?.details?.handle?.schemeSpecificPart
}

fun Call.isOutgoing(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        details.callDirection == Call.Details.DIRECTION_OUTGOING
    } else {
        OUTGOING_CALL_STATES.contains(getStateCompat())
    }
}

fun Call.hasCapability(capability: Int): Boolean = (details.callCapabilities and capability) != 0

fun Call?.isConference(): Boolean = this?.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true
