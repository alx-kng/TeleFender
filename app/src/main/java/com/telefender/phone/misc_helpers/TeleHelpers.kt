package com.telefender.phone.misc_helpers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.CallLog
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.telefender.phone.App
import com.telefender.phone.call_related.HandleMode
import com.telefender.phone.call_related.SimCarrier
import com.telefender.phone.data.default_database.DefaultContacts
import com.telefender.phone.data.tele_database.entities.NotifyItem
import com.telefender.phone.data.tele_database.entities.Parameters
import com.telefender.phone.data.tele_database.entities.defaultHandleMode
import com.telefender.phone.permissions.Permissions
import timber.log.Timber
import java.time.Instant
import java.util.*


/**
 * TODO: HANDLE HASHTAG NUMBERS TO OPEN LINKS
 *
 * TODO: Consider adding retry amount to be used for entire app.
 *
 * Helper functions specifically for anything phone / database related.
 */
object TeleHelpers {

    const val UNKNOWN_NUMBER = "UNKNOWN NUMBER"

    /**
     * Just our simple custom assert function. If success if false, then
     */
    fun assert(success: Boolean, from: String? = null) {
        if (!success) throw Exception("assert() FAILURE from $from")
    }

    /**
     * Converts default aggregate Contact ID to our Tele database CID.
     */
    fun defaultCIDToTeleCID(
        defaultCID: String,
        instanceNumber: String
    ) : String {
        return UUID.nameUUIDFromBytes((defaultCID + instanceNumber).toByteArray()).toString()
    }

    fun getContactName(
        context: Context,
        number: String
    ) : String? {
        return normalizedNumber(number)?.let {
            DefaultContacts.getFirstFullContactFromNumber(
                contentResolver = context.contentResolver,
                number = it
            )?.second
        }
    }

    /**
     * TODO: See if these tiny runBlocking{} usages are very bad? -> Removed for now and made
     *  calling functions suspend.
     *
     * Checks if database is initialized, user is setup, and that the wanted permissions are given.
     */
    suspend fun hasValidStatus(
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
        val databaseCondition = !initializedRequired || repository.databaseInitialized()
        val setupCondition = !setupRequired || repository.hasClientKey()

        return databaseCondition && setupCondition
    }

    /**
     * Returns current handle mode stored in database.
     */
    suspend fun currentHandleMode(context: Context) : HandleMode {
        val repository = (context.applicationContext as App).repository
        return repository.getStoredMap()?.currentHandleMode ?: defaultHandleMode
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
            Timber.e("$DBL: getUserNumberStored() returned null!!!")
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
    suspend fun getUserNumberUncertain(context: Context) : String? {
        val databaseNumber = getUserNumberStored(context)

        return databaseNumber ?:
            if (!Permissions.hasPhoneStatePermissions(context)) {
                Timber.e("$DBL: User number was null due to lack of permissions!!!")
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

    /**
     * Returns new times window (list of epoch milli times in last [windowSize] milliseconds). Adds
     * in [newTime] to window if given.
     */
    fun updatedTimesWindow(
        window: List<Long>,
        windowSize: Long,
        newTime: Long? = null,
        ) : List<Long> {
        val currentTime = Instant.now().toEpochMilli()
        val mutableWindow = window.toMutableList()

        if (newTime != null) {
            mutableWindow.add(newTime)
        }

        return mutableWindow.filter { currentTime - it < windowSize }
    }

    /**
     * Returns updated NotifyItem if [oldNotifyItem] is not null.
     */
    fun updatedNotifyItem(
        oldNotifyItem: NotifyItem?,
        callEpochDate: Long,
        newNotifyWindow: List<Long>,
        qualifies: Boolean
    ) : NotifyItem? {
        return oldNotifyItem?.copy(
            lastCallTime = callEpochDate,

            // If number re-qualifies for notify list, then update lastQualifiedTime
            lastQualifiedTime = if (qualifies) {
                callEpochDate
            } else {
                oldNotifyItem.lastQualifiedTime
            },

            notifyWindow = newNotifyWindow,

            // Remember, last call's nextDropWindow becomes this call's currDropWindow
            currDropWindow = oldNotifyItem.nextDropWindow,

            // Resets since item is obviously not seen yet after new call.
            seenSinceLastCall = false
        )
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
     * an invalid message string is printed and the [bareNumber] is used instead.
     */
    fun normalizedNumber(number : String?) : String {
        // Let bareNumber() handle # sign so that numbers like #225 aren't converted to +1225
        if (number?.contains('#') == true) return bareNumber(number)

        val normalizedNum = number?.let {
            try {
                val phoneUtil = PhoneNumberUtil.getInstance()
                val protoNum = phoneUtil.parse(it, "US")
                phoneUtil.format(protoNum, PhoneNumberUtil.PhoneNumberFormat.E164)
            } catch (e: Exception) {
                Timber.i("$DBL: $number is either invalid or wonky!! Error = ${e.message}")
                null
            }
        }

        return normalizedNum ?: bareNumber(number)
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
     * TODO: See if this works in the general case.
     *
     * Returns the SIM carrier of the phone if possible.
     *
     * NOTE: Requires phone state permissions.
     */
    fun getSimCarrier(context: Context) : SimCarrier? {
        if (!Permissions.hasPhoneStatePermissions(context)) {
            Timber.e("$DBL: Can't get SIM carrier due to lack of permissions!!!")
            return null
        }

        val tMgr = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val simCarrierName = tMgr.simCarrierIdName?.toString()?.lowercase() ?: return null

        if (simCarrierName.contains("verizon")) return SimCarrier.VERIZON

        if (simCarrierName.contains("at")) return SimCarrier.AT_T

        if (simCarrierName.contains("t-mobile")) return SimCarrier.T_MOBILE

        return null
    }

    /**
     * Gets direction as normal. However, voicemail direction is also accurately returned (in
     * some carriers). If app doesn't have phone state permissions, then the direction returned is
     * just the direction passed in.
     *
     * NOTE: Retrieving SIM carrier requires phone state permissions.
     * NOTE: Requires non-normalized raw number.
     */
    fun getTrueDirection(context: Context, direction: Int, rawNumber: String) : Int {
        return if (isVoiceMail(context, direction, rawNumber))
            CallLog.Calls.VOICEMAIL_TYPE
        else
            direction
    }

    /**
     * TODO: Find a way to check for voicemails in carriers such as T-Mobile.
     *
     * TODO: Unfortunately, this seems to only work on Verizon phones as of now. T-Mobile phones
     *  unfortunately don't work (always adds +1), and we're currently unsure of AT&T. This means
     *  that we need to at least be able to tell what the user's carrier is so that we don't
     *  misidentify call types. --> Partially done
     *
     * Duct tape way to check if call log is a voicemail or not. Uses the idea that voicemail call
     * logs always have a '+' in front of the number and are incoming. Returns false if the trick
     * cannot be used, that is, we automatically assume the call is not a voicemail if the trick
     * doesn't work on the current carrier.
     *
     * NOTE: This trick currently only works on Verizon.
     * NOTE: Retrieving SIM carrier requires phone state permissions.
     * NOTE: Requires non-normalized raw number.
     */
    private fun isVoiceMail(context: Context, direction: Int?, rawNumber: String) : Boolean {
        return when (getSimCarrier(context)) {
            SimCarrier.VERIZON -> {
                rawNumber.isNotEmpty()
                && rawNumber[0] == '+'
                && direction == CallLog.Calls.INCOMING_TYPE
            }
            else -> false
        }
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