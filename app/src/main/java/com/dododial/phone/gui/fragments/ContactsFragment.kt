package com.dododial.phone.gui.fragments

import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.dododial.phone.R
import com.dododial.phone.databinding.FragmentContactsBinding
import com.dododial.phone.gui.MainActivity
import com.dododial.phone.gui.decoration.ContactHeaderDecoration
import com.dododial.phone.gui.adapters.ContactsAdapter
import com.dododial.phone.gui.model.ContactsViewModel
import com.dododial.phone.gui.model.ContactsViewModelFactory

// TODO: Consider not showing contacts that don't have a number associated with them.
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

        setAppBarTitle(getString(R.string.contacts))

        val context = context!!
        val recyclerView = binding.contactsRecyclerView

        val adapter = ContactsAdapter(context)
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

        // TODO: Handle case where permissions aren't given (or default dialer isn't granted).
        requireActivity().applicationContext.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            ContactsObserver(Handler(Looper.getMainLooper()), contactsViewModel)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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

    class ContactsObserver(
        handler: Handler,
        private val contactsViewModel: ContactsViewModel
    ) : ContentObserver(handler) {

        override fun deliverSelfNotifications(): Boolean {
            return true
        }

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            Log.i("DODODEBUG", "NEW CONTACT")

            contactsViewModel.updateContacts()
        }
    }
}