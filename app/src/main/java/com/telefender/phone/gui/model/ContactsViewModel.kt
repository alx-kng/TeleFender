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
    private val applicationContext = getApplication<Application>().applicationContext
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
     * For ChangeContactFragment and ViewContactFragment
     **********************************************************************************************/

    /**
     * Selected aggregate CID for the ChangeContact screen. Info retrieved from selected contact or
     * Add button.
     */
    private var _selectCID: String? = null
    val selectCID : String?
        get() = _selectCID

    private var originalDataList = mutableListOf<ContactData>()
    private var originalUpdatedDataList = mutableListOf<ContactData>()

    private var _nonContactDataList = listOf<ChangeContactItem>()
    val nonContactDataList : List<ChangeContactItem>
        get() = _nonContactDataList

    private var _updatedDataList = mutableListOf<ChangeContactItem>()
    val updatedDataList : List<ChangeContactItem>
        get() = _updatedDataList

    private var _viewFormattedList = mutableListOf<ViewContactItem>()
    val viewFormattedList: List<ViewContactItem>
        get() = _viewFormattedList

    val updatedIndicatorLiveData = MutableLiveData<UUID>()
    val viewFormattedIndicatorLiveData = MutableLiveData<UUID>()

    private val mutexUpdated = Mutex()
    private val mutexFormatted = Mutex()

    /**********************************************************************************************
     * Callback used for both the ContactsFragment and ViewContactFragment. This is called when we
     * detect a change in the default contacts.
     **********************************************************************************************/

    fun onContactsUpdate() {
        updateContactsList()
        updatePackagedDataLists()
    }

    /**********************************************************************************************
     * For ContactsFragment
     **********************************************************************************************/

    /**
     * Preloads contacts when ViewModel is first created.
     */
    init {
        updateContactsList()
    }

    /**
     * Dummy method to initialize ViewModel.
     */
    fun activateDummy() {}

    private fun updateContactsList() {
        viewModelScope.launch(Dispatchers.Default) {
            val tempContacts = DefaultContacts.getAggregateContacts(applicationContext)
            formatForDividedUI(tempContacts)

            Timber.i("$DBL: ABOUT TO ASSIGN CONTACTS VALUE")
            _contacts.postValue(tempContacts)
        }
    }

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

    /**********************************************************************************************
     * TODO: Maybe allow for ChangeContactFragment to also update according to default contact
     *  changes one day (a little cumbersome). It would be for the case where the user is on the
     *  change contact screen but goes to the default edit contact screen and modifies something
     *  there. Right now, we're not updating the updatedList even if something was changed, as it
     *  would mess up the UI.
     *
     * For ChangeContactFragment and ViewContactFragment
     **********************************************************************************************/

    /**
     * As mentioned earlier in the section comment, for now, we will just update the view formatted
     * data list used by the ViewContactFragment.
     */
    private fun updatePackagedDataLists() {
        Timber.e("$DBL: Default contact change detected!")

        val selectCIDSnapShot = _selectCID

        viewModelScope.launch(Dispatchers.Default) {
            // Don't update the data lists if the selected CID is null.
            if (selectCIDSnapShot != null) {
                val newPackagedDataLists = DefaultContacts.getContactData(
                    contentResolver = applicationContext.contentResolver,
                    contactID = selectCIDSnapShot,
                    context = applicationContext
                )

                setViewFormattedList(newPackagedDataLists.viewFormattedList)
            }
        }
    }

    /**
     * Called when starting the ChangeContactFragment. Given the selected aggregate CID [selectCID],
     * this loads the corresponding data lists necessary for the UI.
     */
    fun setDataLists(
        selectCID: String?,
        startingNumber: String? = null
    ) {
        _selectCID = selectCID

        viewModelScope.launch(Dispatchers.Default) {
            if (selectCID == null) {
                val initialChangeUIList = mutableListOf(
                    ChangeContactHeader(ContactDataMimeType.NAME),
                    ContactData.createNullPairContactData(
                        mimeType = ContactDataMimeType.NAME,
                        columnInfo = Pair(1, ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)
                    ),
                    ContactData.createNullPairContactData(
                        mimeType = ContactDataMimeType.NAME,
                        columnInfo = Pair(2, ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)
                    ),
                    ChangeContactHeader(ContactDataMimeType.PHONE),
                    ChangeContactAdder(ContactDataMimeType.PHONE),
                    ChangeContactHeader(ContactDataMimeType.EMAIL),
                    ChangeContactAdder(ContactDataMimeType.EMAIL),
                    ChangeContactHeader(ContactDataMimeType.ADDRESS),
                    ChangeContactAdder(ContactDataMimeType.ADDRESS),
                    ChangeContactFooter(),
                )

                startingNumber?.let {
                    initialChangeUIList.add(4,
                        ContactData.createNullPairContactData(
                            mimeType = ContactDataMimeType.PHONE,
                            value = startingNumber
                        ),
                    )
                }

                originalUpdatedDataList = mutableListOf()
                originalDataList = mutableListOf()

                /*
                We don't use the set function here, since setNonContactDataList will already notify
                the adapter after setting this. Prevents double blinking update.
                 */
                mutexUpdated.withLock {
                    _updatedDataList = initialChangeUIList.toMutableList()
                }

                // _nonContactDataList should not contain the initial null-pair ContactData.
                initialChangeUIList.removeAt(1)
                initialChangeUIList.removeAt(1)
                setNonContactDataList(initialChangeUIList)

                /*
                If selectCID is null, the ViewContactFragment technically shouldn't even be showing,
                but we set a basic adapter list here just in case it causes a smoother UI transition
                when coming back from the ChangeContactFragment after adding a new Contact.
                 */
                setViewFormattedList(
                    mutableListOf(
                        ViewContactHeader(
                            displayName = null,
                            primaryNumber = null,
                            primaryEmail = null,
                        ),
                        ViewContactBlockedStatus(isBlocked = false),
                        ViewContactFooter()
                    )
                )

                return@launch
            }

            setListsGivenPackagedLists(
                DefaultContacts.getContactData(
                    contentResolver = applicationContext.contentResolver,
                    contactID = selectCID,
                    context = applicationContext
                )
            )
        }
    }

    /**
     * TODO: Double check
     *
     * Submits the changes in the UI to the default database.
     */
    fun submitChanges() {
        val selectCIDSnapshot = _selectCID
        val dataListsSnapshot = PackagedDataLists(
            originalUpdatedDataList = originalUpdatedDataList,
            updatedDataList = _updatedDataList,
            originalDataList = originalDataList,
            nonContactDataList = _nonContactDataList,
            viewFormattedList = _viewFormattedList
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

            if (dataListsSnapshot.originalUpdatedDataList == pureContactData) {
                return@launch
            }

            val cleanUpdatedDataList = DefaultContacts.cleanUpdatedDataList(
                uncleanList = pureContactData
            )

            Timber.i("$DBL: cleanUpdatedDataList = $cleanUpdatedDataList")

            val resultPair = DefaultContacts.executeChanges(
                context = applicationContext,
                contentResolver = applicationContext.contentResolver,
                originalCID = selectCIDSnapshot,
                originalDataList = dataListsSnapshot.originalDataList,
                updatedDataList = cleanUpdatedDataList,
                accountName = null,
                accountType = null,
            )

            val success = resultPair.first
            val newRawCID = resultPair.second

            if (success) {
                if (selectCIDSnapshot != null) {
                    Timber.e("$DBL: HERE HERE HERE HERE HERE")

                    setListsGivenPackagedLists(
                        DefaultContacts.getContactData(
                            contentResolver = applicationContext.contentResolver,
                            contactID = selectCIDSnapshot,
                            context = applicationContext
                        )
                    )
                } else if (newRawCID != null) {
                    val correspondingCID = DefaultContacts.getAggregateCIDFromRawCID(
                        contentResolver = applicationContext.contentResolver,
                        rawContactID = newRawCID
                    )

                    correspondingCID?.let {
                        /*
                        Set selectCID as newCID so that going back to the edit screen and editing
                        will update the data lists through the selectCIDSnapshot != null check.
                         */
                        _selectCID = it

                        setListsGivenPackagedLists(
                            DefaultContacts.getContactData(
                                contentResolver = applicationContext.contentResolver,
                                contactID = it,
                                context = applicationContext
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Deletes Contact given by [_selectCID] by deleting all of the underlying RawContacts.
     */
    fun deleteContact() {
        val selectCIDSnapShot = _selectCID

        scope.launch {
            DefaultContacts.deleteContact(
                context = applicationContext,
                contentResolver = applicationContext.contentResolver,
                aggregateCID = selectCIDSnapShot
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
     * Changes the blocked status of the contact in our database when the blocked button is pressed
     * in the ViewContactFragment.
     */
    fun changeIsBlockedWrapper(viewContactBlockedStatus: ViewContactBlockedStatus)  {
        CommonViewModelHelpers.changeIsBlocked(
            applicationContext = applicationContext,
            scope = scope,
            selectCIDSnapshot = selectCID,
            currentBlockedStatus = viewContactBlockedStatus.isBlocked
        ) { actualBlockedStatus ->
            val newViewContactBlockedStatus = viewContactBlockedStatus.copy(
                isBlocked = actualBlockedStatus
            )

            replaceInViewFormattedDataList(
                oldViewContactItem = viewContactBlockedStatus,
                newViewContactItem = newViewContactBlockedStatus
            )
        }
    }

    fun clearDataLists() {
        _selectCID = null
        originalDataList = mutableListOf()
        originalUpdatedDataList = mutableListOf()
        _updatedDataList = mutableListOf()
        _nonContactDataList = listOf()
        _viewFormattedList = mutableListOf()

        Timber.e("$DBL: Clearing contact data lists!")
    }

    private suspend fun setListsGivenPackagedLists(packagedDataLists: PackagedDataLists) {
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
        setViewFormattedList(packagedDataLists.viewFormattedList)
    }


    /**
     * TODO: Should we make these more generic extension functions? That is, add / remove to list
     *  by object reference? -> Maybe, maybe not. It would require wrapping the lists in an object
     *  or making list type enums, which is probably more cumbersome than having these few functions.
     */
    private suspend fun setUpdatedDataList(newUpdatedDataList: MutableList<ChangeContactItem>) {
        mutexUpdated.withLock {
            _updatedDataList = newUpdatedDataList
            updatedIndicatorLiveData.postValue(UUID.randomUUID())
        }
    }

    private fun setNonContactDataList(newNonContactDataList: List<ChangeContactItem>) {
        _nonContactDataList = newNonContactDataList
        updatedIndicatorLiveData.postValue(UUID.randomUUID())
    }

    private suspend fun setViewFormattedList(newViewFormattedList: MutableList<ViewContactItem>) {
        /*
        Mutex check here is to prevent blinking from overlapping updates (e.g., contact observer
        update and submit changes update).
         */
        mutexFormatted.withLock {
            Timber.e("$DBL: setViewFormattedList() - Was same = ${viewFormattedList == newViewFormattedList}")

            if (viewFormattedList != newViewFormattedList) {
                _viewFormattedList = newViewFormattedList
                viewFormattedIndicatorLiveData.postValue(UUID.randomUUID())
            }
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

    suspend fun replaceInViewFormattedDataList(
        oldViewContactItem: ViewContactItem,
        newViewContactItem: ViewContactItem
    ) {
        mutexFormatted.withLock {
            val index = _viewFormattedList.indexOfFirst { it === oldViewContactItem }
            if (index >= 0) {
                _viewFormattedList[index] = newViewContactItem
                viewFormattedIndicatorLiveData.postValue(UUID.randomUUID())
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
