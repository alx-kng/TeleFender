package com.telefender.phone.gui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.telefender.phone.R
import com.telefender.phone.databinding.FragmentContactsBinding
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.adapters.ContactsAdapter
import com.telefender.phone.gui.adapters.recycler_view_items.AggregateContact
import com.telefender.phone.gui.decoration.ContactHeaderDecoration
import com.telefender.phone.gui.model.ContactsViewModel
import com.telefender.phone.gui.model.ContactsViewModelFactory
import com.telefender.phone.misc_helpers.DBL
import timber.log.Timber


/**
 * TODO: Don't update adapter list if there isn't a visual change. Or at least stop the UI from
 *  scrolling to the bottom when there is a new update.
 *
 * TODO: THERE IS SOME SORT OF LEAK HERE (One with actual Fragment and one with LinearLayout)
 *
 * TODO: Consider not showing contacts that don't have a number associated with them.
 *
 * TODO: If you go to CallHistoryFragment, tap on ContactsFragment, and press back, then we go
 *  back to CallHistoryFragment, but I feel like we should stay in ContactsFragment.
 */
class ContactsFragment : Fragment() {

    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!
    private val contactsViewModel : ContactsViewModel by activityViewModels {
        ContactsViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Timber.i("$DBL: ContactsFragment - onViewCreated() - $this")

        setupAppBar()
        showBottomNavigation()

        val recyclerView = binding.contactsRecyclerView

        val adapter = ContactsAdapter(requireContext().applicationContext) { item ->
            /*
            TODO: For now, this goes to the ChangeContactFragment for testing, but later, we'll
             make it go to the Contact detail fragment.
             */
            Timber.e("$DBL: Contact onClick! Contact = $item")

            when(item) {
                is AggregateContact -> {
                    contactsViewModel.setDataLists(selectCID = item.aggregateID.toString())
                    val action = ContactsFragmentDirections.actionContactsFragmentToChangeContactFragment()
                    findNavController().navigate(action)
                }
                else -> {}
            }
        }
        adapter.setHasStableIds(true)
        recyclerView.adapter = adapter

        /**
         * Creates header view for contacts screen. Header always shows the first letter of the
         * currently scrolled contact (visible contact closest to the top of the screen).
         */
        val headerDecoration = ContactHeaderDecoration { letter ->
            binding.letter.text = letter ?: binding.letter.text
        }
        recyclerView.addItemDecoration(headerDecoration)

        adapter.submitList(contactsViewModel.dividedContacts)
        contactsViewModel.contacts.observe(viewLifecycleOwner) {
            adapter.submitList(contactsViewModel.dividedContacts)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupAppBar() {
        if (activity is MainActivity) {
            Timber.i("$DBL: ContactsFragment - setupAppBar()!")

            val act = activity as MainActivity

            // Cleans old app bar before setting up new app bar
            act.revertAppBar()

            // New app bar stuff
            act.setTitle(getString(R.string.contacts_title))
            act.displayAppBarTextButton(show2 = true, text2 = "Add")
            act.setAppBarTextButtonOnClickListener(onClickListener2 = {
                contactsViewModel.setDataLists(selectCID = null)
                val action = ContactsFragmentDirections.actionContactsFragmentToChangeContactFragment()
                findNavController().navigate(action)
            })

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