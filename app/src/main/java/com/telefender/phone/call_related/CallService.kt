package com.telefender.phone.call_related


import android.provider.CallLog
import android.telecom.Call
import android.telecom.InCallService
import com.telefender.phone.data.tele_database.TeleCallDetails
import com.telefender.phone.data.tele_database.background_tasks.workers.CatchSyncScheduler
import com.telefender.phone.gui.InCallActivity
import com.telefender.phone.gui.IncomingCallActivity
import com.telefender.phone.misc_helpers.DBL
import timber.log.Timber
import java.lang.ref.WeakReference


/**
 * TODO: LEAK IS HERE! --> Still haven't fixed it -> Pretty sure it's fixed
 *
 * TODO: CHECK IF NO PERMISSION FOR SILENCE MODE.
 *
 * TODO: POSSIBLE ERROR - During setup, even after database initialization supposedly finishes,
 *  when unknown call comes, the IncomingActivity screen doesn't show. Moreover, logs were printing
 *  "Waiting for rest of database!" even after the call. Actually, make sure that screens still
 *  show if the setup hasn't finished. LOOK INTO THIS!!! --> Seems to be fixed, but double check.
 *
 * TODO: Maybe we need do not disturb permissions (for premium) no matter what??? --> Seconded
 */
class CallService : InCallService() {

    override fun onCreate() {
        super.onCreate()

        Timber.e("$DBL: CallService onCreate()")

        // Sets CallService context.
        _contexts.add(WeakReference(this))
    }

    /**
     * TODO: Maybe clearCallObjects() is unneeded?
     *
     * Only called when there are no active / incoming calls left
     */
    override fun onDestroy() {
        Timber.e("$DBL: CallService onDestroy()")

        // Removes CallService context for memory leak safety.
        _contexts.removeAll { it.get() == this }

        // Removes remaining Call object references in CallManager to prevent possible memory leak?
        CallManager.clearCallObjects()

        // We put onDestroy() at the end so that we can still use the reference before it
        super.onDestroy()
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
            val isSafe = RuleChecker.isSafe(this, call.number())
            Timber.e("$DBL: CallService - SAFE = $isSafe")

            if (isSafe) {
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
        CatchSyncScheduler.initiateCatchSyncWorker(this.applicationContext)

        CallManager.removeCall(call)
        super.onCallRemoved(call)
    }

    private fun safeCall() {
        /*
        Only need to set the ringer mode back to normal if changed in the first place. We know if
        the ringer mode was changed in RuleChecker if the app has Do Not Disturb permissions.
         */
        AudioHelpers.setRingerMode(this, RingerMode.NORMAL)

        IncomingCallActivity.start(this, true)
    }

    /**
     * TODO: Check insertCallDetail() works. Also, update response for unsafe calls in ALLOW_MODE,
     *  specifically in terms of showing UI.
     */
    private fun unsafeCall(call: Call) {
        when(CallManager.currentMode) {
            HandleMode.BLOCK_MODE -> {
                Timber.e("$DBL: BLOCK_MODE Action: Unsafe call blocked!")
                TeleCallDetails.insertCallDetail(this, call, true, CallLog.Calls.BLOCKED_TYPE)
                CallManager.hangup()
            }
            HandleMode.SILENCE_MODE -> {
                // Silences ringer if app has permissions.
                AudioHelpers.setRingerMode(this, RingerMode.SILENT)
                IncomingCallActivity.start(this, false)
            }
            HandleMode.ALLOW_MODE -> {
                Timber.e("$DBL: ALLOW_MODE Action: Unsafe call allowed!")
                IncomingCallActivity.start(this, true)
            }
        }
    }

    companion object {
        /**
         * Stores CallService context. Used so that AudioHelpers can modify ringer mode and speaker.
         * Also used to safely launch some activities. We use a list here for static safety, check
         * Android - General Notes for more info.
         */
        private val _contexts : MutableList<WeakReference<CallService>> = mutableListOf()
        val context : CallService?
            get() {
                cleanUp()
                return _contexts.lastOrNull()?.get()
            }

        /**
         * Remove any WeakReferences that no longer reference a CallService
         */
        private fun cleanUp() {
            _contexts.removeAll { it.get() == null }
        }
    }
}