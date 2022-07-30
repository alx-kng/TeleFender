package com.dododial.phone.call_related

import android.content.res.ColorStateList
import android.os.Bundle
import android.telecom.Call
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.dododial.phone.DialerActivity
import com.dododial.phone.R
import com.dododial.phone.call_related.InCallActivity.Companion.context
import com.dododial.phone.databinding.FragmentInCallBinding
import kotlinx.coroutines.NonCancellable.children
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

        binding.apply {
            viewModel = inCallViewModel
            lifecycleOwner = viewLifecycleOwner
        }

        prepareAudio()

        /**
         * Observes and updates UI based off current focusedConnection
         */
        CallManager.focusedConnection.observe(viewLifecycleOwner) { connection ->
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

        binding.hangupActive.setOnClickListener {
            CallManager.hangup()
        }

        binding.addActive.setOnClickListener {
            DialerActivity.start(requireActivity())
        }

        binding.swapActive.setOnClickListener {
            CallManager.swap()
        }

        binding.mergeActive.setOnClickListener {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        Timber.i("DODODEBUG: InCallFragment Destroyed!")
    }

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

    private fun conferenceShouldDisconnect(): Boolean {
        val oneConnection = CallManager.connections.size == 1

        val children = CallManager.conferenceConnection()?.call?.children
        val noChildren = children?.size == 0

        return oneConnection && noChildren
    }

    private fun singleDisplay(): Boolean {
        val singleNonConference = (
            CallManager.isStableState()
                && CallManager.connections.size == 1
                && !(CallManager.focusedConnection.value?.isConference ?: false)
            )

        val outgoing = (
            CallManager.focusedCall.getStateCompat() == Call.STATE_CONNECTING
                || CallManager.focusedCall.getStateCompat() == Call.STATE_DIALING
            )

        val twoNotActiveStable = !CallManager.isActiveStableState() && CallManager.connections.size == 2
        val twoNotHolding = CallManager.holdingConnections().size != 2
        val noConference = CallManager.conferenceConnection() == null
        val unstableConference = CallManager.calls.size != CallManager.connections.size

        return singleNonConference
            || (twoNotActiveStable && twoNotHolding && noConference && !unstableConference)
            || outgoing
    }

    private fun singleConference(): Boolean {
        val hasConference = CallManager.conferenceConnection() != null
        val oneConnection = CallManager.connections.filter { it.state != Call.STATE_DISCONNECTED }.size == 1
        val threeConnections = CallManager.connections.size == 3

        return hasConference && (oneConnection || threeConnections)
    }

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

    private fun getNumberDisplay(connection: Connection): String {
        return if (connection.isConference) {
            val conferenceSize = connection.call?.children?.size ?: 0
            "Conference (${conferenceSize} others)"
        } else {
            getNumber(connection.call)
        }
    }

    private fun displayColor(firstActive: Boolean) {
        if (firstActive) {
            binding.firstNumber.setTextColor(ContextCompat.getColor(requireActivity(), R.color.icon_white))
            binding.firstText.setTextColor(ContextCompat.getColor(requireActivity(), R.color.icon_white))
            binding.firstDuration.setTextColor(ContextCompat.getColor(requireActivity(), R.color.icon_white))

            binding.secondNumber.setTextColor(ContextCompat.getColor(requireActivity(), R.color.holding_grey))
            binding.secondText.setTextColor(ContextCompat.getColor(requireActivity(), R.color.holding_grey))
            binding.secondDuration.setTextColor(ContextCompat.getColor(requireActivity(), R.color.holding_grey))
        } else {
            binding.firstNumber.setTextColor(ContextCompat.getColor(requireActivity(), R.color.holding_grey))
            binding.firstText.setTextColor(ContextCompat.getColor(requireActivity(), R.color.holding_grey))
            binding.firstDuration.setTextColor(ContextCompat.getColor(requireActivity(), R.color.holding_grey))

            binding.secondNumber.setTextColor(ContextCompat.getColor(requireActivity(), R.color.icon_white))
            binding.secondText.setTextColor(ContextCompat.getColor(requireActivity(), R.color.icon_white))
            binding.secondDuration.setTextColor(ContextCompat.getColor(requireActivity(), R.color.icon_white))
        }
    }

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
     * TODO: Double check this logic to make sure all cases are handled correctly.
     *  Also, there seems to be a problem where the audio isn't connected sometimes (when there
     *  are two calls), particularly when swapping calls (may or may not be fixed).
     *  Need to catch in-between states to ensure smoother UI transition.
     */
    private fun updateCallerDisplay() {

        if (singleDisplay()) {
            binding.multiCallerDisplay.visibility = View.GONE
            binding.singleCallerDisplay.visibility = View.VISIBLE

            Timber.i("DODODEBUG: SINGLE DISPLAY")
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
        } else {
            binding.multiCallerDisplay.visibility = View.VISIBLE
            binding.singleCallerDisplay.visibility = View.GONE

            // Single conference call.
            if (singleConference()) {
                binding.secondDisplay.visibility = View.INVISIBLE

                inCallViewModel.singleMode = true

                showInfoButton(1, true)
                showInfoButton(2, false)
                displayColor(true)

                Timber.i("DODODEBUG: SINGLE CONFERENCE")

                val conferenceSize = CallManager.focusedCall?.children?.size ?: 0
                binding.firstNumber.text = "Conference (${conferenceSize} others)"
                binding.firstText.text = "Active"
            } else {
                // Two connections.

                binding.secondDisplay.visibility = View.VISIBLE
                inCallViewModel.singleMode = false

                val orderedConnections = CallManager.orderedConnections()

                val firstConnection = orderedConnections.first()
                val secondConnection = if (orderedConnections.size == 1) {
                    throw Exception("Second connection was null!")
                } else {
                    orderedConnections[1]
                }

                binding.firstNumber.text = getNumberDisplay(firstConnection!!)
                binding.secondNumber.text = getNumberDisplay(secondConnection!!)

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

                if (firstConnection.isConference && firstActive) {
                    showInfoButton(1, true)
                } else {
                    showInfoButton(1, false)
                }

                if (secondConnection.isConference && !firstActive) {
                    showInfoButton(2, true)
                } else {
                    showInfoButton(2, false)
                }
            }
        }
    }

    /**
     * TODO: When pressing add button, DialerActivity doesn't show on screen because we didn't call
     *  the show over lock screen function in it.
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