package com.telefender.phone.helpers

import android.content.Context
import android.provider.CallLog
import android.telephony.TelephonyManager


// TODO: Consider adding retry amount to be used for entire app.
object MiscHelpers {

    const val DEBUG_LOG_TAG = "TELE_DEBUG"

    /**
     * Gets user's phone number.
     */
    fun getInstanceNumber(context: Context) : String? {
        val tMgr = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return cleanNumber(tMgr.line1Number)
    }

    /**
     * Gets rid of non-numerical symbols from number.
     */
    fun cleanNumber(number : String?) : String? {
        return number?.replace("(\\s|\\(|\\)|-|\\.|,)".toRegex(), "")
    }

    /**
     * Gets direction as normal. However, voicemail direction is also accurately returned.
     */
    fun getTrueDirection(direction: Int?, number: String) : Int? {
        return if (isVoiceMail(direction, number)) CallLog.Calls.VOICEMAIL_TYPE else direction
    }

    /**
     * TODO: Voicemail call log refine
     * TODO: Incorporate into algorithm.
     *
     * Duct tape way to check if call log is a voicemail or not. Uses the idea that
     * voicemail call logs always have a '+' in front of the number and are incoming.
     * Don't know how this changes between carriers.
     */
    fun isVoiceMail(direction: Int?, number: String) : Boolean {
        return number.isNotEmpty() && number[0] == '+' && direction == CallLog.Calls.INCOMING_TYPE
    }

    // TODO: Problem with direction string maybe? Unknown code 0?
    fun getDirectionString(directionCode: Int?) : String {
        return when (directionCode) {
            CallLog.Calls.INCOMING_TYPE -> "INCOMING"
            CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
            CallLog.Calls.MISSED_TYPE -> "MISSED"
            CallLog.Calls.VOICEMAIL_TYPE -> "VOICEMAIL"
            CallLog.Calls.REJECTED_TYPE -> "REJECTED"
            CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
            CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "EXTERNAL"
            else -> "UNKNOWN DIRECTION"
        }
    }
}