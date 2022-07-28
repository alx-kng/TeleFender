package com.dododial.phone.call_related

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.dododial.phone.DialerActivity
import com.dododial.phone.R
import com.dododial.phone.databinding.FragmentInCallBinding
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
            if (connection == null) {
                requireActivity().finishAndRemoveTask()
                Timber.i("DODODEBUG: IN CALL FINISHED!")
            } else {
                updateScreen()
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

    private fun updateScreen() {

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

        /**
         * Display only number if contact doesn't exist. Otherwise display both number and contact.
         */
        val temp: String? = CallManager.focusedCall.number()
        val number = if (!temp.isNullOrEmpty()) {
            temp
        } else {
            if (temp == null) "Conference" else "Unknown"
        }

        val contactExists = false
        if (contactExists) {

        } else {
            binding.smallNumber.visibility = View.GONE
            binding.numberOrContact.text = number
        }

    }
}