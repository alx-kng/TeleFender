package com.telefender.phone.gui.model

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.*
import com.telefender.phone.data.default_database.*
import com.telefender.phone.gui.adapters.recycler_view_items.*
import com.telefender.phone.misc_helpers.DBL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.text.Typography.section


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
    private val scope = CoroutineScope(Dispatchers.IO)

    private var _contacts = MutableLiveData<List<AggregateContact>>()
    val contacts : LiveData<List<AggregateContact>> = _contacts

    private var _dividedContacts = mutableListOf<BaseContactItem>()
    val dividedContacts : List<BaseContactItem>
        get() = _dividedContacts

    /**
     * Selected aggregate CID for the ChangeContact screen. Info retrieved from selected contact or
     * Add button.
     */
    private var _selectCID: String? = null
    val selectCID : String?
        get() = _selectCID

    private var _originalUpdatedDataList = mutableListOf<ContactData>()
    val originalUpdatedDataList = _originalUpdatedDataList

    private var _updatedDataList = mutableListOf<ChangeContactItem>()
    val updatedDataList = _updatedDataList

    private var _originalDataList = mutableListOf<ContactData>()
    val originalDataList = _originalDataList

    private var _nonContactDataList = listOf<ChangeContactItem>()
    val nonContactDataList = _nonContactDataList

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
        viewModelScope.launch(Dispatchers.Default) {
            val tempContacts = DefaultContacts.getAggregateContacts(context)
            addDividers(tempContacts)

            Timber.i("$DBL: ABOUT TO ASSIGN CONTACTS VALUE")
            _contacts.value = tempContacts
        }
    }

    /**
     * Called when starting the ChangeContactFragment. Given the selected aggregate CID [selectCID],
     * this loads the corresponding data lists necessary for the UI.
     */
    fun setDataLists(selectCID: String?) {
        _selectCID = selectCID

        if (selectCID == null) {
            val initialUIList = mutableListOf(
                SectionHeader(ContactDataMimeType.NAME),
                BlankEdit(ContactDataMimeType.NAME),
                SectionHeader(ContactDataMimeType.PHONE),
                BlankEdit(ContactDataMimeType.PHONE),
                SectionHeader(ContactDataMimeType.EMAIL),
                BlankEdit(ContactDataMimeType.EMAIL),
                SectionHeader(ContactDataMimeType.ADDRESS),
                BlankEdit(ContactDataMimeType.ADDRESS),
            )

            _originalUpdatedDataList = mutableListOf()
            _updatedDataList = initialUIList
            _originalDataList = mutableListOf()
            _nonContactDataList = initialUIList
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val packagedDataLists = DefaultContacts.getContactData(context.contentResolver, selectCID)

            _originalUpdatedDataList = packagedDataLists.originalUpdatedDataList
            _updatedDataList = packagedDataLists.updatedDataList
            _originalDataList = packagedDataLists.originalDataList
            _nonContactDataList = packagedDataLists.nonContactDataList
        }
    }

    /**
     * TODO: Finish
     *
     * Submits the changes in the UI to the default database.
     */
    fun submitChanges() {
        scope.launch {
            /*
            1. Convert updatedDataList to be all ContactData by removing SectionHeaders and
             converting the non-blank BlankEdits to ContactData (with null pairID).
            2. Clean converted updatedDataList
            3. Compare with originalUpdatedDataList to see if they are different.
            4. If different, then call [executeChanges] in DefaultContacts.
             */
            val cleanUpdatedDataList = listOf<ContactData>()
            DefaultContacts.executeChanges(
                context = context,
                contentResolver = context.contentResolver,
                originalCID = selectCID,
                originalDataList = originalDataList,
                updatedDataList = cleanUpdatedDataList,
                accountName = null,
                accountType = null,
            )
        }
    }
}

class ContactsViewModelFactory(
    private val app: Application,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactsViewModel::class.java)) {
            return ContactsViewModel(app) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
