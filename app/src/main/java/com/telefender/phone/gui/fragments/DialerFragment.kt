package com.telefender.phone.gui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.telefender.phone.R
import com.telefender.phone.call_related.CallManager
import com.telefender.phone.databinding.FragmentDialerBinding
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.model.DialerViewModel


/**
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDialerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setAppBarTitle(getString(R.string.dialer))
        showAppBar()

        binding.apply {
            viewModel = dialerViewModel
            lifecycleOwner = viewLifecycleOwner
        }

        binding.dialPhone.setOnClickListener {
            makeCall()
        }

        binding.dial0.setOnClickListener {
            dialerViewModel.typeDigit(0)
            CallManager.keypad('0')
        }

        binding.dial1.setOnClickListener {
            dialerViewModel.typeDigit(1)
            CallManager.keypad('1')
        }

        binding.dial2.setOnClickListener {
            dialerViewModel.typeDigit(2)
            CallManager.keypad('2')
        }

        binding.dial3.setOnClickListener {
            dialerViewModel.typeDigit(3)
            CallManager.keypad('3')
        }

        binding.dial4.setOnClickListener {
            dialerViewModel.typeDigit(4)
            CallManager.keypad('4')
        }

        binding.dial5.setOnClickListener {
            dialerViewModel.typeDigit(5)
            CallManager.keypad('5')
        }

        binding.dial6.setOnClickListener {
            dialerViewModel.typeDigit(6)
            CallManager.keypad('6')
        }

        binding.dial7.setOnClickListener {
            dialerViewModel.typeDigit(7)
            CallManager.keypad('7')
        }

        binding.dial8.setOnClickListener {
            dialerViewModel.typeDigit(8)
            CallManager.keypad('8')
        }

        binding.dial9.setOnClickListener {
            dialerViewModel.typeDigit(9)
            CallManager.keypad('9')
        }

        binding.dialPound.setOnClickListener {
            dialerViewModel.typeSymbol(dialerViewModel.poundSign)
            CallManager.keypad('#')
        }

        binding.dialAsterisk.setOnClickListener {
            dialerViewModel.typeSymbol(dialerViewModel.asterisk)
            CallManager.keypad('*')
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun makeCall() {
        if (activity is MainActivity) {
            (activity as MainActivity).makeCallNoParam()
        }
    }

    private fun setAppBarTitle(title: String) {
        if (activity is MainActivity) {
            (activity as MainActivity).setTitle(title)
        }
    }

    private fun showAppBar() {
        if (activity is MainActivity) {
            (activity as MainActivity).displayAppBar(true)
        }
    }
}

