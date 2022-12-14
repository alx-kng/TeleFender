package com.telefender.phone.gui.fragments

import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.telefender.phone.R
import com.telefender.phone.databinding.FragmentRecentsBinding
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.adapters.RecentsAdapter
import com.telefender.phone.gui.model.RecentsViewModel
import com.telefender.phone.gui.model.RecentsViewModelFactory
import com.telefender.phone.helpers.MiscHelpers


class RecentsFragment : Fragment() {

    private var _binding: FragmentRecentsBinding? = null
    private val binding get() = _binding!!
    private val recentsViewModel: RecentsViewModel by activityViewModels {
        RecentsViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentRecentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setAppBarTitle(getString(R.string.recents))
        showAppBar()

        val context = context!!
        val recyclerView = binding.recentsRecyclerView

        /**
         * First lambda passed to initiate CallActivity when Recycler View item is clicked.
         * Second lambda passed to initiate CallHistory Fragment when info button is clicked
         */
        val adapter = RecentsAdapter(context,
            { number ->
                makeCall(number)
            },
            { number, epochTime ->
                recentsViewModel.retrieveDayLogs(number, epochTime)
                val action = RecentsFragmentDirections.actionRecentsFragmentToCallHistoryFragment()
                findNavController().navigate(action)
            })

        // To disable recycler view blinking (smooth reloading experience).
        adapter.setHasStableIds(true)
        recyclerView.adapter = adapter

        adapter.submitList(recentsViewModel.groupedCallLogs)
        recentsViewModel.callLogs.observe(viewLifecycleOwner) {
            adapter.submitList(recentsViewModel.groupedCallLogs)
        }

        // TODO: Handle case where permissions aren't given (or default dialer isn't granted).
        requireActivity().applicationContext.contentResolver.registerContentObserver(
            android.provider.CallLog.Calls.CONTENT_URI,
            true,
            CallLogObserver(Handler(Looper.getMainLooper()), recentsViewModel)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun makeCall(number: String) {
        if (activity is MainActivity) {
            (activity as MainActivity).makeCallParam(number)
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

    class CallLogObserver(
        handler: Handler,
        val recentsViewModel: RecentsViewModel
    ) : ContentObserver(handler) {

        override fun deliverSelfNotifications(): Boolean {
            return true
        }

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            Log.i("${MiscHelpers.DEBUG_LOG_TAG}", "NEW CALL LOG")

            recentsViewModel.updateCallLogs()
        }
    }

}