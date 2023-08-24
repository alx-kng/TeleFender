package com.telefender.phone.call_related

import android.content.Context
import com.telefender.phone.App
import com.telefender.phone.data.server_related.RequestWrappers
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.workers.SMSVerifyScheduler
import com.telefender.phone.data.tele_database.entities.*
import com.telefender.phone.misc_helpers.*
import kotlinx.coroutines.*
import timber.log.Timber
import java.time.Instant

object RuleChecker {

    /**
     * TODO: We need to actually reject calls from blocked contact numbers AND non-contact numbers.
     *
     * TODO: Preliminary algo:
     *  - Check if we should create new scope or use applicationScope here.
     *  -
     *  - Double check allow mode stuff.
     *  -
     *  - Can make full sync test better maybe?
     *  -
     *  - Probably need Do Not Disturb for premium users, otherwise the silence mode and block
     *    mode will always have two seconds of ringing before block / silence. --> Maybe we even
     *    need to check that app has Do Not Disturb permissions before sending SMS request,
     *    otherwise the user would see unexpected behavior.
     *  -
     *  - Check for premium mode?
     *
     * Returns whether or not number should be allowed (safety-wise) along with whether the number
     * should be flat out rejected (e.g., when a number is blocked as a contact OR non-contact).
     * Read "TeleFender - Algorithm Overview" for more info.
     */
    suspend fun isSafe(
        context: Context,
        scope: CoroutineScope,
        number: String?
    ): Pair<Boolean, Boolean> {

        //Requires that app is initialized and setup before using algorithm.
        if (!TeleHelpers.hasValidStatus(context)) {
            Timber.e("$DBL: " +
                "isSafe() - Doesn't have valid status to use algorithm!")

            return Pair(true, false)
        }

        val repository = (context.applicationContext as App).repository
        val currentTime = Instant.now().toEpochMilli()

        val normalizedNumber = TeleHelpers.normalizedNumber(number)
            ?: TeleHelpers.bareNumber(number)

        if (normalizedNumber == TeleHelpers.UNKNOWN_NUMBER) return Pair(false, false)

        val analyzedNumber: AnalyzedNumber
        val analyzed: Analyzed
        val parameters: Parameters
        val storedMap: StoredMap

        try {
            analyzedNumber = repository.getAnalyzedNum(normalizedNumber = normalizedNumber)!!
            analyzed = analyzedNumber.getAnalyzed()
            parameters = repository.getParameters()!!
            storedMap = repository.getStoredMap()!!
        } catch (e: Exception) {
            /*
            Allow number through if some huge internal error occurs so that the user isn't hard locked
            from receiving outside calls.
             */
            Timber.e("$DBL: isSafe() - Core data problem! ${e.message}")
            e.printStackTrace()
            return Pair(true, false)
        }

        /*
         Checking the last full sync time allows us to decide whether or not the database is
         up-to-date enough to be used for algorithm. This is most useful for calls during the
         initialization stage.
         */
        if (storedMap.lastLogFullSyncTime == 0L || storedMap.lastContactFullSyncTime == 0L) {
            Timber.e("$DBL: " +
                "isSafe() - Can't use algorithm since we haven't completed a full sync yet!")

            return Pair(true, false)
        }

        // Should flat out reject blocked number (regardless of whether it's a contact or non-contact)
        if (analyzed.isBlocked) return Pair(false, true)

        if (analyzed.numSharedContacts > 0) return Pair(true, false)

        if (analyzed.numTreeContacts > 0) return Pair(true, false)

        if (analyzed.markedSafe) return Pair(true, false)

        if (analyzed.maxIncomingDuration >= parameters.incomingGate) return Pair(true, false)

        if (analyzed.maxOutgoingDuration >= parameters.outgoingGate) return Pair(true, false)

        analyzed.lastFreshOutgoingTime?.let {
            if (currentTime - it < parameters.freshOutgoingExpirePeriod) return Pair(true, false)
        }

        if (analyzed.smsVerified) return Pair(true, false)

        /*
        If the checks reach here, and the current call mode is ALLOW_MODE, then we deem the number
        as unsafe. However, since the call is going through anyways, there's no need to SMS verify.
         */
        if (CallManager.currentMode == HandleMode.ALLOW_MODE) return Pair(false, false)

        // Used to silence the ringer during the wait time for the SMS verify result.
        AudioHelpers.setRingerMode(context, RingerMode.SILENT)

        // If should send sms request, then continue, otherwise just deem number unsafe.
        if (!shouldSendSMSRequest(context, repository, normalizedNumber, analyzed, parameters)) return Pair(false, false)

        scope.launch {
            RequestWrappers.smsVerify(
                context = context,
                repository = repository,
                scope = scope,
                number = normalizedNumber
            )
        }

        // If there is no verify result within 2 seconds, then unallow number. Otherwise, allow.
        delay(parameters.smsImmediateWaitTime)

        val newAnalyzed = repository.getAnalyzedNum(normalizedNumber)?.getAnalyzed()
            ?: return Pair(false, false)

        /*
        If not verified, then send another SMS request to server after a around minute. This is
        mostly for server load optimization.
         */
        if (!newAnalyzed.smsVerified) {
            SMSVerifyScheduler.initiateSMSVerifyWorker(context, normalizedNumber)
        }

        return Pair(newAnalyzed.smsVerified, false)
    }

    private suspend fun shouldSendSMSRequest(
        context: Context,
        repository: ClientRepository,
        normalizedNumber: String,
        analyzed: Analyzed,
        parameters: Parameters
    ) : Boolean {
        // If SMS verification is disabled for this device, then don't even check.
        if (!parameters.shouldVerifySMS) return false

        // We treat the current time as the informal callEpochTime for this current call.
        val currentTime = Instant.now().toEpochMilli()

        // Updated server sent window with old sent times kicked out.
        val newServerSentWindow = TeleHelpers.updatedTimesWindow(
            window = analyzed.serverSentWindow,
            windowSize = parameters.serverSentWindowSize.hoursToMilli()
        )

        // Updated notify window with old call times kicked out and new call time added in..
        val newNotifyWindow = TeleHelpers.updatedTimesWindow(
            window = analyzed.notifyWindow,
            windowSize = parameters.notifyWindowSize.daysToMilli(),
            newTime = currentTime
        )

        // Means that this number would qualify for the notify list IF this current call was added.
        val qualifies = newNotifyWindow.size >= analyzed.notifyGate

        // Updated NotifyItem for shouldRemoveNotifyItem() check. Conforms with ExecuteAgent.
        val newNotifyItem = TeleHelpers.updatedNotifyItem(
            oldNotifyItem = repository.getNotifyItem(normalizedNumber = normalizedNumber),
            callEpochDate = currentTime,
            newNotifyWindow = newNotifyWindow,
            qualifies = qualifies
        )

        if (!qualifies) {
            // Ensures that number qualifies for notify list or is on notify list before sending SMS request.
            if (newNotifyItem == null) return false

            // If number doesn't qualify and is on notify list but should be removed, then also don't send SMS request.
            if (TeleHelpers.shouldRemoveNotifyItem(newNotifyItem, parameters)) return false
        }

        // E.g., If server only sent 1 SMS in last 24 hours (where max sent is 2 in 24 hours).
        if (analyzed.serverSentWindow.size < parameters.maxServerSent) return true

        // If serverSent == maxServerSent but call is within expire period, then send SMS request.
        if (currentTime - newServerSentWindow.last() <= parameters.smsLinkExpirePeriod.minutesToMilli()) return true

        /*
        If call is after expire but client hasn't sent an after-expire request yet, then send SMS request.
        Note that the clientSentAfterExpire is changed in SMSVerifyRequestGen (HTTP response).
         */
        if (!analyzed.clientSentAfterExpire) return true

        // Otherwise, don't send SMS request.
        return false
    }
}
