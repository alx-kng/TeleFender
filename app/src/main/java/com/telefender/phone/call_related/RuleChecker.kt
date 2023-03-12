package com.telefender.phone.call_related

import android.content.Context
import com.telefender.phone.App
import com.telefender.phone.data.server_related.RequestWrappers
import com.telefender.phone.data.tele_database.background_tasks.workers.SMSVerifyScheduler
import com.telefender.phone.data.tele_database.entities.*
import com.telefender.phone.helpers.TeleHelpers
import com.telefender.phone.permissions.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

object RuleChecker {

    /**
     * TODO: Preliminary algo:
     *  - Double check (specifically smsVerified)
     *  -
     *  - Put in stuff for (temp) NotifyItem.
     *  -
     *  - Check if we should create new scope or use applicationScope here.
     *  -
     *  - In the smsImmediateWaitTime, when there's no allow / unallow decision yet, the ringer
     *    still rings on silence / block mode. We should probably preemptively put the phone on
     *    silent during those two seconds, although for most users without the Do not disturb
     *    permissions, we will probably only be able to put the phone on vibrate. -> mostly fixed
     *  -
     *  - Double check allow mode stuff.
     *  -
     *  - Can make full sync test better maybe?
     *  -
     *  - Probably need Do Not Disturb for premium users, otherwise the silence mode and block
     *    mode will always have two seconds of ringing before block / silence.
     *  -
     *  - Check for premium mode?
     *
     * Returns whether or not number should be allowed. Read "TeleFender - Algorithm Overview" for
     * more info.
     */
    fun isSafe(context: Context, number: String?): Boolean {

        //Requires that app is initialized and setup before using algorithm.
        if (!TeleHelpers.hasValidStatus(context)) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: " +
                "isSafe() - Doesn't have valid status to use algorithm!")

            return true
        }

        val repository = (context.applicationContext as App).repository
        val applicationScope = (context.applicationContext as App).applicationScope

        val normalizedNumber = TeleHelpers.normalizedNumber(number)
            ?: TeleHelpers.bareNumber(number)

        if (normalizedNumber == TeleHelpers.UNKNOWN_NUMBER) return false

        val analyzedNumber: AnalyzedNumber
        val analyzed: Analyzed
        val parameters: Parameters
        val storedMap: StoredMap

        try {
            analyzedNumber = TeleHelpers.getAnalyzedNumber(context, normalizedNumber)!!
            analyzed = analyzedNumber.getAnalyzed()
            parameters = TeleHelpers.getParameters(context)!!
            storedMap = TeleHelpers.getStoredMap(context)!!
        } catch (e: Exception) {
            /*
            Allow number through if some huge internal error occurs so that the user isn't hard locked
            from receiving outside calls.
             */
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: isSafe() - Core data problem! ${e.message}")
            e.printStackTrace()
            return true
        }

        /*
         Checking the last full sync time allows us to decide whether or not the database is
         up-to-date enough to be used for algorithm. This is most useful for calls during the
         initialization stage.
         */
        if (storedMap.lastLogFullSyncTime == 0L || storedMap.lastContactFullSyncTime == 0L) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: " +
                "isSafe() - Can't use algorithm since we haven't completed a full sync yet!")

            return true
        }

        if (analyzed.isBlocked) return false

        if (analyzed.numSharedContacts > 0) return true

        if (analyzed.numTreeContacts > 0) return true

        if (analyzed.markedSafe) return true

        if (analyzed.smsVerified) return true

        if (analyzed.maxIncomingDuration >= parameters.incomingGate) return true

        if (analyzed.maxOutgoingDuration >= parameters.outgoingGate) return true

        /*
        If the checks reach here, and the current call mode is ALLOW_MODE, then we deem the number
        as unsafe. However, since the call is going through anyways, there's no need to SMS verify.
         */
        if (CallManager.currentMode == HandleMode.ALLOW_MODE) return false

        // TODO: Check here if in temp NotifyItem and if satisfies max request time constraint.`

        // Used to silence the ringer during the wait time for the SMS verify result.
        AudioHelpers.setRingerMode(context, RingerMode.SILENT)

        applicationScope.launch {
            RequestWrappers.smsVerify(
                context = context,
                repository = repository,
                scope = applicationScope,
                number = normalizedNumber
            )
        }

        // If there is no verify result within 2 seconds, then unallow number. Otherwise, allow.
        return runBlocking(Dispatchers.Default) {
            delay(parameters.smsImmediateWaitTime)

            val newAnalyzed = repository.getAnalyzedNum(normalizedNumber)?.getAnalyzed()
                ?: return@runBlocking false

            /*
            If not verified, then send another SMS request to server after a around minute. This is
            mostly for server load optimization.
             */
            if (!newAnalyzed.smsVerified) {
                SMSVerifyScheduler.initiateSMSVerifyWorker(context, normalizedNumber)
            }

            return@runBlocking newAnalyzed.smsVerified
        }
    }
}
