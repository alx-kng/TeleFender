package com.telefender.phone.gui.model

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.telefender.phone.data.default_database.*
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//TODO: Probably / Maybe refactor ContactsViewModel to use repository to actually query the data,
// as the "good" app architecture suggests the repository should be a single source of truth.

// TODO: LOOK INTO PAGING FOR RECENTS AND CONTACTS
class ContactsViewModel(app: Application) : AndroidViewModel(app) {

    @SuppressLint("StaticFieldLeak")
    private val context = getApplication<Application>().applicationContext

    private var _contacts = MutableLiveData<List<ContactDetail>>()
    val contacts : LiveData<List<ContactDetail>> = _contacts

    private var _dividedContacts : MutableList<ContactItem> = mutableListOf()
    val dividedContacts : List<ContactItem>
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

    private suspend fun addDividers(contacts: List<ContactDetail>) {
        Log.i("${MiscHelpers.DEBUG_LOG_TAG}", "ADD DIVIDER START")
        val tempDividers =  mutableListOf<ContactItem>()

        withContext(Dispatchers.Default) {
            Log.i("${MiscHelpers.DEBUG_LOG_TAG}", "ADD DIVIDER MIDDLE")

            val miscContacts = mutableListOf<ContactItem>()
            var _currLetter: Char? = null

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
                if (_currLetter == null || firstLetter != _currLetter) {
                    _currLetter = firstLetter
                    tempDividers.add(Divider(_currLetter.toString()))
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

        Log.i("${MiscHelpers.DEBUG_LOG_TAG}", "ADD DIVIDER END")
        _dividedContacts = tempDividers
    }

    fun updateContacts() {
        viewModelScope.launch {
            val tempContacts = DefaultContacts.getContactDetails(context)
            addDividers(tempContacts)

            Log.i("${MiscHelpers.DEBUG_LOG_TAG}", "ABOUT TO ASSIGN CONTACTS VALUE")
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
