package com.telefender.phone.gui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.telefender.phone.data.default_database.DefaultContacts
import com.telefender.phone.databinding.FragmentChangeContactOldBinding
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.model.*


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

    private var _binding: FragmentChangeContactOldBinding? = null
    private val binding get() = _binding!!
    private val contactsViewModel: ContactsViewModel by activityViewModels {
        ChangeContactViewModelFactory(requireActivity().application)
    }

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
        _binding = FragmentChangeContactOldBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * TODO: Fill in values with existing contact if there is one.
     *
     * TODO: Cancel and Done buttons shoudld be moved to App Bar or something,
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAppBar()
        hideBottomNavigation()

        updatedDoneEnabled()

        binding.changeContactNameEdit.addTextChangedListener(doneTextWatcher)
        binding.changeContactNumberEdit.addTextChangedListener(doneTextWatcher)
        binding.changeContactEmailEdit.addTextChangedListener(doneTextWatcher)
        binding.changeContactAddressEdit.addTextChangedListener(doneTextWatcher)
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
        if (activity is MainActivity) {
            val act = activity as MainActivity
            act.setEnabledAppBarTextButton(enabled2 = validContactEntries())
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

            // Cleans old app bar before setting up new app bar
            act.revertAppBar()

            // New app bar stuff
            act.displayMoreMenu(show = false)

            act.displayAppBarTextButton(
                show1 = true,
                show2 = true,
                text1 = "Cancel",
                text2 = "Done"
            )

            act.setAppBarTextButtonOnClickListener(
                onClickListener1 = {
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                },
                onClickListener2 = {
                    submitContact()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            )

            act.setEnabledAppBarTextButton(enabled2 = false)

            // Actually show app bar
            act.displayAppBar(true)
        }
    }

    private fun hideBottomNavigation() {
        if (activity is MainActivity) {
            val act = (activity as MainActivity)
            act.displayBottomNavigation(false)
        }
    }
}