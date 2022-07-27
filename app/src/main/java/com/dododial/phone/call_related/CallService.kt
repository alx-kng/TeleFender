package com.dododial.phone.call_related

import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import com.dododial.phone.RuleChecker


import timber.log.Timber

class CallService : InCallService() {

    override fun onCreate() {
        super.onCreate()

        /**
         * Sets CallService context so that AudioHelpers can modify ringer mode. The context is
         * only required for speaker.
         */
        AudioHelpers.callServiceContext = this
    }

    override fun onDestroy() {
        super.onDestroy()

        /**
         * Removes CallService context for safety.
         */
        AudioHelpers.callServiceContext = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        CallManager.addCall(call)

        if (RuleChecker.isSafe(call.number()) || CallManager.currentMode == CallManager.ALLOW_MODE) {
            safeCall()
        } else {
            unsafeCall()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)

        CallManager.removeCall(call)
    }

    private fun safeCall() {
        IncomingCallActivity.start(this, true)
    }

    private fun unsafeCall() {

        if (CallManager.currentMode == CallManager.BLOCK_MODE) {
            CallManager.hangup()
        } else {
            AudioHelpers.ringerSilent(this, true)
            IncomingCallActivity.start(this, false)
        }
    }
}