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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*


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

    /**********************************************************************************************
     * For ContactsFragment
     **********************************************************************************************/

    // TODO: Should this be get()?

    private var _contacts = MutableLiveData<List<AggregateContact>>()
    val contacts : LiveData<List<AggregateContact>> = _contacts

    private var _dividedContacts = mutableListOf<BaseContactItem>()
    val dividedContacts : List<BaseContactItem>
        get() = _dividedContacts

    /**********************************************************************************************
     * TODO: See if there is a better solution for observing updated data list than using our
     *  indicator live data.
     *
     * For ChangeContactFragment
     **********************************************************************************************/

    /**
     * Selected aggregate CID for the ChangeContact screen. Info retrieved from selected contact or
     * Add button.
     */
    private var _selectCID: String? = null
    val selectCID : String?
        get() = _selectCID

    private var originalUpdatedDataList = mutableListOf<ContactData>()

    val updatedIndicatorLiveData = MutableLiveData<UUID>()

    private var _updatedDataList = mutableListOf<ChangeContactItem>()
    val updatedDataList : List<ChangeContactItem>
        get() = _updatedDataList

    private var originalDataList = mutableListOf<ContactData>()

    private var _nonContactDataList = listOf<ChangeContactItem>()
    val nonContactDataList : List<ChangeContactItem>
        get() = _nonContactDataList

    private val mutexUpdated = Mutex()

    /**********************************************************************************************
     * For ContactsFragment
     **********************************************************************************************/

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
            _contacts.postValue(tempContacts)
        }
    }

    /**********************************************************************************************
     * For ChangeContactFragment
     **********************************************************************************************/

    /**
     * Called when starting the ChangeContactFragment. Given the selected aggregate CID [selectCID],
     * this loads the corresponding data lists necessary for the UI.
     */
    fun setDataLists(selectCID: String?) {
        _selectCID = selectCID

        viewModelScope.launch(Dispatchers.Default) {
            if (selectCID == null) {
                val initialUIList = mutableListOf(
                    SectionHeader(ContactDataMimeType.NAME),
                    ContactData.createNullPairContactData(ContactDataMimeType.NAME),
                    SectionHeader(ContactDataMimeType.PHONE),
                    ItemAdder(ContactDataMimeType.PHONE),
                    SectionHeader(ContactDataMimeType.EMAIL),
                    ItemAdder(ContactDataMimeType.EMAIL),
                    SectionHeader(ContactDataMimeType.ADDRESS),
                    ItemAdder(ContactDataMimeType.ADDRESS),
                    ChangeFooter()
                )

                originalUpdatedDataList = mutableListOf()
                setUpdatedDataList(initialUIList.toMutableList())
                originalDataList = mutableListOf()

                // _nonContactDataList should not contain the initial null-pair ContactData.
                initialUIList.removeAt(1)
                _nonContactDataList = initialUIList

                return@launch
            }

            val packagedDataLists = DefaultContacts.getContactData(context.contentResolver, selectCID)

            originalUpdatedDataList = packagedDataLists.originalUpdatedDataList
            setUpdatedDataList(packagedDataLists.updatedDataList)
            originalDataList = packagedDataLists.originalDataList
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
            1. Filter updatedDataList to only have ContactData and get rid of ContactData with an
             empty string value.
            2. Clean converted updatedDataList
            3. Compare with originalUpdatedDataList to see if they are different.
            4. If different, then call [executeChanges] in DefaultContacts.
             */
            val pureContactData = updatedDataList
                .filterIsInstance<ContactData>()
                .filter { it.value.trim() != "" }
                .toMutableList()

            val cleanUpdatedDataList = DefaultContacts.cleanUpdatedDataList(
                uncleanList = pureContactData
            )

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

    /**
     * ChangeContactFragment entries are valid if there is at least one non-empty string value.
     */
    fun validEntries() : Boolean {
        for (item in updatedDataList) {
            when (item) {
                is ContactData -> if (item.value.trim() != "") return true
                else -> continue
            }
        }

        return false
    }

    /**
     * TODO: Should we make these more generic extension functions? That is, add / remove to list
     *  by object reference?
     */
    suspend fun setUpdatedDataList(newUpdatedDataList: MutableList<ChangeContactItem>) {
        mutexUpdated.withLock {
            Timber.e("$DBL: ${this.updatedDataList}")
            _updatedDataList = newUpdatedDataList
            updatedIndicatorLiveData.postValue(UUID.randomUUID())
        }
    }

    /**
     * Adds by reference and notifies indicator LiveData.
     */
    suspend fun addToUpdatedDataList(
        newContactItem: ChangeContactItem,
        beforeItem: ChangeContactItem? = null,
    ) {
        mutexUpdated.withLock {
            if (beforeItem != null) {
                val index = _updatedDataList.indexOfFirst { it === beforeItem }
                if (index >= 0) {
                    _updatedDataList.add(index, newContactItem)
                    updatedIndicatorLiveData.postValue(UUID.randomUUID())
                }
            } else {
                _updatedDataList.add(newContactItem)
                updatedIndicatorLiveData.postValue(UUID.randomUUID())
            }
        }
    }

    /**
     * Removes by reference and notifies indicator LiveData.
     */
    suspend fun removeFromUpdatedDataList(contactItem: ChangeContactItem) {
        mutexUpdated.withLock {
            val index = _updatedDataList.indexOfFirst { it === contactItem }
            if (index >= 0) {
                _updatedDataList.removeAt(index)
                updatedIndicatorLiveData.postValue(UUID.randomUUID())
            }
        }
    }

    suspend fun replaceInUpdatedDataList(
        oldContactItem: ChangeContactItem,
        newContactItem: ChangeContactItem
    ) {
        mutexUpdated.withLock {
            val index = _updatedDataList.indexOfFirst { it === oldContactItem }
            if (index >= 0) {
                _updatedDataList[index] = newContactItem
                updatedIndicatorLiveData.postValue(UUID.randomUUID())
            }
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
