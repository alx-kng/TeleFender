package com.telefender.phone.gui.model

import android.annotation.SuppressLint
import android.app.Application
import android.provider.ContactsContract
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
 * TODO: Context may be part of memory leak!!! -> maybe, maybe not
 *
 * TODO: Contact sorting algorithm has some issues.
 *
 * TODO: Probably / Maybe refactor ContactsViewModel to use repository to actually query the data
 *  as the "good" app architecture suggests the repository should be a single source of truth.
 *
 * TODO: LOOK INTO PAGING FOR RECENTS AND CONTACTS
 *
 * TODO: Leftover gray divider bar when there are no contacts. -> Need to get rid of decoration
 *  if that's the case.
 */
class ContactsViewModel(app: Application) : AndroidViewModel(app) {

    @SuppressLint("StaticFieldLeak")
    private val context = getApplication<Application>().applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)

    /**********************************************************************************************
     * For ContactsFragment
     **********************************************************************************************/

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

    /**
     * Given the list of aggregate contacts, [formatForDividedUI] automatically adds [BaseDivider] items
     * and a [BaseFooter] (for UI), sorts the list, and assigns it to [_dividedContacts] to be used
     * for the UI.
     */
    private suspend fun formatForDividedUI(contacts: List<AggregateContact>) {
        val tempDivided =  mutableListOf<BaseContactItem>()

        withContext(Dispatchers.Default) {
            val existingDividers = mutableListOf<BaseDivider>()
            var currChar: Char? = null

            for (contact in contacts) {
                /*
                First non-whitespace character adjusted to fit our divider grouping pattern. Also,
                technically guaranteed to exist since even if there's no name, some other piece of
                data is used.
                 */
                val adjustedFirstChar = getAdjustedFirstChar(contact.name)

                /*
                The only time a new divider is added if the first letter is different from before.
                Although, that's only a necessary condition (for efficiency). We still need to make
                sure that there isn't an existing divider (e.g., these edge cases come from the way
                lowercase and uppercase names are sorted).
                 */
                if (adjustedFirstChar != currChar) {
                    currChar = adjustedFirstChar

                    val existingDivider = existingDividers.find {
                        it.adjustedFirstChar == adjustedFirstChar
                    }

                    // Only add new divider if no existing divider.
                    if (existingDivider == null) {
                        val newDivider = BaseDivider(adjustedFirstChar)
                        tempDivided.add(newDivider)
                        existingDividers.add(newDivider)
                    }
                }

                tempDivided.add(contact)
            }
        }

        // Sorted with the dividers.
        tempDivided.sortWith(BaseContactItemComparator)

        // Removes the first divider since the sticky header overlay always has the top header.
        tempDivided.removeFirst()

        // Always add footer to end of list.
        tempDivided.add(BaseFooter)

        _dividedContacts = tempDivided
    }

    fun updateContacts() {
        viewModelScope.launch(Dispatchers.Default) {
            val tempContacts = DefaultContacts.getAggregateContacts(context)
            formatForDividedUI(tempContacts)

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
                    ContactData.createNullPairContactData(
                        mimeType = ContactDataMimeType.NAME,
                        columnInfo = Pair(1, ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)
                    ),
                    ContactData.createNullPairContactData(
                        mimeType = ContactDataMimeType.NAME,
                        columnInfo = Pair(2, ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)
                    ),
                    SectionHeader(ContactDataMimeType.PHONE),
                    ItemAdder(ContactDataMimeType.PHONE),
                    SectionHeader(ContactDataMimeType.EMAIL),
                    ItemAdder(ContactDataMimeType.EMAIL),
                    SectionHeader(ContactDataMimeType.ADDRESS),
                    ItemAdder(ContactDataMimeType.ADDRESS),
                    ChangeFooter()
                )

                originalUpdatedDataList = mutableListOf()
                originalDataList = mutableListOf()

                /*
                We don't use the set function here, since setNonContactDataList will already notify
                the adapter after setting this. Prevents double blinking update.
                 */
                mutexUpdated.withLock {
                    _updatedDataList = initialUIList.toMutableList()
                }

                // _nonContactDataList should not contain the initial null-pair ContactData.
                initialUIList.removeAt(1)
                initialUIList.removeAt(1)
                setNonContactDataList(initialUIList)

                return@launch
            }

            val packagedDataLists = DefaultContacts.getContactData(
                contentResolver = context.contentResolver,
                contactID = selectCID
            )

            originalUpdatedDataList = packagedDataLists.originalUpdatedDataList
            originalDataList = packagedDataLists.originalDataList

            /*
            We don't use the set function here, since setNonContactDataList will already notify
            the adapter after setting this. Prevents double blinking update.
             */
            mutexUpdated.withLock {
                _updatedDataList = packagedDataLists.updatedDataList
            }

            setNonContactDataList(packagedDataLists.nonContactDataList)
        }
    }

    /**
     * TODO: Finish / double check
     *
     * Submits the changes in the UI to the default database.
     */
    fun submitChanges() {
        val dataListsSnapshot = PackagedDataLists(
            originalUpdatedDataList = originalUpdatedDataList,
            updatedDataList = _updatedDataList,
            originalDataList = originalDataList,
            nonContactDataList = _nonContactDataList
        )

        scope.launch {
            /*
            1. Filter updatedDataList to only have ContactData and get rid of ContactData with an
             empty string value unless it's a Name ContactData (special handling due first and last
             name).
            2. Clean converted updatedDataList
            3. Compare with originalUpdatedDataList to see if they are different.
            4. If different, then call [executeChanges] in DefaultContacts.
             */
            val pureContactData = dataListsSnapshot.updatedDataList
                .filterIsInstance<ContactData>()
                .filter { it.value.trim() != "" || it.mimeType == ContactDataMimeType.NAME }
                .toMutableList()

            Timber.i("$DBL: originalUpdatedDataList = ${dataListsSnapshot.originalUpdatedDataList}")
            Timber.i("$DBL: pureUpdatedDataList = $pureContactData")

            if (originalUpdatedDataList == pureContactData) {
                return@launch
            }


            val cleanUpdatedDataList = DefaultContacts.cleanUpdatedDataList(
                uncleanList = pureContactData
            )

            Timber.i("$DBL: cleanUpdatedDataList = $cleanUpdatedDataList")

            DefaultContacts.executeChanges(
                context = context,
                contentResolver = context.contentResolver,
                originalCID = selectCID,
                originalDataList = dataListsSnapshot.originalDataList,
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

    fun clearDataLists() {
        originalDataList = mutableListOf()
        originalUpdatedDataList = mutableListOf()
        _updatedDataList = mutableListOf()
        _nonContactDataList = listOf()
    }

    /**
     * TODO: Should we make these more generic extension functions? That is, add / remove to list
     *  by object reference?
     */
    suspend fun setUpdatedDataList(newUpdatedDataList: MutableList<ChangeContactItem>) {
        mutexUpdated.withLock {
            _updatedDataList = newUpdatedDataList
            updatedIndicatorLiveData.postValue(UUID.randomUUID())
        }
    }

    suspend fun setNonContactDataList(newNonContactDataList: List<ChangeContactItem>) {
        _nonContactDataList = newNonContactDataList
        updatedIndicatorLiveData.postValue(UUID.randomUUID())
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
