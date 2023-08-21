package com.telefender.phone.gui.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.telefender.phone.R
import com.telefender.phone.databinding.FragmentChangeContactBinding
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.adapters.ChangeContactAdapter
import com.telefender.phone.gui.adapters.recycler_view_items.ChangeContactItem
import com.telefender.phone.gui.adapters.recycler_view_items.ContactData
import com.telefender.phone.gui.adapters.recycler_view_items.ChangeContactAdder
import com.telefender.phone.gui.model.ContactsViewModel
import com.telefender.phone.gui.model.ContactsViewModelFactory
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.diffStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import timber.log.Timber


/**
 * TODO: Show a pop up window to get user confirmation when the delete contact button is pressed.
 *
 * TODO: Need a contact detail screen (analogous to CallHistoryFragment)
 *
 * Represents the screen for both adding a new contact and editing an existing contact.
 */
class ChangeContactFragment : Fragment() {

    private var _binding: FragmentChangeContactBinding? = null
    private val binding get() = _binding!!
    private val contactsViewModel: ContactsViewModel by activityViewModels {
        ContactsViewModelFactory(requireActivity().application)
    }

    private var recyclerView: RecyclerView? = null
    private var adapter: ChangeContactAdapter? = null
    private val adapterScope = CoroutineScope(Dispatchers.Main)

    private val maxAdapterDelay = 2000L

    /**
     * Observer used to change the TextInputEditText focus to a ContactData that was newly added by
     * an ChangeContactAdder.
     */
    private val adapterObserver = object : RecyclerView.AdapterDataObserver() {

        // This method is called when new items are inserted.
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            super.onItemRangeInserted(positionStart, itemCount)

            /*
            By this point, the new adapter list, should have the newly inserted item at the
            inserted position (which is positionStart). However, just because the currentList has
            the newItem, it DOES NOT meant that the corresponding ViewHolder has been created yet.
             */
            val currentItem = adapter?.currentList?.get(positionStart) ?: return

            adapterScope.launch {
                /*
                Wait for ViewHolder to be added, but as a safeguard, make sure that this won't wait
                here forever.
                 */
                var totalDelay = 0
                while(recyclerView?.findViewHolderForItemId(currentItem.longUUID) == null
                    && totalDelay < maxAdapterDelay
                ) {
                    delay(50)
                    totalDelay += 50
                }

                val viewHolder = recyclerView?.findViewHolderForItemId(currentItem.longUUID)
                Timber.e("$DBL: item = ${adapter?.currentList?.get(positionStart)}, viewHolder = $viewHolder")
                if (viewHolder is ChangeContactAdapter.EditViewHolder) {
                    /*
                    Focuses the blinking text bar onto the editText. Click is necessary to set the
                    current focus variable in the adapter.
                     */
                    viewHolder.editText.callOnClick()
                    viewHolder.editText.requestFocus()

                    val inputMethodManager = this@ChangeContactFragment
                        .requireContext()
                        .applicationContext
                        .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

                    // Shows input keyboard.
                    inputMethodManager.showSoftInput(
                        viewHolder.editText,
                        InputMethodManager.SHOW_IMPLICIT
                    )
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentChangeContactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAppBar()
        hideBottomNavigation()

        recyclerView = binding.recyclerView
        adapter = ChangeContactAdapter(
            applicationContext = requireContext().applicationContext,
            lastNonContactDataList = contactsViewModel.nonContactDataList,
            adderClickListener = { item ->
                Timber.e("$DBL: Adder onClick!")

                when (item) {
                    is ChangeContactAdder -> contactsViewModel.viewModelScope.launch {
                        contactsViewModel.addToUpdatedDataList(
                            newContactItem = ContactData.createNullPairContactData(item.mimeType),
                            beforeItem = item,
                        )
                    }
                    else -> {}
                }
            },
            onTextChangedLambda = ::onTextChangedFunction,
            removeClickListener = { item ->
                Timber.e("$DBL: Remove item onClick!")

                when (item) {
                    is ContactData -> contactsViewModel.viewModelScope.launch {
                        contactsViewModel.removeFromUpdatedDataList(contactItem = item)
                    }
                    else -> {}
                }
            },
            deleterClickListener = {
                Timber.e("$DBL: Delete contact onClick!")

                contactsViewModel.deleteContact()
                findNavController().popBackStack(R.id.contactsFragment, false)
                contactsViewModel.clearDataLists()
            }
        )

        adapter?.setHasStableIds(true)
        recyclerView?.adapter = adapter

        adapter?.submitList(contactsViewModel.updatedDataList)
        contactsViewModel.updatedIndicatorLiveData.observe(viewLifecycleOwner) {
            Timber.e("$DBL - Current | Size = ${adapter?.currentList?.size}, List = ${adapter?.currentList}")
            Timber.e("$DBL - Updated | Size = ${contactsViewModel.updatedDataList.size}, List = ${contactsViewModel.updatedDataList}")
            /*
            Must submit new instance of list (with the same ChangeContactItem elements), otherwise
            the adapter can't tell when there is an update in the list. Creating a new list instance
            like this shouldn't be a big deal performance wise, as the updatedDataList is usually
            very small.
             */
            adapter?.lastNonContactDataList = contactsViewModel.nonContactDataList
            adapter?.submitList(contactsViewModel.updatedDataList.toList())

            /*
            Update the Done button in case the updatedDataList is non-empty (i.e., editing an
            existing contact).
             */
            updatedDoneEnabled()
        }

        adapter?.registerAdapterDataObserver(adapterObserver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter?.unregisterAdapterDataObserver(adapterObserver)
        _binding = null
    }

    /**
     * TODO: Maybe there is a cleaner way to do the string diff.
     *
     * Called when the UI text is changed in the EditViewHolder. Necessary not only to update the
     * actual ContactData, but also to prevent non-focused items on the UI from being edited.
     */
    private fun onTextChangedFunction(
        currentItem: ChangeContactItem,
        newValue: String
    ) {
        val focusedItemID = adapter?.currentFocusedItemID

        val focusedPosition = adapter?.currentList
            ?.indexOfFirst { it.longUUID == focusedItemID }

        val focusedItem = adapter?.currentList
            ?.find { it.longUUID == focusedItemID }
            as ContactData?

        Timber.i("$DBL: EditViewHolder onTextChanged! newValue = $newValue, " +
            "currentItem = ${currentItem.longUUID}, focusedItem = ${focusedItem?.longUUID}")

        when (currentItem) {
            is ContactData -> {
                if (currentItem == focusedItem) {
                    currentItem.value = newValue
                } else {
                    // Requires non-null focusedItem.
                    val focusedInputType = focusedItem?.let {
                        ChangeContactAdapter.getInputType(it.mimeType)
                    } ?: return

                    val stringDiffs = diffStrings(newValue, currentItem.value)
                    for (diff in stringDiffs) {
                        Timber.e("$DBL: StringDiff = $diff | newValue = $newValue, oldValue = ${currentItem.value}")

                        if (diff.first == null) {
                            val newString = focusedItem.value.dropLast(1)
                            newString.let {
                                focusedItem.value = newString
                            }

                            continue
                        }

                        /*
                        Prevents text with the wrong input type from being added to the focused item
                        (e.g., no text in phone numbers).
                         */
                        val validInputType = ChangeContactAdapter.isValidForInputType(
                            inputType = focusedInputType,
                            text = diff.first.toString()
                        )

                        if (diff.second == null && validInputType) {
                            val newString = focusedItem.value.plus(diff.first)
                            newString.let {
                                focusedItem.value = newString
                            }
                        }
                    }

                    Timber.e("$DBL: focusedPosition = $focusedPosition, itemCount = ${adapter?.itemCount}")
                    if (focusedPosition != null
                        && focusedPosition in 0 until (adapter?.itemCount ?: 0)
                    ) {
                        /*
                        We need to post the scrolling action, which delays the execution just long
                        enough to let any pending operations finish (e.g., setting up the previous
                        ViewHolders. This prevents the need for two onTextChanged events in order
                         to scroll.
                         */
                        recyclerView?.post {
                            recyclerView?.smoothScrollToPosition(focusedPosition)

                            /*
                            Only edit text of ViewHolder if it's still visible after the scroll.
                            Scroll listener waits for the scroll to complete before doing anything.
                            This is here because if the scroll doesn't complete before modifying the
                            ViewHolder, sometimes the scroll just completely fails.
                             */
                            recyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                        // Scroll has basically completed.
                                        val viewHolder = recyclerView.findViewHolderForItemId(currentItem.longUUID)
                                        if (viewHolder is ChangeContactAdapter.EditViewHolder) {
                                            val textWatcher = adapter?.textWatcherMap?.get(currentItem.longUUID)

                                            /*
                                            Set non-focused item to original value without notifying
                                            the TextChanged listeners.
                                             */
                                            viewHolder.editText.removeTextChangedListener(textWatcher)
                                            viewHolder.editText.setText(currentItem.value)
                                            viewHolder.editText.addTextChangedListener(textWatcher)
                                        }

                                        // Removes this scroll listener to prevent it from being called again.
                                        recyclerView.removeOnScrollListener(this)
                                    }
                                }
                            })
                        }
                    }
                }

                // Update Done button in the app bar.
                updatedDoneEnabled()
            }
            else -> {}
        }
    }

    private fun updatedDoneEnabled() {
        if (activity is MainActivity) {
            val act = activity as MainActivity
            act.setEnabledAppBarTextButton(enabled2 = contactsViewModel.validEntries())
        }
    }

    private fun setupAppBar() {
        if (activity is MainActivity) {
            val act = activity as MainActivity
            val navController = findNavController()

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
                    Timber.e("$DBL: Submit contact changes!")

                    contactsViewModel.submitChanges()

                    /*
                    When submitting a Contact change, we want to bring the user to the ViewContact
                    screen or CallHistory screen (while maintaining a clean / predictable backstack).
                     */
                    val previousDestination = navController.previousBackStackEntry?.destination?.id
                    if (previousDestination == R.id.viewContactFragment
                        || previousDestination == R.id.callHistoryFragment
                    ) {
                        /*
                        If the ChangeContactFragment was navigated to from the ViewContactFragment,
                        or CallHistoryFragment, then just do a back press to get back to the
                        corresponding Fragment.
                         */
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    } else {
                        /*
                        If the ChangeContactFragment wasn't navigated to from the
                        ViewContactFragment, then navigate to the ViewContactFragment while making
                        sure to remove the ChangeContactFragment from the backstack (e.g., we don't
                        want back button to take user back to edit screen). Moreover,
                         */
                        val action = ChangeContactFragmentDirections.actionChangeContactFragmentToViewContactFragment()
                        navController.navigate(action)
                    }
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