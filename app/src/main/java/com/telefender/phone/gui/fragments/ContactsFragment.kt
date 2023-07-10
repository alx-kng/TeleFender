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
import com.telefender.phone.gui.decoration.ContactHeaderDecoration
import com.telefender.phone.gui.model.ContactsViewModel
import com.telefender.phone.gui.model.ContactsViewModelFactory
import com.telefender.phone.misc_helpers.DBL
import timber.log.Timber


/**
 * TODO: Sometimes, Add contact button is not shown -> Caused by tapping bottom again
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

        Timber.e("$DBL: ContactsFragment - onViewCreated() - $this")

        setupAppBar()
        showAppBar()

        val recyclerView = binding.contactsRecyclerView

        val adapter = ContactsAdapter(requireContext())
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
        revertAppbar()
    }

    private fun setupAppBar() {
        if (activity is MainActivity) {
            Timber.i("$DBL: ContactsFragment - setupAppBar()!")

            val act = activity as MainActivity
            act.setTitle(getString(R.string.contacts_title))
            act.displayAppBarTextButton(show = true, text = "Add")
            act.setEditOrAddOnClickListener {
                val action = ContactsFragmentDirections.actionContactsFragmentToChangeContactFragment()
                findNavController().navigate(action)
            }
        }
    }

    private fun showAppBar() {
        if (activity is MainActivity) {
            (activity as MainActivity).displayAppBar(true)
        }
    }

    private fun revertAppbar() {
        if (activity is MainActivity) {
            (activity as MainActivity).revertAppbar()
        }
    }
}