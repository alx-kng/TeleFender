package com.telefender.phone.call_related


import android.provider.CallLog
import android.telecom.Call
import android.telecom.InCallService
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.TeleCallDetails
import com.telefender.phone.data.tele_database.background_tasks.workers.SyncScheduler
import com.telefender.phone.gui.InCallActivity
import com.telefender.phone.gui.IncomingCallActivity
import com.telefender.phone.helpers.MiscHelpers
import timber.log.Timber

class CallService : InCallService() {

    override fun onCreate() {
        super.onCreate()

        // Sets CallService context.
        _context = this

        Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: CallService onCreate()")
    }

    override fun onDestroy() {
        super.onDestroy()

        // Removes CallService context for safety.
        _context = null

        Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: CallService onDestroy()")
    }

    /**
     * Note that voicemails are not passed to CallService, so you pretty much need to observer
     * default database to know when user receives voicemail.
     */
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

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
        IncomingCallActivity.start(this, true)
    }

    /**
     * TODO: Should set to vibrate on Silence mode if don't have permissions.
     *  Check insertCallDetail() works. Also, update response for unsafe calls in ALLOW_MODE,
     *  specifically in terms of showing UI.
     */
    private fun unsafeCall(call: Call) {
        val repository = ((this.applicationContext) as App).repository

        when(CallManager.currentMode) {
            HandleMode.BLOCK_MODE -> {
                TeleCallDetails.insertCallDetail(repository, call, true, CallLog.Calls.BLOCKED_TYPE)
                CallManager.hangup()
            }
            HandleMode.SILENCE_MODE -> {
                AudioHelpers.ringerSilent(this, true)
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