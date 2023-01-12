package com.telefender.phone.call_related

import android.content.Context
import com.telefender.phone.data.tele_database.entities.Analyzed
import com.telefender.phone.data.tele_database.entities.AnalyzedNumber
import com.telefender.phone.data.tele_database.entities.Parameters
import com.telefender.phone.helpers.TeleHelpers
import timber.log.Timber

object RuleChecker {

    /**
     * TODO: Preliminary algo:
     *  - Double check (specifically smsVerified)
     *  - Put in possible stuff for NotifyList.
     *
     * Returns whether or not number should be allowed. Read "TeleFender - Algorithm Overview" for
     * more info.
     */
    fun isSafe(context: Context, number: String?): Boolean {

        val normalizedNumber = TeleHelpers.normalizedNumber(number)
            ?: TeleHelpers.bareNumber(number)

        if (normalizedNumber == TeleHelpers.UNKNOWN_NUMBER) return false

        val analyzedNumber: AnalyzedNumber
        val analyzed: Analyzed
        val parameters: Parameters

        try {
            analyzedNumber = TeleHelpers.getAnalyzedNumber(context, normalizedNumber)!!
            analyzed = analyzedNumber.getAnalyzed()
            parameters = TeleHelpers.getParameters(context)!!
        } catch (e: Exception) {
            /*
            Allow number through if some huge internal error occurs so that the user isn't hard locked
            from receiving outside calls.
             */
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: isSafe() - MAYDAY MAYDAY! ${e.message}")
            e.printStackTrace()
            return true
        }

        if (analyzed.isBlocked) return false

        if (analyzed.numSharedContacts > 0) return true

        if (analyzed.numTreeContacts > 0) return true

        if (analyzed.markedSafe) return true

        if (analyzed.smsVerified) return true

        if (analyzed.maxIncomingDuration >= parameters.incomingGate) return true

        if (analyzed.maxOutgoingDuration >= parameters.outgoingGate) return true

        return false
    }
}
