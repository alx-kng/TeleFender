package com.dododial.phone.call_related

import android.os.Bundle
import android.telecom.Call
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.dododial.phone.databinding.FragmentConferenceBinding

class ConferenceFragment : Fragment() {

    private var _binding: FragmentConferenceBinding? = null
    private val binding get() = _binding!!
    private var lastAnswered : Call? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentConferenceBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * TODO: Problem with call automatically hanging up in conference connection (with only one
     *  call) and inside this fragment. (May not be a big problem)
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Up button action.
        binding.conferenceAppbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        val context = context!!
        val recyclerView = binding.conferenceRecyclerView
        val adapter = ConferenceAdapter(context)

        adapter.setHasStableIds(true)
        recyclerView.adapter = adapter

        val conferenceCalls = CallManager.conferenceConnection()?.call?.children ?: emptyList()
        adapter.submitList(conferenceCalls)

        lastAnswered = CallManager.lastAnsweredCall
        CallManager.focusedConnection.observe(viewLifecycleOwner) {
            /**
             * If a new call is answered, the ConferenceFragment closes.
             */
            if (lastAnswered != CallManager.lastAnsweredCall) {
                findNavController().popBackStack()
            }

            /**
             * Updates recycler view with new conference calls. Closes ConferenceFragment if there
             * are no conference children left.
             */
            val children = CallManager.conferenceConnection()?.call?.children
            val calls = children?.filter {
                it.getStateCompat() != Call.STATE_DISCONNECTED
            }

            if (!calls.isNullOrEmpty()) {
                adapter.submitList(calls)
            } else {
                findNavController().popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}