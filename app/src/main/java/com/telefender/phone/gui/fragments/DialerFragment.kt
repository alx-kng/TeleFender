package com.telefender.phone.gui.fragments

import android.os.Bundle
import android.telecom.Call
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.telefender.phone.R
import com.telefender.phone.call_related.CallHelpers
import com.telefender.phone.call_related.CallManager
import com.telefender.phone.call_related.stateCompat
import com.telefender.phone.databinding.FragmentDialerBinding
import com.telefender.phone.gui.InCallActivity
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.model.DialerViewModel
import com.telefender.phone.misc_helpers.DBL
import timber.log.Timber


/**
 * TODO: UP BUTTON IN KEYPAD DIALER CAUSES PROBLEMS!
 *
 * TODO: Make number display touchable / editable
 *
 * TODO: FUCKING PROBLEM WITH BACK BUTTON IN DIALER FRAGMENT
 *
 * TODO: Double check keypad usage.
 */
class DialerFragment : Fragment() {

    private var _binding: FragmentDialerBinding? = null
    private val binding get() = _binding!!
    private val dialerViewModel: DialerViewModel by activityViewModels()

    private var lastAnswered : Call? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDialerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAppBar()
        showBottomNavigation()

        binding.apply {
            viewModel = dialerViewModel
            lifecycleOwner = viewLifecycleOwner
        }

        binding.dialPhone.setOnClickListener {
            val number = dialerViewModel.dialNumber.value?.trim() ?: ""
            if (number != "") {
                CallHelpers.makeCall(requireContext(), dialerViewModel.dialNumber.value)
            } else {
                Toast.makeText(activity, "Not a valid number!", Toast.LENGTH_SHORT).show()
            }
        }

        // Only show dial button if in regular dial screen (don't want to dial from in-call keypad).
        binding.dialPhone.visibility = if (dialerViewModel.fromInCall) View.GONE else View.VISIBLE

        binding.dial0Layout.setOnClickListener {
            dialerViewModel.typeDigit(0)
            CallManager.keypad('0')
        }

        binding.dial1Layout.setOnClickListener {
            dialerViewModel.typeDigit(1)
            CallManager.keypad('1')
        }

        binding.dial2Layout.setOnClickListener {
            dialerViewModel.typeDigit(2)
            CallManager.keypad('2')
        }

        binding.dial3Layout.setOnClickListener {
            dialerViewModel.typeDigit(3)
            CallManager.keypad('3')
        }

        binding.dial4Layout.setOnClickListener {
            dialerViewModel.typeDigit(4)
            CallManager.keypad('4')
        }

        binding.dial5Layout.setOnClickListener {
            dialerViewModel.typeDigit(5)
            CallManager.keypad('5')
        }

        binding.dial6Layout.setOnClickListener {
            dialerViewModel.typeDigit(6)
            CallManager.keypad('6')
        }

        binding.dial7Layout.setOnClickListener {
            dialerViewModel.typeDigit(7)
            CallManager.keypad('7')
        }

        binding.dial8Layout.setOnClickListener {
            dialerViewModel.typeDigit(8)
            CallManager.keypad('8')
        }

        binding.dial9Layout.setOnClickListener {
            dialerViewModel.typeDigit(9)
            CallManager.keypad('9')
        }

        binding.dialPoundLayout.setOnClickListener {
            dialerViewModel.typeSymbol(dialerViewModel.poundSign)
            CallManager.keypad('#')
        }

        binding.dialAsteriskLayout.setOnClickListener {
            dialerViewModel.typeSymbol(dialerViewModel.asterisk)
            CallManager.keypad('*')
        }

        if (activity is InCallActivity) {
            lastAnswered = CallManager.lastAnsweredCall

            CallManager.focusedConnection.observe(viewLifecycleOwner) {
                /**
                 * If a new call is answered or no new calls left, the DialerFragment (as keypad)
                 * closes.
                 */
                if (lastAnswered != CallManager.lastAnsweredCall || it == null) {
                    findNavController().popBackStack()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupAppBar() {
        if (activity is MainActivity) {
            Timber.i("$DBL: DialerFragment - MainActivity - setupAppBar()!")

            val act = activity as MainActivity

            // Cleans old app bar before setting up new app bar
            act.revertAppBar()

            // New app bar stuff
            act.setTitle(getString(R.string.dialer_title))

            // Only show up button when accessed as keypad from in call
            act.displayUpButton(false)
            act.displayMoreMenu(true)

            // Actually show app bar
            act.displayAppBar(true)
        } else if (activity is InCallActivity) {
            Timber.i("$DBL: DialerFragment - InCallActivity - setupAppBar()!")

            val act = activity as InCallActivity

            // New app bar stuff
            act.setTitle(getString(R.string.keypad_title))

            // Only show up button when accessed as keypad from in call
            act.displayUpButton(true)

            // Actually show app bar
            act.displayAppBar(true)
        }
    }

    private fun showBottomNavigation() {
        if (activity is MainActivity) {
            val act = (activity as MainActivity)
            act.displayBottomNavigation(true)
        }
    }
}

