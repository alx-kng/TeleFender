package com.telefender.phone.call_related

import android.telecom.Call
import android.telecom.VideoProfile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.telefender.phone.App
import com.telefender.phone.call_related.CallManager.connections
import com.telefender.phone.data.server_related.RequestWrappers
import com.telefender.phone.misc_helpers.DBL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber


/**
 * Let's first understand a conference call before we go further. Essentially, when you merge
 * two calls into a conference call, the telecom carrier is notified and separate connections
 * are created for the conference call. By separate, we mean that the existing call connections
 * (objects) are no longer used and two new call connections (same numbers) fit for a
 * conference call are created and added as new calls through onCallAdded() of the CallService.
 * Moreover, the telecom carrier / Android system provides a third host connection (also as a
 * call object) with a null number that manages the child conference calls. Note, the host
 * is the only call that is actually marked as a conference call. When more calls are added to
 * the conference call, the same null host call is used and another replacement call is added
 * for the newly merged number.
 */

/**
 * As mentioned above, a conference call is a different type of
 * connection with the telecom carrier (remember how a null host call and replacement
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

/**
 * A call is not conferenceable only
 * when it's already in a conference call through another phone (e.g., the user calls
 * another phone that puts the user in a conference call with someone else). This allows us
 * to disable the merge button and inform the user when the call is already in another
 * conference (by checking if a call is conferencable or not).
 * Unfortunately, once the user has been in someone else's conference call, the user can no
 * longer create conference call with that person. For example, if the user joins Alex's
 * conference call with 2 people, and Alex removes the other caller from the conference call,
 * Alex's call inherently stays as a conference call (due to being a different type of
 * connection). As a result, the user cannot create a conference call with Alex (through the
 * user's phone) since Alex's call object is not conferenceable.
*/

/**
 * Abstraction of a group of calls. Represents either a single call or a conference call.
 */
class Connection(val call: Call?) {
    val isConference: Boolean
        get() = call.isConference()

    val state: Int
        get() = call.stateCompat()

    fun hold() {
        if (isConference) {
            call?.hold()
            call?.children?.forEach { it?.hold() }
        } else {
            call?.hold()
        }
    }

    fun unhold() {
        if (isConference) {
            call?.unhold()
            call?.children?.forEach { it?.unhold() }
        } else {
            call?.unhold()
        }
    }

    override fun toString() : String {
        return "{ Connection | number: ${call.number()}, state: ${state.toStateString()}, " +
            "isConference = $isConference, children size: ${call?.children?.size}, " +
            "createTime = ${call.createTime()} }"
    }
}

enum class HandleMode(val serverString: String) {
    BLOCK_MODE("Block"), SILENCE_MODE("Silence"), ALLOW_MODE("Allow");

    fun toInt() : Int {
        return this.ordinal
    }

    companion object {
        fun Int.toHandleMode() : HandleMode {
            return values()[this]
        }
    }
}

object CallManager {

    /**
     * CoroutineScope used to launch debugCallState() requests to the server.
     */
    val debugScope = CoroutineScope(Dispatchers.IO)

    /**
     * Contains the last answered Call object. Used to smoothly update UI in InCallFragment's
     * updateCallerDisplay().
     */
    var lastAnsweredCall: Call? = null

    /**
     * List of calls for managing and logging calls (emphasis on latter). The same calls are
     * stored in [connections] and are more often managed in that form.
     */
    val calls = mutableListOf<Call>()

    /**
     * List of Connections used to manage the calls.
     */
    val connections = mutableListOf<Connection>()

    /**
     * Current top level connection. That is, the connection that should have the user's attention.
     * Usually will be the current active call, incoming call, or dialing call.
     */
    private val _focusedConnection = MutableLiveData<Connection?>()
    val focusedConnection : LiveData<Connection?> = _focusedConnection
    val focusedCall : Call?
        get() = _focusedConnection.value?.call

    /**
     * Used to decide whether the incoming call screen should still show.
     */
    val incomingCallLiveData : LiveData<Boolean> = _focusedConnection.map {
        it?.state == Call.STATE_RINGING || it?.state == Call.STATE_NEW
    }

    /**
     * Updates the current focused connection. Ringing, Connecting, and Dialing connections take
     * precedence over Active connections (since they should be addressed immediately). During
     * state transitions, there might not be any connections that are in the aforementioned states
     * (Ringing, Connection, Dialing, Active). However, a focusedConnection is still necessary
     * (if there are still connections) to update the UI. As a result, we keep the current
     * focusedConnection if it is not disconnected. If it is disconnected, we search for any
     * connection that is not disconnected (if nothing is found, that means there
     * are no live calls).
     */
    fun updateFocusedConnection() {
        Timber.i("$DBL: updateFocusedConnection()")

        for (connection in connections) {
            if (connection.state in listOf(
                    Call.STATE_RINGING, Call.STATE_CONNECTING, Call.STATE_DIALING, Call.STATE_NEW)
            ) {
                _focusedConnection.value = connection

                launchDebugCallState()
                return
            }
        }

        for (connection in connections) {
            if (connection.state == Call.STATE_ACTIVE) {
                _focusedConnection.value = connection

                launchDebugCallState()
                return
            }
        }

        if (focusedConnection.value?.state != Call.STATE_DISCONNECTED) {
            launchDebugCallState()
            return
        }

        _focusedConnection.value = connections.find { it.state != Call.STATE_DISCONNECTED }

        launchDebugCallState()
    }

    /**
     * TODO: Check if overlapping debugCallState() requests can occur and if they can cause any
     *  problems.
     *
     * TODO: Currently here for debug. In the future, the flow may be refactored or this may be
     *  omitted from the code entirely (if we fix all calling / UI problems).
     *
     * Launches post request to server to upload the current call / UI states.
     */
    fun launchDebugCallState(logLocation: String = "Update") {
        debugScope.launch {
            Timber.i("$DBL: launchDebugCallState()")

            val applicationContext = CallService.context?.applicationContext
            applicationContext?.let {
                RequestWrappers.debugCallState(
                    context = it,
                    repository = (it as App).repository,
                    scope = debugScope,
                    logLocation = logLocation
                )
            }
        }
    }

    /**
     * TODO: Why is DIALING state used to detect incoming call -> Removed it but should double check
     *
     * Indicates whether there is currently an incoming call. Used to correctly update the
     * InCallFragment UI in updateCallerDisplay(). [incomingCallLiveData] is not used because
     * LiveData only updates its value when it's being observed.
     */
    fun incomingCall(): Boolean {
        return connections.find { it.state == Call.STATE_RINGING || it.state == Call.STATE_NEW } != null
    }

    /**
     * Finds the connection that is a conference (if there is one).
     */
    fun conferenceConnection(): Connection? {
        return connections.find { it.isConference  && it.state != Call.STATE_DISCONNECTED}
    }

    /**
     * Returns non-disconnected connections. Function is called orderedConnections() because the
     * returned connections are naturally ordered from oldest to newest added.
     */
    fun orderedConnections(): List<Connection?> {
        return connections.filter { it.state != Call.STATE_DISCONNECTED }
    }

    fun hasActiveConnection(): Boolean {
        return connections.find { it.state == Call.STATE_ACTIVE } != null
    }

    /**
     * Returns non-disconnected calls. Remember that calls and connections are different, so this
     * function is slightly different from orderedConnections().
     */
    fun nonDisconnectedCalls(): List<Call?> {
        return calls.filter { it.stateCompat() != Call.STATE_DISCONNECTED }
    }

    /**
     * Checks if there is an existing connection with the same number as the passed in Call object.
     * Remember, Connection stores a call object, but there can be different call objects with the
     * same number (e.g., during transition to merge)
     */
    fun existingConnection(call: Call): Boolean {
        return connections.find { it.call.number() == call.number() } != null
    }

    /**
     * Checks if there are any connections with the same state.
     */
    private fun sameStateConnections(): Boolean {
        val uniqueStateConnections = mutableListOf<Connection>()
        for (connection in connections) {
            if (uniqueStateConnections.find { it.state == connection.state } == null) {
                uniqueStateConnections.add(connection)
            } else {
                return true
            }
        }
        return false
    }

    /**
     * Checks if there are any Call objects with the same number.
     */
    fun repeatCalls(): Boolean {
        val uniqueCalls = mutableListOf<Call>()
        for (call in calls) {
            if (uniqueCalls.find { it.number() == call.number() } == null) {
                uniqueCalls.add(call)
            } else {
                return true
            }
        }
        return false
    }

    /**
     * Checks if the connections are in a stable state (refer to Android - Telecom Diagram).
     * Connections are stable if
     *
     *      1. No connections have the same state
     *      2. No calls have the same number
     *      3. There are no disconnected calls
     */
    fun isStableState(): Boolean {
        val hasDisconnected = calls.find { it.stateCompat() == Call.STATE_DISCONNECTED } != null
        return !(sameStateConnections() || repeatCalls() || hasDisconnected)
    }

    fun isActiveStableState(): Boolean {
        val hasIncoming = connections.find { it.state == Call.STATE_RINGING } != null
        val hasOutgoing = connections.find { it.state == Call.STATE_CONNECTING || it.state == Call.STATE_DIALING} != null

        return isStableState() && !hasIncoming && !hasOutgoing
    }

    /**********************************************************************************************
     * The following wrapped functions require an active stable state.
     *********************************************************************************************/

    fun holdingConnection(): Connection? {
        return connections.find { it.state == Call.STATE_HOLDING }
    }

    fun nonHoldingConnection(): Connection? {
        return connections.find { it.state != Call.STATE_HOLDING }
    }

    /*********************************************************************************************/

    /**
     * TODO: Make patch for OS duplicate call.
     *
     * Only adds a new connection for the call if there isn't an existing connection with the same
     * number and the call is not a child of a conference connection.
     */
    fun addCall(call: Call) {
        // Log before adding call (in addition to update focused connection).
        launchDebugCallState(logLocation = "Pre-add")

        val conference = conferenceConnection()
        if (conference == null || call !in (conference.call?.children ?: emptyList())) {
            if (!existingConnection(call)) {
                val newConnection = Connection(call)
                connections.add(newConnection)

                Timber.i("$DBL: CONNECTION ADDED")
            }
        }

        calls.add(call)

        updateFocusedConnection()

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call?, state: Int) {
                updateFocusedConnection()
                logConnections()
                logCalls()
            }

            override fun onConferenceableCallsChanged(call: Call?, conferenceableCalls: MutableList<Call>?) {
                updateFocusedConnection()
                logConnections()
                logCalls()
            }
        })

        logCalls()
        Timber.i("$DBL: CALL ADDED")
    }

    /**
     * Removes a Connection from [connections] only if the Connection wraps the [call].
     */
    fun removeCall(call: Call) {
        // Log before removing call (in addition to update focused connection).
        launchDebugCallState(logLocation = "Pre-remove")

        if (existingConnection(call)) {
            connections.removeIf { it.call == call }
        }

        calls.remove(call)

        updateFocusedConnection()

        Timber.i("$DBL: CALL REMOVED")
    }

    /**
     * Clears all references to calls.
     */
    fun clearCallObjects() {
        lastAnsweredCall = null
    }

    fun answer() {
        Timber.i("$DBL: ANSWER PRESSED ==============================================")
        focusedConnection.value?.call?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /**
     * When hangup() is called, onCallRemoved() of CallService is automatically invoked.
     */
    fun hangup() {
        Timber.i("$DBL: HANGUP PRESSED ==============================================")
        if (focusedConnection.value?.state == Call.STATE_RINGING) {
            focusedConnection.value?.call?.reject(false, null)
        } else {
            focusedConnection.value?.call?.disconnect()
        }
    }

    fun hangupArg(call: Call?) {
        if (call.stateCompat() == Call.STATE_RINGING) {
            call?.reject(false, null)
        } else {
            call?.disconnect()
        }
    }

    fun swap() {
        logConnections()
        if (isActiveStableState() && connections.size == 2) {
            holdingConnection()?.unhold()
            nonHoldingConnection()?.hold()
        }
    }

    /**
     * TODO: Second branch might not be CDMA vs GSM (check).
     *
     * We can get the possible calls that the focused call can conference with by using
     * conferenceableCalls. The conference() method then puts the  focused call in a
     * conference with the other conferenceable call. The reason why .first() is used is
     * because there can only be one call outside of the conference (remember, only one active
     * and one on hold connection is allowed). The difference between conference() and
     * mergeConference() is that they are probably used for different carriers (GSM and CDMA),
     * although this is not confirmed.
     */
    fun merge() {
        if (!isStableState() && connections.size == 2) {
            Timber.i("$DBL: NOT STABLE STATE")
            logConnections()
            logCalls()
            return
        }

        val focusedCall = focusedConnection.value?.call

        val conferenceableCalls = focusedCall?.conferenceableCalls ?: emptyList()
        if (conferenceableCalls.isNotEmpty()) {
            focusedCall?.conference(conferenceableCalls.first())

            Timber.i("$DBL: CONFERENCEABLE ==========================================")
        } else {
            val conferenceCapability = focusedCall?.hasCapability(Call.Details.CAPABILITY_MERGE_CONFERENCE) ?: false
            if (conferenceCapability) {
                focusedCall?.mergeConference()

                Timber.i("$DBL: MERGED WORKED =======================================")
            } else {
                Timber.i("$DBL: MERGED NOT WORKED ===================================")
            }
        }
    }

    fun canMerge() : Boolean{

        val hasConferenceable = !focusedCall?.conferenceableCalls.isNullOrEmpty()
        val conferenceCapability = focusedCall?.hasCapability(Call.Details.CAPABILITY_MERGE_CONFERENCE) ?: false

        Timber.i("$DBL: canMerge = ${hasConferenceable || conferenceCapability}")
        return (hasConferenceable || conferenceCapability)
    }

    fun keypad(c: Char) {
        Timber.e("$DBL: KEYPAD $c PRESSED!")

        focusedConnection.value?.call?.playDtmfTone(c)
        focusedConnection.value?.call?.stopDtmfTone()
    }

    fun logConnections() {
        for (connection in connections) {
            Timber.i("$DBL: CONNECTIONS ======= " +
                "connection: $connection"
            )
        }
    }

    fun logCalls() {
        for (call in calls) {
            Timber.i("$DBL: CALLS ============= " +
                "${call.details?.handle?.schemeSpecificPart}" +
                " | in conference: ${call.isConference()}" +
                " | call state: ${call.stateString()}" +
                " | conferenceable: ${conferenceableState(call)}"
            )
        }
    }

    /**
     * Returns a String for whether a call is conferenceable or not.
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
}