package com.telefender.phone.call_related


import android.provider.CallLog
import android.telecom.Call
import android.telecom.InCallService
import com.telefender.phone.data.tele_database.ClientDatabase
import com.telefender.phone.data.tele_database.TeleCallDetails
import com.telefender.phone.data.tele_database.background_tasks.workers.SyncScheduler
import com.telefender.phone.gui.InCallActivity
import com.telefender.phone.gui.IncomingCallActivity
import com.telefender.phone.helpers.TeleHelpers
import com.telefender.phone.permissions.Permissions
import timber.log.Timber


/**
 * TODO: CHECK IF NO PERMISSION FOR SILENCE MODE.
 *
 * TODO: IMPORTANT ERROR - During setup, even after database initialization supposedly finishes,
 *  when unknown call comes, the IncomingActivity screen doesn't show. Moreover, logs were printing
 *  "Waiting for rest of database!" even after the call. Actually, make sure that screens still
 *  show if the setup hasn't finished. LOOK INTO THIS!!! --> Could be permission error.
 *
 * TODO: Maybe we need to require do not disturb permissions no matter what???
 */
class CallService : InCallService() {

    override fun onCreate() {
        super.onCreate()

        // Sets CallService context.
        _context = this

        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: CallService onCreate()")
    }

    override fun onDestroy() {
        super.onDestroy()

        // Removes CallService context for safety.
        _context = null

        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: CallService onDestroy()")
    }

    /**
     * Note that voicemails are not passed to CallService, so you pretty much need to observer
     * default database to know when user receives voicemail.
     */
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        /*
        If the user receives a call during setup, the first time database access callback is
        actually stopped, meaning that certain stuff doesn't close correctly (can cause infinite
        loop). Here, we manually make sure that the stuff closes.
         */
//        ClientDatabase.databaseCallbackInterrupt()

        CallManager.addCall(call)

        /**
         * Only launches IncomingActivity if it is an incoming call. Otherwise, InCallActivity is
         * directly started. Also, safe calls and unsafe calls are handled separately.
         */
        if (call.getStateCompat() == Call.STATE_RINGING) {
            if (RuleChecker.isSafe(this, call.number())) {
                safeCall()
            } else {
                unsafeCall(call)
            }
        } else {
            InCallActivity.start(this)
        }
    }

    /**
     * TODO: MAKE SURE WE DIRECT USERS TO VOICEMAIL BOX. CAUSE IF FULL, NOTHING COMES THROUGH!!!!
     */
    override fun onCallRemoved(call: Call) {
        /**
         * CatchSyncWorker is more to catch voicemails that may pop up in the default call logs,
         * as voicemails are not passed to our CallService. However, it also syncs the most
         * immediate call log.
         */
        SyncScheduler.initiateCatchSyncWorker(this.applicationContext)

        CallManager.removeCall(call)
        super.onCallRemoved(call)
    }

    private fun safeCall() {
        /*
        Only need to set the ringer mode back to normal if changed in the first place. We know if
        the ringer mode was changed in RuleChecker if the app has Do Not Disturb permissions.
         */
        if (Permissions.hasDoNotDisturbPermission(this)) {
            AudioHelpers.setRingerMode(this, RingerMode.NORMAL)
        }

        IncomingCallActivity.start(this, true)
    }

    /**
     * TODO: Should set to vibrate on Silence mode if don't have permissions.
     *  Check insertCallDetail() works. Also, update response for unsafe calls in ALLOW_MODE,
     *  specifically in terms of showing UI.
     */
    private fun unsafeCall(call: Call) {
        when(CallManager.currentMode) {
            HandleMode.BLOCK_MODE -> {
                TeleCallDetails.insertCallDetail(this, call, true, CallLog.Calls.BLOCKED_TYPE)
                CallManager.hangup()
            }
            HandleMode.SILENCE_MODE -> {
                AudioHelpers.setRingerMode(this, RingerMode.SILENT)
                IncomingCallActivity.start(this, false)
            }
            HandleMode.ALLOW_MODE -> {
                IncomingCallActivity.start(this, true)
            }
        }
    }

    companion object {
        /**
         * Stores CallService context. Used so that AudioHelpers can modify ringer mode and speaker.
         */
        private var _context : CallService? = null
        val context : CallService?
            get() = _context
    }
}