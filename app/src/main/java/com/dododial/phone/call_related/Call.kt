package com.dododial.phone.call_related

import android.os.Build
import android.telecom.Call
import timber.log.Timber


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

fun Int.toStateString(): String = when (this) {
    Call.STATE_NEW -> "NEW"
    Call.STATE_RINGING -> "RINGING"
    Call.STATE_DIALING -> "DIALING"
    Call.STATE_ACTIVE -> "ACTIVE"
    Call.STATE_HOLDING -> "HOLDING"
    Call.STATE_DISCONNECTED -> "DISCONNECTED"
    Call.STATE_CONNECTING -> "CONNECTING"
    Call.STATE_DISCONNECTING -> "DISCONNECTING"
    Call.STATE_SELECT_PHONE_ACCOUNT -> "SELECT_PHONE_ACCOUNT"
    else -> {
        Timber.w("Unknown state ${this}")
        "UNKNOWN"
    }
}

fun Call?.stateString(): String {
    return this.getStateCompat().toStateString()
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

fun Call?.connectTime(): Long {
    return if (this != null) {
        details.connectTimeMillis
    } else {
        -1
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
