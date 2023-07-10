package com.telefender.phone.gui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.telefender.phone.R
import com.telefender.phone.data.default_database.DefaultContacts
import com.telefender.phone.databinding.FragmentChangeContactBinding
import com.telefender.phone.gui.MainActivity


/**
 * TODO: NEW CONTACT ISN'T ALWAYS ADDED ALPHABETICALLY IN CONTACTS VIEW
 *
 * TODO: NEEDS TO BE RECYCLER VIEW IN THE LONG RUN!
 *
 * TODO: Need a contact detail screen (analogous to CallHistoryFragment)
 *
 * TODO: ALLOW TAP OUTSIDE KEYPAD TO STOP TEXT EDIT!
 *
 * Represents the screen for both adding a new contact and editing an existing contact.
 */
class ChangeContactFragment : Fragment() {

    private var _binding: FragmentChangeContactBinding? = null
    private val binding get() = _binding!!

    /**
     * Makes sure you can't submit an empty Contact.
     */
    private val doneTextWatcher = object : TextWatcher {

        // No use currently
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            return
        }

        // No use currently
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            return
        }

        override fun afterTextChanged(s: Editable?) {
            updatedDoneEnabled()
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentChangeContactBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * TODO: Fill in values with existing contact if there is one.
     *
     * TODO: Cancel and Done buttons should be moved to App Bar or something,
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAppBar()
        showAppBar()

        updatedDoneEnabled()

        binding.changeContactNameEdit.addTextChangedListener(doneTextWatcher)
        binding.changeContactNumberEdit.addTextChangedListener(doneTextWatcher)
        binding.changeContactEmailEdit.addTextChangedListener(doneTextWatcher)
        binding.changeContactAddressEdit.addTextChangedListener(doneTextWatcher)

        binding.changeContactDone.setOnClickListener {
            submitContact()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.changeContactCancel.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun submitContact() {
        DefaultContacts.insertContact(
            contentResolver = requireContext().contentResolver,
            name = binding.changeContactNameEdit.text.toString(),
            number = binding.changeContactNumberEdit.text.toString(),
            email = binding.changeContactEmailEdit.text.toString(),
            address = binding.changeContactAddressEdit.text.toString()
        )
    }

    private fun updatedDoneEnabled() {
        if (validContactEntries()) {
            binding.changeContactDone.isClickable = true
            binding.changeContactDone.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.teal_700)
            )
        } else {
            binding.changeContactDone.isClickable = false
            binding.changeContactDone.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.disabled_grey)
            )
        }
    }

    private fun validContactEntries() : Boolean {
        return binding.changeContactNameEdit.text.toString() != ""
            || binding.changeContactNumberEdit.text.toString() != ""
            || binding.changeContactEmailEdit.text.toString() != ""
            || binding.changeContactAddressEdit.text.toString() != ""
    }

    private fun setupAppBar() {
        if (activity is MainActivity) {
            val act = activity as MainActivity
            act.setTitle(getString(R.string.add_contact_title))
            act.displayAppBarTextButton(show = false, text = "")
            act.setEditOrAddOnClickListener {  }
        }
    }

    private fun showAppBar() {
        if (activity is MainActivity) {
            (activity as MainActivity).displayAppBar(true)
        }
    }
}