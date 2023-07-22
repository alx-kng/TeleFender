package com.telefender.phone.gui.model

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.*
import com.telefender.phone.data.default_database.*
import com.telefender.phone.gui.adapters.recycler_view_items.AggregateContact
import com.telefender.phone.gui.adapters.recycler_view_items.ContactFooter
import com.telefender.phone.gui.adapters.recycler_view_items.BaseContactItem
import com.telefender.phone.gui.adapters.recycler_view_items.Divider
import com.telefender.phone.misc_helpers.DBL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


/**
 * TODO: Contact sorting algorithm has some issues.
 *
 * TODO: Probably / Maybe refactor ContactsViewModel to use repository to actually query the data
 *  as the "good" app architecture suggests the repository should be a single source of truth.
 *
 * TODO: LOOK INTO PAGING FOR RECENTS AND CONTACTS
 *
 * TODO: Leftover gray divider bar when there are no contacts.
 */
class ContactsViewModel(app: Application) : AndroidViewModel(app) {

    // TODO: THIS MIGHT BE CAUSE OF A MEMORY LEAK!!! -> maybe, maybe not
    @SuppressLint("StaticFieldLeak")
    private val context = getApplication<Application>().applicationContext

    private var _contacts = MutableLiveData<List<AggregateContact>>()
    val contacts : LiveData<List<AggregateContact>> = _contacts

    private var _dividedContacts : MutableList<BaseContactItem> = mutableListOf()
    val dividedContacts : List<BaseContactItem>
        get() = _dividedContacts

    /**
     * Preloads contacts when ViewModel is first created.
     */
    init {
        updateContacts()
    }

    /**
     * Dummy method to initialize ViewModel.
     */
    fun activateDummy() {}

    private suspend fun addDividers(contacts: List<AggregateContact>) {
        val tempDividers =  mutableListOf<BaseContactItem>()

        withContext(Dispatchers.Default) {
            val miscContacts = mutableListOf<BaseContactItem>()
            var currLetter: Char? = null

            for (contact in contacts) {
                // First non-whitespace character. Guaranteed to exist.
                val firstNonWhiteSpace = contact.name.find { !it.isWhitespace() }
                val firstLetter = firstNonWhiteSpace!!.uppercaseChar()

                // If first letter isn't A through Z or #, then add to misc contacts.
                if (firstLetter !in 'A'..'Z' && firstLetter != '#') {
                    miscContacts.add(contact)
                    continue
                }

                // New divider is added if the first letter is different from before.
                if (currLetter == null || firstLetter != currLetter) {
                    currLetter = firstLetter
                    tempDividers.add(Divider(currLetter.toString()))
                }

                tempDividers.add(contact)
            }

            // Only adds in misc divider if there are misc contacts.
            if (miscContacts.isNotEmpty()) {
                tempDividers.add(Divider("Misc"))
                miscContacts.forEach { tempDividers.add(it) }
            }

            // Always adds footer to end of list.
            tempDividers.add(ContactFooter)

            // Removes the first divider since the sticky header overlay always has the top header.
            tempDividers.removeFirst()
        }

        _dividedContacts = tempDividers
    }

    fun updateContacts() {
        viewModelScope.launch {
            val tempContacts = DefaultContacts.getAggregateContacts(context)
            addDividers(tempContacts)

            Timber.i("$DBL: ABOUT TO ASSIGN CONTACTS VALUE")
            _contacts.value = tempContacts
        }
    }
}

class ContactsViewModelFactory(
    private val app: Application)
    : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactsViewModel::class.java)) {
            return ContactsViewModel(app) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
