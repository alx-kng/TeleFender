package com.telefender.phone.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.CallLog
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.entities.*
import com.telefender.phone.permissions.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.time.Instant


/*
TODO: Consider adding retry amount to be used for entire app.
 */
object TeleHelpers {

    const val DEBUG_LOG_TAG = "TELE_DEBUG"
    const val UNKNOWN_NUMBER = "UNKNOWN NUMBER"

    /**
     * Just our simple custom assert function. If success if false, then
     */
    fun assert(success: Boolean, from: String? = null) {
        if (!success) throw Exception("assert() FAILURE from $from")
    }

    /**
     * TODO: See if these tiny runBlocking{} usages are very bad?
     *
     * Checks if database is initialized, user is setup, and that the wanted permissions are given.
     */
    fun hasValidStatus(
        context: Context,
        initializedRequired: Boolean = true,
        setupRequired: Boolean = true,
        phoneStateRequired: Boolean = false,
        logRequired: Boolean = false,
        contactRequired: Boolean = false
    ) : Boolean {

        if (phoneStateRequired && !Permissions.hasPhoneStatePermissions(context)) return false

        if (logRequired && !Permissions.hasLogPermissions(context)) return false

        if (contactRequired && !Permissions.hasContactPermissions(context)) return false

        val repository = (context.applicationContext as App).repository
        return runBlocking(Dispatchers.Default) {
            val databaseCondition = !initializedRequired || repository.databaseInitialized()
            val setupCondition = !setupRequired || repository.hasClientKey()

            databaseCondition && setupCondition
        }
    }

    /**
     * Gets user's phone number from own database. Doesn't need permissions. Throws Exception if
     * unable to retrieve number for some reason.
     *
     * NOTE: should ONLY BE USED after database initialization!
     */
    suspend fun getUserNumberStored(context: Context) : String? {
        val repository = (context.applicationContext as App).repository
        val userNumber = repository.getUserNumber()

        if (userNumber == null) {
            Timber.e("$DEBUG_LOG_TAG: getUserNumberStored() returned null!!!")
        }

        return userNumber
    }

    /**
     * TODO: Double check if subscription manager retrieval of user's number works or not.
     * TODO: Handle multiple sim cards (numbers) for one phone.
     * TODO: Apparently, default dialer doesn't give READ_PHONE_NUMBERS permission
     *  -> Found solution. Look in Permissions. SUMMARIZE.
     *
     * Gets user's phone number. Tries to retrieve from own database before resorting to retrieving
     * using permissions.
     */
    @SuppressLint("MissingPermission")
    fun getUserNumberUncertain(context: Context) : String? {
        val databaseNumber = runBlocking(Dispatchers.Default) {
            getUserNumberStored(context)
        }

        return databaseNumber ?:
            if (!Permissions.hasPhoneStatePermissions(context)) {
                Timber.e("$DEBUG_LOG_TAG: User number was null due to lack of permissions!!!")
                return null
            } else {
                val tMgr = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val sMgr = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

                /*
                Android 13 and above deprecates getLine1Number() for retrieving the user's number.
                Instead, we must use the SubscriptionManager's getPhoneNumber(), which allows for
                the retrieval of the user's different numbers (if they have multiple sim cards).

                NOTE: That getLine1Number() still seems to work in higher versions though.
                 */
                val number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    sMgr.getPhoneNumber(tMgr.subscriptionId)
                } else {
                    tMgr.line1Number
                }

                return normalizedNumber(number) ?: bareNumber(number)
            }
    }

    fun getAnalyzedNumber(context: Context, normalizedNumber: String) : AnalyzedNumber? {
        val repository = (context.applicationContext as App).repository
        return runBlocking(Dispatchers.Default) {
            repository.getAnalyzedNum(number = normalizedNumber)
        }
    }

    fun getParameters(context: Context) : Parameters? {
        val repository = (context.applicationContext as App).repository
        return runBlocking(Dispatchers.Default) {
            repository.getParametersWrapper()?.getParameters()
        }
    }

    fun getStoredMap(context: Context) : StoredMap? {
        val repository = (context.applicationContext as App).repository
        return runBlocking(Dispatchers.Default) {
            repository.getStoredMap()
        }
    }

    /**
     * Returns new notify window (list of call times from last [windowSize] days). Adds in
     * [newCallTime] to window if given.
     *
     * [callDirection] is not incoming or outgoing.
     */
    fun updateNotifyWindow(
        notifyWindow: List<Long>,
        windowSize: Int,
        newCallTime: Long? = null,
    ) : List<Long> {
        val currentTime = Instant.now().toEpochMilli()
        val mutableNotifyWindow = notifyWindow.toMutableList()

        if (newCallTime != null) {
            mutableNotifyWindow.add(newCallTime)
        }

        return mutableNotifyWindow.filter { currentTime - it < windowSize.daysToMilli() }
    }

    /**
     * Returns true if [notifyItem] should be removed from notify list.
     */
    fun shouldRemoveNotifyItem(notifyItem: NotifyItem, parameters: Parameters) : Boolean {
        val currentTime = Instant.now().toEpochMilli()
        with(notifyItem) {
            return currentTime - lastCallTime > currDropWindow.daysToMilli()
                && currentTime - lastQualifiedTime > parameters.qualifiedDropWindow.daysToMilli()
                && veryFirstSeenTime != null
                && currentTime - veryFirstSeenTime > parameters.seenDropWindow.daysToMilli()
        }
    }

    /**
     * TODO: Maybe dynamically get country code later.
     * TODO: Maybe combine normalizedNumber() and bareNumber()?
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
        val cleaned = number?.replace("(\\s|\\(|\\)|-|\\.|,)".toRegex(), "")
        return if (cleaned != null && cleaned != "") cleaned else UNKNOWN_NUMBER
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
     *
     * Duct tape way to check if call log is a voicemail or not. Uses the idea that
     * voicemail call logs always have a '+' in front of the number and are incoming.
     * Don't know how this changes between carriers.
     * NOTE: Requires non-normalized raw number.
     */
    private fun isVoiceMail(direction: Int?, rawNumber: String) : Boolean {
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