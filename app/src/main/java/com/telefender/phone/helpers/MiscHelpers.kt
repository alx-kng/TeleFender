package com.telefender.phone.helpers

import android.content.Context
import android.provider.CallLog
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import timber.log.Timber


// TODO: Consider adding retry amount to be used for entire app.
object MiscHelpers {

    const val DEBUG_LOG_TAG = "TELE_DEBUG"
    const val INVALID_NUMBER = "NULL / INVALID NUMBER"

    /**
     * Gets user's phone number.
     */
    fun getInstanceNumber(context: Context) : String {
        val tMgr = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val number = tMgr.line1Number
        return normalizedNumber(number) ?: bareNumber(number)
    }

    /**
     * TODO: Maybe dynamically get country code later.
     * TODO: Maybe combine normalizedNumber() and bareNumber()?
     * TODO: Maybe we should let bareNumber() handle numbers with # sign?
     *
     * Puts number in normalized E164 form (assuming US country code). If the number is invalid,
     * an invalid message string is returned instead of null.
     */
    fun normalizedNumber(number : String?) : String? {
        // Let bareNumber() handle # sign so that numbers like #225 aren't converted to +1225
        if (number?.contains('#') == true) return null

        val normalizedNum = number?.let {
            try {
                val phoneUtil = PhoneNumberUtil.getInstance()
                val protoNum = phoneUtil.parse(it, "US")
                phoneUtil.format(protoNum, PhoneNumberUtil.PhoneNumberFormat.E164)
            } catch (e: Exception) {
                e.printStackTrace()
                Timber.i("$DEBUG_LOG_TAG: $number is either invalid or wonky!!")
                null
            }
        }
        return normalizedNum
    }

    /**
     * TODO: Should we even handle / display numbers like #225? It seems like when you call them
     *  through the default phone app, they just open a webpage.
     *
     * Pseudo normalized number used in case it's not possible to normalize number.
     */
    fun bareNumber(number: String?) : String {
        return number?.replace("(\\s|\\(|\\)|-|\\.|,)".toRegex(), "") ?: INVALID_NUMBER
    }

    /**
     * Gets direction as normal. However, voicemail direction is also accurately returned.
     * NOTE: Requires non-normalized raw number.
     */
    fun getTrueDirection(direction: Int, rawNumber: String) : Int {
        return if (isVoiceMail(direction, rawNumber)) CallLog.Calls.VOICEMAIL_TYPE else direction
    }

    /**
     * TODO: Voicemail call log refine
     * TODO: Incorporate into algorithm.
     *
     * Duct tape way to check if call log is a voicemail or not. Uses the idea that
     * voicemail call logs always have a '+' in front of the number and are incoming.
     * Don't know how this changes between carriers.
     * NOTE: Requires non-normalized raw number.
     */
    fun isVoiceMail(direction: Int?, rawNumber: String) : Boolean {
        return rawNumber.isNotEmpty() && rawNumber[0] == '+' && direction == CallLog.Calls.INCOMING_TYPE
    }

    // TODO: Problem with direction string maybe? Unknown code 0?
    fun getDirectionString(directionCode: Int) : String {
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