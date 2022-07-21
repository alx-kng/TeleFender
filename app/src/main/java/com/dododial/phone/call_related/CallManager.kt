package com.dododial.phone.call_related

import android.telecom.Call
import android.telecom.VideoProfile
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dododial.phone.call_related.CallManager.getPhoneState
import com.dododial.phone.call_related.OngoingCall.call
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArraySet


interface CallListener {
    val tag: String
    fun onStateChanged()
    fun onPrimaryCallChanged(call: Call)
}

/**
 * The sealed modifier just means that the only possible subclasses of PhoneState are the ones
 * listed below. This is useful in when expressions, where full coverage can be confirmed.
 */
sealed class PhoneState
object NoCall : PhoneState()
class OneCall(val call: Call) : PhoneState()
class TwoCallsIncoming(val active: Call, val incoming: Call) : PhoneState()
class TwoCallsHold(val active: Call, val onHold: Call) : PhoneState()

object CallManager {

    /**
     * Stores the current active call.
     */
    private var primaryCall: Call? = null

    /**
     * Stores all calls.
     */
    private val calls = mutableListOf<Call>()

    /**
     * Stores current phone state (useful for UI).
     */
    private val _phoneState = MutableLiveData<PhoneState>(NoCall)
    val phoneState : LiveData<PhoneState> = _phoneState

    /**
     * Stores listeners / callbacks for when the active call changes to a different call object
     * or the state of the active call changes. CopyOnWriteArraySet just provides a thread-safe
     * way of adding and accessing the listeners.
     */
    private val listeners = CopyOnWriteArraySet<CallListener>()

    fun addListener(listener: CallListener) {
        listeners.add(listener)
    }

    fun removeListener(tag: String) {
        listeners.removeIf { listener -> listener.tag == tag }
    }

    fun addCall(call: Call) {
        primaryCall = call
        calls.add(call)
        updatePhoneState()

        for (listener in listeners) {
            listener.onPrimaryCallChanged(call)
        }

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call?, state: Int) {
                updateState()
                logCalls()
            }

            override fun onDetailsChanged(call: Call?, details: Call.Details?) {
//                updateState()
                logCalls()
            }
            override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) {
                updateState()
            }
        })

        Timber.i("DODODEBUG: CALL ADDED")
    }

    /**
     * TODO: Singular conference call UI
     *
     * As mentioned further down in the code, a conference call is a different type of
     * connection with the service provider (remember how a null host call and replacement
     * calls are added). Due to this difference, any calls within a conference call permanently
     * stay within the conference call (each call will always be a child of the null host call).
     * The slight problem that arises is that a conference call can technically have just one
     * child call, which may not seem ideal since it doesn't make much sense to have a conference
     * call with a single call.
     *
     * Although it's impossible to decouple a child call from its null host call, we can
     * simply treat a singular conference call as a single call UI wise (e.g., not showing the
     * conference call UI for singular conference calls). This way, the singular conference
     * call acts like a single call (so the user isn't confused), with the normal merging
     * functionality working as expected.
     */
    fun removeCall(call: Call) {
        calls.remove(call)
        updateState()
        Timber.i("DODODEBUG: CALL REMOVED")
    }

    /**
     * Returns a PhoneState that is primarily used to find the current active call.
     *
     * For the call.size == 2 case, the calls are "grouped" by active, newCall, onHold, and incoming.
     * The reason why .find() is used, which only returns the first matching element, is because
     * there should be only be at most one of each type of call, and there should be at most two
     * types of calls. For example, you can have one active and one incoming call, but you can't
     * have an active, on hold, and incoming call.
     *
     * The slight catch is for conference calls, where there are technicallya multiple active calls.
     * Let's first understand a conference call before we go further. Essentially, when you merge
     * two calls into a conference call, the service provider is notified and separate connections
     * are created for the conference call. By separate, we mean that the existing call connections
     * (objects) are no longer used and two new call connections (same numbers) fit for a
     * conference call are created and added as new calls through onCallAdded() of the CallService.
     * Moreover, the service provider / Android system provides a third host connection (also as a
     * call object) with a null number that manages the child conference calls. Note, the host
     * is the only call that is actually marked as a conference call. When more calls are added to
     * the conference call, the same null host call is used and another replacement call is added
     * for the newly merged number.
     *
     * As mentioned above, a conference call will always have at least 3 calls, so the
     * call.size == 2 case is not applicable for conference calls.
     */
    fun getPhoneState(): PhoneState {
        return when (calls.size) {
            0 -> NoCall
            1 -> OneCall(calls.first())
            2 -> {
                val active = calls.find { it.getStateCompat() == Call.STATE_ACTIVE }
                val newCall = calls.find { it.getStateCompat() == Call.STATE_CONNECTING || it.getStateCompat() == Call.STATE_DIALING }
                val onHold = calls.find { it.getStateCompat() == Call.STATE_HOLDING }
                val incoming = calls.find { it.getStateCompat() == Call.STATE_RINGING }

                if (active != null && newCall != null) {
                    TwoCallsHold(newCall, active)
                } else if (newCall != null && onHold != null) {
                    TwoCallsHold(newCall, onHold)
                } else if (active != null && onHold != null) {
                    TwoCallsHold(active, onHold)
                } else if (active != null && incoming != null) {
                    TwoCallsIncoming(active, incoming)
                } else if (incoming != null && onHold != null) {
                    TwoCallsIncoming(incoming, onHold)
                } else {
                    Log.i("DODODEBUG", "nulls: active - ${active == null} | newCall - ${newCall == null} | onHold - ${onHold == null} | incoming - ${incoming == null}")
                    throw Exception("No matching PhoneState!")
                }
            } else -> {
                /**
                 * As mentioned in the earlier comment, there is a conference host that is the
                 * only call marked as a conference call, and it has a null number.
                 * conferenceHost.children contains the list of calls within the same conference
                 * call (excluding the conference host call). As a result,
                 * conferenceHost.children.size + 1 gives the number of calls in the conference.
                 * If the number of calls in the conference isn't the total number of calls,
                 * then there must be a separate call
                 */
                val conferenceHost = calls.find { it.isConference() } ?: return NoCall
                val separateCall = if (conferenceHost.children.size + 1 != calls.size) {
                    calls.filter { !it.isConference() }
                        .subtract(conferenceHost.children.toSet())
                        .firstOrNull()
                } else {
                    null
                }

                /**
                 * It takes a little time for the original copies of the conference call children
                 * to be removed.
                 */
                if (separateCall == null
                    || separateCall?.details?.state == Call.STATE_DISCONNECTING
                    || separateCall?.details?.state == Call.STATE_DISCONNECTED
                ) {
                    OneCall(conferenceHost)
                } else {
                    when (separateCall.getStateCompat()) {
                        Call.STATE_ACTIVE, Call.STATE_CONNECTING, Call.STATE_DIALING -> {
                            TwoCallsHold(separateCall, conferenceHost)
                        }
                        Call.STATE_RINGING -> {
                            TwoCallsIncoming(conferenceHost, separateCall)
                        }
                        else -> {
                            TwoCallsHold(conferenceHost, separateCall)
                        }
                    }
                }
            }
        }
    }

    fun updatePhoneState() {
        val currentPhoneState = getPhoneState()
        if (currentPhoneState != _phoneState.value) {
            _phoneState.value = currentPhoneState
        }
    }

    private fun updateState() {
        val currentPhoneState = getPhoneState()
        val activeCall = when (currentPhoneState) {
            is NoCall -> null
            is OneCall -> currentPhoneState.call
            is TwoCallsHold -> currentPhoneState.active
            is TwoCallsIncoming -> currentPhoneState.incoming
        }

        if (currentPhoneState != _phoneState.value) {
            _phoneState.value = currentPhoneState
        }

        var notify = true
        if (activeCall == null) {
            primaryCall = null
        } else if (activeCall != primaryCall) {
            primaryCall = activeCall

            for (listener in listeners) {
                listener.onPrimaryCallChanged(activeCall)
            }

            notify = false
        }

        if (notify) {
            for (listener in listeners) {
                listener.onStateChanged()
            }
        }
    }

    fun answer() {
        primaryCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /**
     * When hangup() is called, onCallRemoved() of CallService is automatically invoked.
     */
    fun hangup() {
        if (primaryCall != null) {
            if (getState() == Call.STATE_RINGING) {
                primaryCall!!.reject(false, null)
            } else {
                primaryCall!!.disconnect()
            }
        }
    }

    /**
     * Not sure why the primary call state (through getState()) would ever be holding, as the
     * state is updated every time a call state changes (through the callbacks in the beginning
     * of the file). The weird thing is when the code holds / unholds the primary call, the
     * other call doesn't seem to be toggled here. I guess the default hold() and unhold()
     * methods automatically make the other call hold or unhold based off what we do to the
     * primary call (as you can't have two holding calls or two active calls).
     */
    fun toggleHold(): Boolean {
        val isOnHold = getState() == Call.STATE_HOLDING
        if (isOnHold) {
            primaryCall?.unhold()
        } else {
            primaryCall?.hold()
        }
        return !isOnHold
    }

    fun swap() {
        if (calls.size > 1) {
            calls.find { it.getStateCompat() == Call.STATE_HOLDING }?.unhold()
        }
    }

    /**
     * A call is conferenceable when it's not already in another conference call (there may
     * be additional cases). [conferenceableCalls] gives the possible calls a the primary call
     * [call] can conference with. The conference() method then puts the primary call in a
     * conference with the other conferenceable call. The reason why .first() is used is
     * because there can only be one call outside of the conference (remember, only one active
     * and one on hold call is allowed). The difference between conference() and mergeConference()
     * is that they are probably used for different carriers (GSM and CDMA), although this is
     * not confirmed.
     */
    fun merge() {
        val conferenceableCalls = primaryCall!!.conferenceableCalls
        if (conferenceableCalls.isNotEmpty()) {
            primaryCall!!.conference(conferenceableCalls.first())
            Timber.i("DODODEBUG: CONFERENCEABLE ==========================================")
        } else {
            if (primaryCall!!.hasCapability(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
                primaryCall!!.mergeConference()
                Timber.i("DODODEBUG: MERGED WORKED =======================================")
            } else {
                Timber.i("DODODEBUG: MERGED NOT WORKED ===================================")
            }
        }
    }

    fun keypad(c: Char) {
        primaryCall?.playDtmfTone(c)
        primaryCall?.stopDtmfTone()
    }

    /**
     * Used for null safe access of call state (primaryCall can be null).
     */
    fun getState() = primaryCall?.getStateCompat()

    fun getPrimaryCall(): Call? {
        return primaryCall
    }

    fun getConferenceHost(): Call? {
        return calls.find { it.isConference() }
    }

    fun getConferenceCalls(): List<Call> {
        return getConferenceHost()?.children ?: emptyList()
    }

    fun logCalls() {
        for (call in calls ) {
            Timber.i("DODODEBUG: CALLS =============" +
                "${call?.details?.handle?.schemeSpecificPart}" +
                    " | in conference: ${call.isConference()}" +
                    " | call state: ${callStateString(call.getStateCompat())}" +
                    " | conferenceable: ${conferenceableState(call)}"
            )
        }
    }

    /**
     * Used to check if a call is conferenceable or not. A call is not conferenceable only
     * when it's already in a conference call through another phone (e.g., the user calls
     * another phone that puts the user in a conference call with someone else). This allows us
     * to disable the merge button and inform the user when the call is already in another
     * conference (by checking if a call is conferencable or not).
     *
     * Unfortunately, once the user has been in someone else's conference call, the user can no
     * longer create conference call with that person. For example, if the user joins Alex's
     * conference call with 2 people, and Alex removes the other caller from the conference call,
     * Alex's call inherently stays as a conference call (due to being a different type of
     * connection). As a result, the user cannot create a conference call with Alex (through the
     * user's phone) since Alex's call object is not conferenceable.
     */
    fun conferenceableState(call: Call): String {
        if (calls.size == 1) {
            return "N/A: Only one call"
        }

        val conferenceableCalls = call.conferenceableCalls.size
        return if (conferenceableCalls == 0
            && !call.hasCapability(Call.Details.CAPABILITY_MERGE_CONFERENCE)
        ) {
            "CDMA, GSM - Not Conferenceable"
        } else if (conferenceableCalls != 0) {
            "CDMA - Conferenceable with ${call.conferenceableCalls.size} calls"
        } else {
            "GSM - Is Conferenceable"
        }
    }

    fun callStateString(state: Int): String {
        return when (state) {
            Call.STATE_RINGING -> "Ringing"
            Call.STATE_ACTIVE -> "Active"
            Call.STATE_DIALING -> "Dialing"
            Call.STATE_CONNECTING -> "Connecting"
            Call.STATE_HOLDING -> "Holding"
            Call.STATE_AUDIO_PROCESSING -> "Audio Processing"
            Call.STATE_DISCONNECTED -> "Disconnected"
            Call.STATE_DISCONNECTING -> "Disconnecting"
            Call.STATE_NEW -> "New"
            else -> "Some other state"
        }
    }

}