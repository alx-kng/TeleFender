package com.telefender.phone.call_related

import android.os.Build
import android.telecom.Call


private val OUTGOING_CALL_STATES = arrayOf(
    Call.STATE_CONNECTING,
    Call.STATE_DIALING,
    Call.STATE_SELECT_PHONE_ACCOUNT
)

fun Call?.toSimpleString(): String? {
    return if (this != null) {
        "{ Call | number: ${this.number()}, state: ${stateString()}, createTime = ${createTime()} }"
    } else {
        null
    }
}

fun Call?.number(): String? {
    return this?.details?.handle?.schemeSpecificPart
}

// TODO: Should we fix things to show call state as null if call is null?
fun Call?.stateCompat(): Int {
    return when {
        this == null -> Call.STATE_DISCONNECTED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> details.state
        else -> state
    }
}

fun Call?.stateString(): String {
    return this.stateCompat().toStateString()
}


fun Int?.toStateString(): String = when (this) {
    Call.STATE_NEW -> "New"
    Call.STATE_RINGING -> "Ringing"
    Call.STATE_DIALING -> "Dialing"
    Call.STATE_CONNECTING -> "Connecting"
    Call.STATE_ACTIVE -> "Active"
    Call.STATE_HOLDING -> "Holding"
    Call.STATE_AUDIO_PROCESSING -> "Audio Processing"
    Call.STATE_DISCONNECTED -> "Disconnected"
    Call.STATE_DISCONNECTING -> "Disconnecting"
    null -> "null"
    else -> "Unknown state $this"
}

/*
TODO: Apparently, call duration clock shouldn't depend on connectTimeMillis, but it works for now,
 so look into it later.
 */
fun Call?.callDurationSEC(): Int {
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

fun Call?.callDurationMILLI(): Long {
    return if (this != null) {
        val connectTimeMillis = details.connectTimeMillis
        if (connectTimeMillis == 0L) {
            return 0
        }
        System.currentTimeMillis() - connectTimeMillis
    } else {
        0
    }
}

fun Call?.createTime(): Long {
    return if (this != null) {
        details.creationTimeMillis
    } else {
        -1
    }
}

fun Call?.connectTime(): Long {
    return if (this != null) {
        details.connectTimeMillis
    } else {
        -1
    }
}

fun Call.isOutgoing(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        details.callDirection == Call.Details.DIRECTION_OUTGOING
    } else {
        OUTGOING_CALL_STATES.contains(stateCompat())
    }
}

fun Call.hasCapability(capability: Int): Boolean = (details.callCapabilities and capability) != 0

fun Call?.isConference(): Boolean = this?.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true
