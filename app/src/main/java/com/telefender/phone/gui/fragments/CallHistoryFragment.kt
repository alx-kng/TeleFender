package com.telefender.phone.gui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.telefender.phone.R
import com.telefender.phone.call_related.CallHelpers
import com.telefender.phone.databinding.FragmentCallHistoryBinding
import com.telefender.phone.gui.CommonIntentsForUI
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.adapters.CallHistoryAdapter
import com.telefender.phone.gui.adapters.recycler_view_items.CallHistoryBlockedStatus
import com.telefender.phone.gui.adapters.recycler_view_items.CallHistoryHeader
import com.telefender.phone.gui.adapters.recycler_view_items.CallHistorySafetyStatus
import com.telefender.phone.gui.adapters.recycler_view_items.common_types.SafetyStatus
import com.telefender.phone.gui.model.ContactsViewModel
import com.telefender.phone.gui.model.ContactsViewModelFactory
import com.telefender.phone.gui.model.RecentsViewModel
import com.telefender.phone.gui.model.RecentsViewModelFactory
import com.telefender.phone.misc_helpers.DBL
import timber.log.Timber


/**
 * TODO: THERE MAY STILL BE A PROBLEM WITH "EDIT" SHOWING FOR NON-CONTACT NUMBERS. KEEP CHECKING!
 *  -> Think it might be fixed actually, but double check!
 *
 * TODO: CallHistoryFragment VERY VERY VERY OCCASIONALLY SHOWS BLANK AFTER DOING SOME SORT OF
 *  ACTION (unsure). Only happened once, but look out for it.
 */
class CallHistoryFragment : Fragment() {

    private var _binding: FragmentCallHistoryBinding? = null
    private val binding get() = _binding!!

    private val recentsViewModel: RecentsViewModel by activityViewModels {
        RecentsViewModelFactory(requireActivity().application)
    }

    private val contactsViewModel: ContactsViewModel by activityViewModels {
        ContactsViewModelFactory(requireActivity().application)
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

        setupAppBar(recentsViewModel.getCurrentCID() != null)
        hideBottomNavigation()

        val applicationContext = requireContext().applicationContext
        val recyclerView = binding.recyclerView

        val adapter = CallHistoryAdapter(
            applicationContext = applicationContext,
            photoOnClickListener = { item ->
                Timber.e("$DBL: CallHistory photo onClick!")

                when (item) {
                    is CallHistoryHeader -> {
                        // TODO: Implement the contact photo.
                    }
                    else -> {}
                }
            },
            callOnClickListener = { item ->
                Timber.e("$DBL: CallHistory call button onClick!")

                when (item) {
                    is CallHistoryHeader -> {
                        CallHelpers.makeCall(
                            context = applicationContext,
                            number = item.associatedNumber
                        )
                    }
                    else -> {}
                }
            },
            messageOnClickListener = { item ->
                Timber.e("$DBL: CallHistory message button onClick!")

                when (item) {
                    is CallHistoryHeader -> {
                        CommonIntentsForUI.sendSMSIntent(
                            activity = requireActivity(),
                            number = item.associatedNumber
                        )
                    }
                    else -> {}
                }
            },
            emailOnClickListener = { item ->
                Timber.e("$DBL: CallHistory email button onClick!")

                when (item) {
                    is CallHistoryHeader -> {
                        item.primaryEmail?.let {
                            CommonIntentsForUI.sendEmailIntent(
                                activity = requireActivity(),
                                email = it,
                            )
                        }
                    }
                    else -> {}
                }
            },
            blockOnClickListener = { item ->
                Timber.e("$DBL: CallHistory block button onClick!")

                when (item) {
                    is CallHistoryBlockedStatus -> {
                        recentsViewModel.changeIsBlockedWrapper(callHistoryBlockedStatus = item)
                    }
                    else -> {}
                }
            },
            spamOnClickListener = { item ->
                Timber.e("$DBL: CallHistory spam button onClick!")

                when (item) {
                    is CallHistorySafetyStatus -> {
                        recentsViewModel.changeSafetyStatusWrapper(
                            callHistorySafetyStatus = item,
                            newSafetyStatus = SafetyStatus.SPAM
                        )
                    }
                    else -> {}
                }
            },
            defaultOnClickListener = { item ->
                Timber.e("$DBL: CallHistory default button onClick!")

                when (item) {
                    is CallHistorySafetyStatus -> {
                        recentsViewModel.changeSafetyStatusWrapper(
                            callHistorySafetyStatus = item,
                            newSafetyStatus = SafetyStatus.DEFAULT
                        )
                    }
                    else -> {}
                }
            },
            safeOnClickListener = { item ->
                Timber.e("$DBL: CallHistory safe button onClick!")

                when (item) {
                    is CallHistorySafetyStatus -> {
                        recentsViewModel.changeSafetyStatusWrapper(
                            callHistorySafetyStatus = item,
                            newSafetyStatus = SafetyStatus.SAFE
                        )
                    }
                    else -> {}
                }
            },
        )

        // To disable recycler view blinking (smooth reloading experience).
        adapter.setHasStableIds(true)
        recyclerView.adapter = adapter

        adapter.submitList(recentsViewModel.dayLogs)
        recentsViewModel.dayLogIndicatorLiveData.observe(viewLifecycleOwner) {
            adapter.submitList(recentsViewModel.dayLogs.toList())

            // Updates button2 text to accurately reflect the Edit / Add once contact is loaded.
            updateAppBar(isContact = recentsViewModel.getCurrentCID() != null)

            // Preloads contactsViewModel in case of edit (smoother UI transition).
            contactsViewModel.setDataLists(selectCID = recentsViewModel.getCurrentCID())
        }
    }

    override fun onResume() {
        super.onResume()
        updateAppBar(recentsViewModel.getCurrentCID() != null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupAppBar(isContact: Boolean) {
        if (activity is MainActivity) {
            val act = (activity as MainActivity)

            // Cleans old app bar before setting up new app bar
            act.revertAppBar()

            // New app bar stuff
            act.displayUpButton(true)
            act.displayMoreMenu(false)

            // Prevents blinking from Add to Edit when there is no header in the day logs yet.
            if (!recentsViewModel.hasHeader()) {
                act.displayAppBarTextButton()
            } else {
                act.displayAppBarTextButton(show2 = true, text2 = if (isContact) "Edit" else "Add")
            }

            // Actually show app bar
            act.displayAppBar(true)
        }
    }

    private fun updateAppBar(isContact: Boolean) {
        if (activity is MainActivity) {
            val act = (activity as MainActivity)

            act.displayUpButton(true)
            act.displayMoreMenu(false)

            // Prevents blinking from Add to Edit when there is no header in the day logs yet.
            if (!recentsViewModel.hasHeader()) {
                act.displayAppBarTextButton()
            } else if (isContact) {
                act.displayAppBarTextButton(show2 = true, text2 = "Edit")
                act.setAppBarTextButtonOnClickListener(
                    fragment2 = R.id.callHistoryFragment,
                    onClickListener2 = {
                        val action = CallHistoryFragmentDirections.actionCallHistoryFragmentToChangeContactFragment()
                        findNavController().navigate(action)
                    }
                )
            } else {
                act.displayAppBarTextButton(show2 = true, text2 = "Add")
                act.setAppBarTextButtonOnClickListener(
                    fragment2 = R.id.callHistoryFragment,
                    onClickListener2 = {
                        contactsViewModel.setDataLists(
                            selectCID = null,
                            startingNumber = recentsViewModel.selectNumber
                        )
                        val action = CallHistoryFragmentDirections.actionCallHistoryFragmentToChangeContactFragment()
                        findNavController().navigate(action)
                    }
                )
            }
        }
    }

    private fun hideBottomNavigation() {
        if (activity is MainActivity) {
            val act = (activity as MainActivity)
            act.displayBottomNavigation(false)
        }
    }
}