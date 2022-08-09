package com.dododial.phone.gui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.dododial.phone.databinding.FragmentCallHistoryBinding
import com.dododial.phone.gui.MainActivity
import com.dododial.phone.gui.adapters.CallHistoryAdapter
import com.dododial.phone.gui.model.RecentsViewModel
import com.dododial.phone.gui.model.RecentsViewModelFactory
import java.text.SimpleDateFormat
import java.util.*


class CallHistoryFragment : Fragment() {

    private var _binding: FragmentCallHistoryBinding? = null
    private val binding get() = _binding!!
    private val recentsViewModel: RecentsViewModel by activityViewModels {
        RecentsViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentCallHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyAppBar(true)

        val number = recentsViewModel.selectNumber

        val simpleDate = SimpleDateFormat("MM/dd/yy")
        val date = Date(recentsViewModel.selectTime)
        val time = simpleDate.format(date)

        val context = context!!
        val recyclerView = binding.recyclerView

        val adapter = CallHistoryAdapter(context, number, time)

        // To disable recycler view blinking (smooth reloading experience).
        adapter.setHasStableIds(true)
        recyclerView.adapter = adapter

        /**
         * Observes call logs in recentViewModel while fragment is active and updates day logs
         * correspondingly.
         */
        adapter.submitList(recentsViewModel.dayLogs)
        recentsViewModel.callLogs.observe(viewLifecycleOwner) {
            recentsViewModel.updateDayLogs()
            adapter.submitList(recentsViewModel.dayLogs)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        revertAppbar()
    }

    private fun historyAppBar(isContact: Boolean) {
        if (activity is MainActivity) {
            val act = (activity as MainActivity)
            act.displayUpButton(true)
            act.setTitle("")
            act.displayMoreMenu(false)
            act.displayEditOrAdd(true, isContact)
        }
    }

    private fun revertAppbar() {
        if (activity is MainActivity) {
            val act = (activity as MainActivity)
            act.displayUpButton(false)
            act.displayMoreMenu(true)
            act.displayEditOrAdd(false, false)
        }
    }
}