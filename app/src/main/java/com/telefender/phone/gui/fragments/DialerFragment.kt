package com.telefender.phone.gui.fragments

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.telefender.phone.R
import com.telefender.phone.databinding.FragmentDialerBinding
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.model.DialerViewModel
import com.telefender.phone.permissions.Permissions

// TODO: Make number display touchable / editable
// TODO: FUCKING PROBLEM WITH BACK BUTTON IN DIALER FRAGMENT
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

        Permissions.multiplePermissions(requireContext(), activity as Activity)
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

