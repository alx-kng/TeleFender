package com.telefender.phone.call_related


import android.telecom.Call
import android.telecom.InCallService
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.TeleCallDetails
import com.telefender.phone.gui.InCallActivity
import com.telefender.phone.gui.IncomingCallActivity

class CallService : InCallService() {

    override fun onCreate() {
        super.onCreate()

        // Sets CallService context.
        _context = this
    }

    override fun onDestroy() {
        super.onDestroy()

        // Removes CallService context for safety.
        _context = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        CallManager.addCall(call)

        /**
         * Only launches IncomingActivity if it is an incoming call. Otherwise, InCallActivity is
         * directly started.
         */
        if (call.getStateCompat() == Call.STATE_RINGING) {
            if (RuleChecker.isSafe(call.number()) || CallManager.currentMode == CallManager.ALLOW_MODE) {
                safeCall()
            } else {
                unsafeCall(call)
            }
        } else {
            InCallActivity.start(this)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)

        CallManager.removeCall(call)
    }

    private fun safeCall() {
        IncomingCallActivity.start(this, true)
    }

    /**
     * TODO: Should set to vibrate on Silence mode if don't have permissions.
     *  Check insertCallDetail() works.
     */
    private fun unsafeCall(call: Call) {
        if (CallManager.currentMode == CallManager.BLOCK_MODE) {
            val repository = ((context?.applicationContext) as App).repository
            TeleCallDetails.insertCallDetail(call, true, repository)
            CallManager.hangup()
        } else {
            AudioHelpers.ringerSilent(this, true)
            IncomingCallActivity.start(this, false)
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