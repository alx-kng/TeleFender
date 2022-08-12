package com.dododial.phone.gui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.telecom.Call
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.dododial.phone.gui.DialerActivity
import com.dododial.phone.R
import com.dododial.phone.call_related.*
import com.dododial.phone.databinding.FragmentInCallBinding
import com.dododial.phone.gui.InCallActivity
import com.dododial.phone.gui.IncomingCallActivity
import com.dododial.phone.gui.MainActivity
import com.dododial.phone.gui.model.InCallViewModel
import timber.log.Timber

class InCallFragment : Fragment() {

    private var _binding: FragmentInCallBinding? = null
    private val binding get() = _binding!!
    private val inCallViewModel: InCallViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentInCallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /**
         * InCallViewModel is used with DataBinding to live update the call duration UI.
         */
        binding.apply {
            viewModel = inCallViewModel
            lifecycleOwner = viewLifecycleOwner
        }

        prepareAudio()

        /**
         * TODO: Conference count doesn't update correctly (not sure if fixed).
         *
         * Observes and updates UI based off current focusedConnection
         */
        CallManager.focusedConnection.observe(viewLifecycleOwner) { connection ->
            /**
             * End InCallActivity if there are no connections, only disconnected connections, or a
             * single conference connection that is about to end.
             */
            val edgeState = (CallManager.connections.firstOrNull()?.state ?: Call.STATE_DISCONNECTED)
            if (connection == null
                || (CallManager.connections.size == 1 && edgeState == Call.STATE_DISCONNECTED)
                || conferenceShouldDisconnect()
            ) {
                requireActivity().finishAndRemoveTask()
                Timber.i("DODODEBUG: IN CALL FINISHED!")
            } else {
                updateCallerDisplay()
                updateButtons()
            }
        }

        /*******************************************************************************************
         * On click listeners for multi-display. Should swap calls if the holding call is pressed.
         * Only safe to use when updateCallerDisplay() enables these buttons. Also shows loading
         * circle.
         ******************************************************************************************/

        binding.firstDisplay.setOnClickListener {
            if (CallManager.isActiveStableState() && CallManager.connections.size == 2) {
                val orderedConnections = CallManager.orderedConnections()
                if (orderedConnections[1] == CallManager.focusedConnection.value) {
                    showInfoButton(1, false)
                    binding.firstProgressBar.visibility = View.VISIBLE
                    CallManager.swap()
                }
            }
        }

        binding.secondDisplay.setOnClickListener {
            if (CallManager.isActiveStableState() && CallManager.connections.size == 2) {
                val orderedConnections = CallManager.orderedConnections()
                if (orderedConnections[0] == CallManager.focusedConnection.value) {
                    showInfoButton(2, false)
                    binding.secondProgressBar.visibility = View.VISIBLE
                    CallManager.swap()
                }
            }
        }

        /*******************************************************************************************
         * On click listeners for info buttons. Shows conference calls within a conference
         * connection. Should only be used when updateCallerDisplay() enables these buttons.
         ******************************************************************************************/

        binding.firstInfo.setOnClickListener {
            val action =
                InCallFragmentDirections.actionInCallFragmentToConferenceFragment()
            findNavController().navigate(action)
        }

        binding.secondInfo.setOnClickListener {
            val action =
                InCallFragmentDirections.actionInCallFragmentToConferenceFragment()
            findNavController().navigate(action)
        }

        /******************************************************************************************/

        binding.hangupActive.setOnClickListener {
            CallManager.hangup()
        }

        binding.addActive.setOnClickListener {
            InCallActivity.startDialer()
        }

        binding.swapActive.setOnClickListener {
            /**
             * Shows loading circle on holding connection during swaps.
             */
            val orderedConnections = CallManager.orderedConnections()
            if (orderedConnections.size == 2) {
                if (binding.firstText.text == "Active") {
                    showInfoButton(2, false)
                    binding.secondProgressBar.visibility = View.VISIBLE
                } else {
                    showInfoButton(1, false)
                    binding.firstProgressBar.visibility = View.VISIBLE
                }
            }
            CallManager.swap()
        }

        binding.mergeActive.setOnClickListener {
            /**
             * Shows loading circle on firstDisplay when merging.
             */
            binding.firstProgressBar.visibility = View.VISIBLE
            showInfoButton(1, false)
            CallManager.merge()
        }

        binding.speakerActive.setOnClickListener {
            AudioHelpers.toggleSpeaker(requireActivity())
        }

        binding.muteActive.setOnClickListener {
            AudioHelpers.toggleMute(requireActivity())
        }

        binding.keypadActive.setOnClickListener {
            Timber.i("DODODEBUG: Keypad button pressed!")
        }
    }

    override fun onStart() {
        super.onStart()
        updateCallerDisplay()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        Timber.i("DODODEBUG: InCallFragment Destroyed!")
    }

    /**
     * Prepares audio states and UI.
     */
    private fun prepareAudio() {
        /**
         * Reset speaker and mute states.
         */
        AudioHelpers.setSpeaker(context!!, false)
        AudioHelpers.setMute(context!!, false)

        /**
         * Set mute UI based on mute state.
         */
        AudioHelpers.muteStatus.observe(viewLifecycleOwner) { mute ->
            val color = if (mute) {
                ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.clicked_blue))
            } else {
                ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.icon_white))
            }

            binding.muteActive.iconTint = color
        }

        /**
         * Set speaker UI based on speaker state.
         */
        AudioHelpers.speakerStatus.observe(viewLifecycleOwner) { speaker ->
            val color = if (speaker) {
                ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.clicked_blue))
            } else {
                ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.icon_white))
            }

            binding.speakerActive.iconTint = color
        }
    }

    /**
     * Conference should disconnect when it is the only connection left and has 0 child calls.
     */
    private fun conferenceShouldDisconnect(): Boolean {
        val oneConnection = CallManager.connections.size == 1
        val children = CallManager.conferenceConnection()?.call?.children
        val noChildren = children?.size == 0

        return oneConnection && noChildren
    }

    /**
     * Checks if the single display UI should show. Single display should show when there is either
     * one non-disconnected call that is also not a conference or an outgoing call.
     */
    private fun singleDisplay(): Boolean {
        val nonDisconnectedCalls = CallManager.nonDisconnectedCalls()
        val singleCallNoConference = (
            nonDisconnectedCalls.size == 1
                && !nonDisconnectedCalls.firstOrNull().isConference()
            )

        val outgoing = CallManager.focusedCall.getStateCompat().let {
            it == Call.STATE_CONNECTING || it == Call.STATE_DIALING
        }

        return singleCallNoConference || outgoing
    }

    /**
     * Checks if the single conference UI should show. Single conference is when there is only
     * one live connection that is also a conference. Multi-caller display is used, but the
     * second display is hidden. A single conference occurs when there is a conference call and
     * either only one connection or repeat calls (refer to the UI Annotated Telecom State Diagram).
     */
    private fun singleConference(): Boolean {
        val hasConference = CallManager.conferenceConnection() != null
        val oneConnection = CallManager.connections.filter { it.state != Call.STATE_DISCONNECTED }.size == 1
        val hasRepeatCalls = CallManager.repeatCalls()

        return hasConference && (oneConnection || hasRepeatCalls)
    }

    /**
     * Small check to ensure that there are two live (non-disconnected) connections.
     */
    private fun twoConnections(): Boolean {
        return CallManager.orderedConnections().size == 2
    }

    /**
     * TODO: Possibly replace Exception for safer code.
     *
     * Returns the number of a Call object. Acts as helper to getNumberDisplay().
     */
    private fun getNumber(call: Call?): String {
        val temp: String? = call.number()
        val number = if (!temp.isNullOrEmpty()) {
            temp
        } else {
            if (temp == null) {
                throw Exception("Shouldn't have null number in single display.")
            } else {
                "Unknown"
            }
        }

        return number
    }

    /**
     * Returns the number display string for a Connection. If the connection is a conference, 0then
     * a different display string is used.
     */
    private fun getNumberDisplay(connection: Connection): String {
        return if (connection.isConference) {
            val conferenceSize = connection.call?.children?.size ?: 0
            "Conference (${conferenceSize} others)"
        } else {
            getNumber(connection.call)
        }
    }

    /**
     * Hides the loading circles within the multi-caller display.
     */
    private fun hideProgressBars() {
        binding.firstProgressBar.visibility = View.GONE
        binding.secondProgressBar.visibility = View.GONE
    }

    /**
     * If the first display contains the active connection, then the first display will be shown
     * in white and the second display will be shown in grey, and vise-versa.
     */
    private fun displayColor(firstActive: Boolean) {
        if (firstActive) {
            binding.firstNumber.setTextColor(ContextCompat.getColor(requireActivity(), R.color.icon_white))
            binding.firstText.setTextColor(ContextCompat.getColor(requireActivity(), R.color.icon_white))
            binding.firstDuration.setTextColor(ContextCompat.getColor(requireActivity(), R.color.icon_white))
            binding.firstInfo.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.icon_white))

            binding.secondNumber.setTextColor(ContextCompat.getColor(requireActivity(), R.color.holding_grey))
            binding.secondText.setTextColor(ContextCompat.getColor(requireActivity(), R.color.holding_grey))
            binding.secondDuration.setTextColor(ContextCompat.getColor(requireActivity(), R.color.holding_grey))
            binding.secondInfo.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.holding_grey))
        } else {
            binding.firstNumber.setTextColor(ContextCompat.getColor(requireActivity(), R.color.holding_grey))
            binding.firstText.setTextColor(ContextCompat.getColor(requireActivity(), R.color.holding_grey))
            binding.firstDuration.setTextColor(ContextCompat.getColor(requireActivity(), R.color.holding_grey))
            binding.firstInfo.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.holding_grey))

            binding.secondNumber.setTextColor(ContextCompat.getColor(requireActivity(), R.color.icon_white))
            binding.secondText.setTextColor(ContextCompat.getColor(requireActivity(), R.color.icon_white))
            binding.secondDuration.setTextColor(ContextCompat.getColor(requireActivity(), R.color.icon_white))
            binding.secondInfo.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.icon_white))
        }
    }

    /**
     * Shows or hides the conference info buttons.
     */
    private fun showInfoButton(button: Int, show: Boolean) {
        val infoButton = if (button == 1) binding.firstInfo else binding.secondInfo

        if (show) {
            infoButton.visibility = View.VISIBLE
            infoButton.isClickable = true
            infoButton.isFocusable = true
        } else {
            infoButton.visibility = View.INVISIBLE
            infoButton.isClickable = false
            infoButton.isFocusable = false
        }
    }

    /**
     * Makes the first and second display clickable or non-clickable. This is used to make the
     * displays clickable when swapping is allowed (so that the user can click a connection to
     * swap instead of having to press the swap button).
     */
    private fun displayClickable(clickable: Boolean) {
        binding.firstDisplay.isClickable = clickable
        binding.firstDisplay.isFocusable = clickable
        binding.secondDisplay.isClickable = clickable
        binding.secondDisplay.isFocusable = clickable
    }

    /**
     * TODO: Double check this logic to make sure all cases are handled correctly.
     *
     * Updates the caller display based on the current call / connection states. There are two
     * main layouts in the caller display: the single display and the multi-caller display. The
     * single display is used for when there is either only one live connection or there is
     * an outgoing call. The multi-caller display layout has a first and second display,
     * which are ordered vertically. When there is a single conference call, only the first
     * display of the multi-caller display is shown. When there are multiple connections, both the
     * first and second display are shown (they contain the active and holding connections).
     */
    private fun updateCallerDisplay() {

        Timber.i("DODODEBUG: incomingCall: ${CallManager.incomingCall}")
        Timber.i("DODODEBUG: incomingActivity running: ${IncomingCallActivity.running}")
        Timber.i("DODODEBUG: incomingActivity last answered: ${CallManager.lastAnsweredCall.number()}")
        Timber.i("DODODEBUG: connections size: ${CallManager.connections.size}")
        Timber.i("DODODEBUG: focusedCall: number = ${CallManager.focusedCall.number()}, " +
            "state = ${CallManager.callStateString(CallManager.focusedCall.getStateCompat())}")

        /**
         * Used to decide whether or not the UI should update when an incoming call is detected
         * and the IncomingActivity is not currently showing (refer to Call UI Flow). Leads
         * to smoother UI transitions.
         */
        if (CallManager.incomingCall && !IncomingCallActivity.running
            && CallManager.lastAnsweredCall != CallManager.focusedCall
        ) {
            Timber.i("DODODEBUG: INSIDE INCOMING RETURN")
            return
        }

        if (singleDisplay()) {
            // Single caller / outgoing call.
            Timber.i("DODODEBUG: SINGLE DISPLAY")

            // Switches out display views. Single caller display is used.
            binding.multiCallerDisplay.visibility = View.GONE
            binding.singleCallerDisplay.visibility = View.VISIBLE
            hideProgressBars()
            displayClickable(false)

            // Changes the call duration variables that InCallViewModel updates.
            inCallViewModel.singleMode = true

            /**
             * Only display number if contact doesn't exist. Otherwise display both number and contact.
             */
            val contactExists = false
            if (contactExists) {

            } else {
                binding.smallNumber.visibility = View.GONE
                binding.numberOrContact.text = getNumber(CallManager.focusedCall)
            }
        } else if (singleConference()) {
            // Single conference call.
            Timber.i("DODODEBUG: SINGLE CONFERENCE")

            // Switches out display views. Second display is hidden.
            binding.multiCallerDisplay.visibility = View.VISIBLE
            binding.singleCallerDisplay.visibility = View.GONE
            binding.secondDisplay.visibility = View.INVISIBLE
            hideProgressBars()
            displayClickable(false)

            inCallViewModel.singleMode = true

            // Shows conference info button and sets the color to white.
            showInfoButton(1, true)
            showInfoButton(2, false)
            displayColor(true)

            binding.firstNumber.text = getNumberDisplay(CallManager.focusedConnection.value!!)
            binding.firstText.text = "Active"

        } else if (twoConnections()) {
            // Two connections.
            Timber.i("DODODEBUG: TWO CONNECTIONS")

            // Switches out display views. Both first and second display are shown.
            binding.multiCallerDisplay.visibility = View.VISIBLE
            binding.singleCallerDisplay.visibility = View.GONE
            binding.secondDisplay.visibility = View.VISIBLE
            hideProgressBars()
            displayClickable(true)

            inCallViewModel.singleMode = false

            // Gets the connections in the order they were initially added.
            val orderedConnections = CallManager.orderedConnections()
            val firstConnection = orderedConnections[0]
            val secondConnection = orderedConnections[1]

            binding.firstNumber.text = getNumberDisplay(firstConnection!!)
            binding.secondNumber.text = getNumberDisplay(secondConnection!!)

            /*
            Sets the text of the displays. A display is set to white if it contains the active
            connection and is set to grey if it contains the holding connection.
             */
            val firstActive = firstConnection == CallManager.focusedConnection.value
            if (firstActive) {
                binding.firstText.text = "Active"
                binding.secondText.text = "Holding"
                displayColor(true)
            } else {
                binding.firstText.text = "Holding"
                binding.secondText.text = "Active"
                displayColor(false)
            }

            // Only shows info button for conference connections.
            if (firstConnection.isConference) {
                showInfoButton(1, true)
            } else {
                showInfoButton(1, false)
            }

            if (secondConnection.isConference) {
                showInfoButton(2, true)
            } else {
                showInfoButton(2, false)
            }
        }
    }

    /**
     * TODO: When pressing add button, DialerActivity doesn't show on screen because we didn't call
     *  the show over lock screen function in it. Make sure to require password to add calls.
     *
     *  Updates the add, swap, and merge buttons based off which actions are possible.
     */
    private fun updateButtons() {

        /**
         * Can only add calls if there is exactly one connection.
         */
        val addClickable = CallManager.isActiveStableState() && CallManager.connections.size == 1
        binding.addActive.isClickable = addClickable
        binding.addActive.isFocusable = addClickable

        if (addClickable) {
            binding.addActive.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.icon_white))
            binding.addText.setTextColor(ContextCompat.getColor(context!!, R.color.icon_white))
        } else {
            binding.addActive.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.disabled_grey))
            binding.addText.setTextColor(ContextCompat.getColor(context!!, R.color.disabled_grey))
        }

        /**
         * Can only swap calls if there are exactly two connections (holding, active).
         */
        val swapClickable = CallManager.isActiveStableState() && CallManager.connections.size == 2
        binding.swapActive.isClickable = swapClickable
        binding.swapActive.isFocusable = swapClickable

        if (swapClickable) {
            binding.swapActive.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.icon_white))
            binding.swapText.setTextColor(ContextCompat.getColor(context!!, R.color.icon_white))
        } else {
            binding.swapActive.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.disabled_grey))
            binding.swapText.setTextColor(ContextCompat.getColor(context!!, R.color.disabled_grey))
        }

        /**
         * Can only merge calls if there are exactly two connections (holding, active) and the
         * call is conferenceable.
         */
        val mergeClickable = CallManager.isActiveStableState() && CallManager.canMerge()
        binding.mergeActive.isClickable = mergeClickable
        binding.mergeActive.isFocusable = mergeClickable

        if (mergeClickable) {
            binding.mergeActive.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.icon_white))
            binding.mergeText.setTextColor(ContextCompat.getColor(context!!, R.color.icon_white))
        } else {
            binding.mergeActive.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.disabled_grey))
            binding.mergeText.setTextColor(ContextCompat.getColor(context!!, R.color.disabled_grey))
        }
    }
}