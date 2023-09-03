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
import com.telefender.phone.databinding.FragmentViewContactBinding
import com.telefender.phone.gui.CommonIntentsForUI
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.adapters.ViewContactAdapter
import com.telefender.phone.gui.adapters.recycler_view_items.ContactDataMimeType
import com.telefender.phone.gui.adapters.recycler_view_items.ViewContactBlockedStatus
import com.telefender.phone.gui.adapters.recycler_view_items.ViewContactData
import com.telefender.phone.gui.adapters.recycler_view_items.ViewContactHeader
import com.telefender.phone.gui.model.ContactsViewModel
import com.telefender.phone.gui.model.ContactsViewModelFactory
import com.telefender.phone.misc_helpers.DBL
import timber.log.Timber


/**
 * TODO: We either need to give the option to send sms / email to each specific data item, or do
 *  a long hold on he top call / sms / email buttons to show a drop down menu to let the user
 *  choose.
 *
 * TODO: Do we need to observe the contacts database for changes -> could just rely on
 *  updateContacts (or make a more generic wrapper function like notifyChange). -> Mostly done
 */
class ViewContactFragment : Fragment() {

    private var _binding: FragmentViewContactBinding? = null
    private val binding get() = _binding!!

    private val contactsViewModel: ContactsViewModel by activityViewModels {
        ContactsViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentViewContactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAppBar()
        hideBottomNavigation()

        Timber.e("$DBL: viewFormattedList start = ${contactsViewModel.viewFormattedList}")

        val applicationContext = requireContext().applicationContext
        val recyclerView = binding.recyclerView

        val adapter = ViewContactAdapter(
            applicationContext = applicationContext,
            photoOnClickListener = { item ->
                Timber.e("$DBL: ViewContact photo onClick!")

                when (item) {
                    is ViewContactHeader -> {
                        // TODO: Implement the contact photo.
                    }
                    else -> {}
                }
            },
            callOnClickListener = { item ->
                Timber.e("$DBL: ViewContact call button onClick!")

                when (item) {
                    is ViewContactHeader -> {
                        item.primaryNumber?.let {
                            CallHelpers.makeCall(
                                context = applicationContext,
                                number = it
                            )
                        }
                    }
                    else -> {}
                }
            },
            messageOnClickListener = { item ->
                Timber.e("$DBL: ViewContact message button onClick!")

                when (item) {
                    is ViewContactHeader -> {
                        item.primaryNumber?.let {
                            CommonIntentsForUI.sendSMSIntent(
                                activity = requireActivity(),
                                number = it
                            )
                        }
                    }
                    else -> {}
                }
            },
            emailOnClickListener = { item ->
                Timber.e("$DBL: ViewContact email button onClick!")

                when (item) {
                    is ViewContactHeader -> {
                        item.primaryEmail?.let {
                            CommonIntentsForUI.sendEmailIntent(
                                activity = requireActivity(),
                                email = it
                            )
                        }
                    }
                    else -> {}
                }
            },
            blockOnClickListener = { item ->
                Timber.e("$DBL: ViewContact block button onClick!")

                when (item) {
                    is ViewContactBlockedStatus -> {
                        contactsViewModel.changeIsBlockedWrapper(viewContactBlockedStatus = item)
                    }
                    else -> {}
                }
            },
            dataOnClickListener = { item ->
                Timber.e("$DBL: ViewContact data item onClick!")

                when (item) {
                    is ViewContactData -> {
                        val data = item.contactData

                        when (data.mimeType) {
                            ContactDataMimeType.PHONE -> {
                                CallHelpers.makeCall(applicationContext, data.value)
                            }
                            ContactDataMimeType.EMAIL -> {
                                CommonIntentsForUI.sendEmailIntent(
                                    activity = requireActivity(),
                                    email = data.value
                                )
                            }
                            ContactDataMimeType.ADDRESS -> {
                                // TODO: Implement the map intent.
                            }
                            else -> {}
                        }
                    }
                    else -> {}
                }
            },
        )

        adapter.setHasStableIds(true)
        recyclerView.adapter = adapter

        adapter.submitList(contactsViewModel.viewFormattedList)
        contactsViewModel.viewFormattedIndicatorLiveData.observe(viewLifecycleOwner) {
            adapter.submitList(contactsViewModel.viewFormattedList.toList())
        }
    }

    override fun onResume() {
        super.onResume()
        updateAppBar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupAppBar() {
        if (activity is MainActivity) {
            val act = (activity as MainActivity)

            // Cleans old app bar before setting up new app bar
            act.revertAppBar()

            // New app bar stuff
            updateAppBar()

            // Actually show app bar
            act.displayAppBar(true)
        }
    }

    private fun updateAppBar() {
        if (activity is MainActivity) {
            val act = (activity as MainActivity)

            // New app bar stuff
            act.displayUpButton(true)
            act.displayMoreMenu(false)
            act.displayAppBarTextButton(show2 = true, text2 = "Edit")

            act.setAppBarTextButtonOnClickListener(
                fragment2 = R.id.viewContactFragment,
                onClickListener2 = {
                    val action = ViewContactFragmentDirections.actionViewContactFragmentToChangeContactFragment()
                    findNavController().navigate(action)
                }
            )
        }
    }

    private fun hideBottomNavigation() {
        if (activity is MainActivity) {
            val act = (activity as MainActivity)
            act.displayBottomNavigation(false)
        }
    }
}