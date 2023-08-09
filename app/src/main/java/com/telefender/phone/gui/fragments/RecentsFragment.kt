package com.telefender.phone.gui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.telefender.phone.App
import com.telefender.phone.R
import com.telefender.phone.call_related.CallHelpers
import com.telefender.phone.databinding.FragmentRecentsBinding
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.adapters.RecentsAdapter
import com.telefender.phone.gui.model.RecentsViewModel
import com.telefender.phone.gui.model.RecentsViewModelFactory
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.DatabaseLogger
import com.telefender.phone.misc_helpers.PrintTypes
import timber.log.Timber

/*
TODO: Handle case where permissions aren't given (or default dialer isn't granted).

TODO: Sometimes Voicemail is sorted incorrectly in recents list. Particularly when you
 call back the +1 number. In general, sometimes Voicemail number is sorted below the
 associated missed / rejected / blocked call... But maybe we can't change this.
 */
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

        setupAppBar()
        showBottomNavigation()

        val applicationContext = requireContext().applicationContext
        val recyclerView = binding.recentsRecyclerView

        /**
         * First lambda passed to initiate CallActivity when Recycler View item is clicked.
         * Second lambda passed to initiate CallHistory Fragment when info button is clicked
         */
        val adapter = RecentsAdapter(applicationContext,
            { number ->
                CallHelpers.makeCall(applicationContext, number)
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
    }

    /**
     * TODO: Currently just here for checking in on status of AnalyzedNumber. Remove / find better
     *  way to check status later.
     */
    override fun onStart() {
        super.onStart()

        val repository = (requireContext().applicationContext as App).repository
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupAppBar() {
        if (activity is MainActivity) {
            Timber.i("$DBL: RecentsFragment - setupAppBar()!")

            val act = activity as MainActivity

            // Cleans old app bar before setting up new app bar
            act.revertAppBar()

            // New app bar stuff
            act.setTitle(getString(R.string.recents_title))

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
