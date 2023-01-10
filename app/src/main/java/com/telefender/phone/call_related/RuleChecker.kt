package com.telefender.phone.call_related

import android.content.Context
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.entities.Analyzed
import com.telefender.phone.data.tele_database.entities.AnalyzedNumber
import com.telefender.phone.data.tele_database.entities.Parameters
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber

object RuleChecker {

    /**
     * TODO: Double check algo (specifically smsVerified) and put in possible stuff for NotifyList.
     *
     * Returns whether or not number should be allowed. Read "TeleFender - Algorithm Overview" for
     * more info.
     */
    fun isSafe(context: Context, number: String?): Boolean {

        val normalizedNumber = MiscHelpers.normalizedNumber(number)
            ?: MiscHelpers.bareNumber(number)

        if (normalizedNumber == MiscHelpers.UNKNOWN_NUMBER) return false

        val analyzedNumber: AnalyzedNumber
        val analyzed: Analyzed
        val parameters: Parameters

        try {
            analyzedNumber = MiscHelpers.getAnalyzedNumber(context, normalizedNumber)!!
            analyzed = analyzedNumber.getAnalyzed()
            parameters = MiscHelpers.getParameters(context)
        } catch (e: Exception) {
            /*
            Allow number through if some huge internal error occurs so that the user isn't hard locked
            from receiving outside calls.
             */
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: isSafe() - MAYDAY MAYDAY! ${e.message}")
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
