package com.telefender.phone.gui.model

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.*
import com.telefender.phone.App
import com.telefender.phone.data.default_database.DefaultCallDetails
import com.telefender.phone.data.default_database.DefaultContacts
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.gui.adapters.recycler_view_items.*
import com.telefender.phone.gui.adapters.recycler_view_items.common_types.SafetyStatus
import com.telefender.phone.gui.adapters.recycler_view_items.common_types.getSafetyStatus
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.sql.Time
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*


/**
 * TODO: Clear data lists when back press
 *
 * TODO: Although we can identify Voicemail by +1 and incoming, maybe we should combine the calls
 *  UI wise so that the user can't tell the difference (maybe even hide the +1).
 *
 * TODO: Consider also querying from Tele database so that we can differentiate UI for unallowed
 *  and declined calls.
 *
 * TODO: LOOK INTO PAGING FOR RECENTS AND CONTACTS <-- prob not necessary but we can also consider
 *  using our own chunk loader.
 *
 * TODO: Probably / Maybe refactor RecentsViewModel to use repository to actually query the data,
 *  as the "good" app architecture suggests the repository should be a single source of truth.
 *  This would require you to pass in application context to repository. See if it works.
 *  OR we could just keep it here to make things not so complicated.
 */
class RecentsViewModel(app: Application) : AndroidViewModel(app) {

    @SuppressLint("StaticFieldLeak")
    private val applicationContext = getApplication<Application>().applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)

    /**********************************************************************************************
     * For RecentsFragment
     **********************************************************************************************/

    private var _callLogs = MutableLiveData<List<CallDetail>>()
    val callLogs : LiveData<List<CallDetail>> = _callLogs

    private var _groupedCallLogs : MutableList<RecentsGroupedCallDetail> = mutableListOf()
    val groupedCallLogs : List<RecentsGroupedCallDetail>
        get() = _groupedCallLogs

    private val numberNameMap = mutableMapOf<String, String>()
    private val noContactSet = mutableSetOf<String>()

    /**********************************************************************************************
     * For CallHistoryFragment
     **********************************************************************************************/

    /**
     * Selected number for the CallHistory screen. Info retrieved from selected call log.
     */
    private var _selectNumber = ""
    val selectNumber : String
        get() = _selectNumber

    /**
     * Selected epoch time for the CallHistory screen. Info retrieved from selected call log.
     * Should come from the same call log as [_selectNumber].
     */
    private var _selectTime = 0L
    val selectTime : Long
        get() = _selectTime

    /**
     * The epoch start and end of day given [_selectTime].
     */
    private var startOfDay = 0L
    private var endOfDay = 0L

    /**
     * Call logs associated with a specific number on a specific day. For Call History screen.
     */
    private var _dayLogs : MutableList<CallHistoryItem> = mutableListOf()
    val dayLogs : List<CallHistoryItem>
        get() = _dayLogs

    val dayLogIndicatorLiveData = MutableLiveData<UUID>()

    private val mutexDay = Mutex()

    /**********************************************************************************************
     * Callback used for both the RecentsFragment and CallHistoryFragment. This is called when we
     * detect a change in the default call logs.
     **********************************************************************************************/

    fun onCallLogUpdate() {
        updateCallLogs()
        updateDayLogs()
    }

    fun onContactsUpdate() {
        val job = dayLogFilter(
            selectNumberParam = _selectNumber,
            selectTimeParam = _selectTime
        )

        viewModelScope.launch(Dispatchers.Default) {
            // Wait for dayLogFilter to finish so that the nameMap can be updated.
            job.join()

            callLogs.value?.let {
                groupCallLogs(it)
            }
        }
    }

    /**********************************************************************************************
     * For RecentsFragment
     **********************************************************************************************/

    /**
     * Preloads call logs when ViewModel is first created.
     */
    init {
        updateCallLogs()
    }

    /**
     * Dummy method to initialize ViewModel.
     */
    fun activateDummy() {}

    /**
     * TODO: Consider not querying all call logs when updating from observer for performance.
     * TODO: Consider canceling viewModelScope in onCleared() if the RecentsViewModel is destroyed.
     */
    private fun updateCallLogs() {
        viewModelScope.launch(Dispatchers.Default) {
            val tempLogs = DefaultCallDetails.getDefaultCallDetails(applicationContext)
            groupCallLogs(tempLogs)

            Timber.i("$DBL: ABOUT TO ASSIGN LOGS VALUE")
            _callLogs.postValue(tempLogs)
        }
    }

    /**
     * TODO: Add unallowed to grouped call logs.
     *
     * Groups call logs by date, type, and number. Basically, this gives the (#calls) value
     * displayed on the right of each call log number.
     */
    private suspend fun groupCallLogs(logs: List<CallDetail>) {
        Timber.i("$DBL GROUP CALL LOG STARTING...")
        val tempGroups = mutableListOf<RecentsGroupedCallDetail>()

        withContext(Dispatchers.Default) {
            var prevLog: CallDetail? = null
            var currGroup: RecentsGroupedCallDetail? = null

            for (log in logs) {
                val mappedContactName = numberNameMap[log.normalizedNumber]
                val definitelyNoContact = noContactSet.contains(log.normalizedNumber)
                val associatedContactName: String

                if (mappedContactName == null && !definitelyNoContact) {
                    val contactName = DefaultContacts.getFirstFullContactFromNumber(
                        contentResolver = applicationContext.contentResolver,
                        number = log.normalizedNumber
                    )?.second

                    if (contactName == null) {
                        noContactSet.add(log.normalizedNumber)
                    } else {
                        numberNameMap[log.normalizedNumber] = contactName
                    }

                    associatedContactName = contactName ?: log.normalizedNumber
                } else {
                    // Definitely no contact OR has mapped contact name.
                    associatedContactName = mappedContactName ?: log.normalizedNumber
                }

                // If the log fits in the current group, then increment the group size.
                if (prevLog != null && currGroup != null
                    && log.rawNumber == prevLog.rawNumber
                    && log.callDirection == prevLog.callDirection
                    && log.callEpochDate in dayPeriod(prevLog.callEpochDate)) {

                    currGroup.amount = currGroup.amount + 1
                    currGroup.firstEpochID = log.callEpochDate
                    currGroup.callLocation = log.callLocation
                    currGroup.name = associatedContactName
                } else {
                    // If the log doesn't fit in the current group, create a new group.
                    tempGroups.add(log.createGroup())
                    currGroup = tempGroups.last()
                    currGroup.name = associatedContactName
                }
                prevLog = log
            }
        }

        _groupedCallLogs = tempGroups
    }


    /**
     * Retrieves the epoch milli range of the day containing [epochTime].
     */
    fun dayPeriod(epochTime: Long): LongRange {
        val milliInDay = 24 * 60 * 60 * 1000
        val localDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochTime), ZoneId.systemDefault())
        val startOfDay = localDate
            .toLocalDate()
            .atStartOfDay()
            .toInstant(ZoneId.systemDefault().rules.getOffset(localDate))
            .toEpochMilli()

        val endOfDay = startOfDay + milliInDay - 1
        return startOfDay..endOfDay
    }

    /**********************************************************************************************
     * For CallHistoryFragment
     **********************************************************************************************/

    /**
     * Updates the day logs. Useful when the displayed day logs are of the current day and a new
     * call log is created.
     */
    private fun updateDayLogs() {
        val selectNumberSnapshot = _selectNumber
        val currentTime = LocalDateTime.now()
        val today = currentTime
            .toLocalDate()
            .atStartOfDay()
            .toInstant(ZoneId.systemDefault().rules.getOffset(currentTime))
            .toEpochMilli()

        /**
         * No need to update day logs if the day logs aren't of the current day, as they won't be
         * updated anymore.
         */
        if (endOfDay >= today) {
            dayLogFilter(
                selectNumberParam = selectNumberSnapshot,
                selectTimeParam = selectTime
            )
        }
    }

    /**
     * Called when a specific call log's info button is first pressed. Sets the [_selectNumber]
     * and [_selectTime] properties, which are then used to set [startOfDay] and [endOfDay]. New
     * day logs are then filtered out and assigned to [_dayLogs] in dayLogFilter().
     */
    fun retrieveDayLogs(number: String, epochTime: Long) {
        _selectNumber = number
        _selectTime = epochTime
        val milliInDay = 24 * 60 * 60 * 1000
        val localDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(selectTime), ZoneId.systemDefault())
        startOfDay = localDate
            .toLocalDate()
            .atStartOfDay()
            .toInstant(ZoneId.systemDefault().rules.getOffset(localDate))
            .toEpochMilli()

        endOfDay = startOfDay + milliInDay - 1

        dayLogFilter(
            selectNumberParam = number,
            selectTimeParam = epochTime
        )
    }

    /**
     * Filters the call logs by the selected epoch milli day range and selected number. The
     * resulting logs are assigned to [_dayLogs], which are the call logs associated with a
     * specific number on a specific day. Also adds in UI items so that it's formatted for the
     * CallHistory Fragment.
     */
    private fun dayLogFilter(
        selectNumberParam: String,
        selectTimeParam: Long,
    ) : Job {
        return viewModelScope.launch(Dispatchers.Default) {
            val tempDayLogs: MutableList<CallHistoryItem> = _callLogs.value
                ?.filter { it.callEpochDate in startOfDay..endOfDay}
                ?.filter { it.rawNumber == selectNumber }
                ?.map { CallHistoryData(callDetail = it) }
                ?.toMutableList()
                ?: mutableListOf()

            val repository = (applicationContext as App).repository
            val normalizedNum = TeleHelpers.normalizedNumber(selectNumberParam)
            val analyzedNumber = normalizedNum?.let {
                repository.getAnalyzedNum(normalizedNumber = normalizedNum)
            }
            val analyzed = analyzedNumber?.getAnalyzed()

            val associatedContact = DefaultContacts.getFirstFullContactFromNumber(
                contentResolver = applicationContext.contentResolver,
                number = selectNumberParam
            )

            // Updates number name map and no contact set in case of modification.
            val name = associatedContact?.second
            normalizedNum?.let {
                if (name == null) {
                    numberNameMap.remove(key = it)
                    noContactSet.add(element = it)
                } else {
                    numberNameMap[it] = name
                    noContactSet.remove(element = it)
                }
            }

            val isContact = associatedContact != null

            tempDayLogs.add(0,
                CallHistoryHeader(
                    associatedNumber = selectNumberParam,
                    defaultCID = associatedContact?.first,
                    displayName = associatedContact?.second,
                    primaryEmail = associatedContact?.third,
                )
            )

            val locale = Locale.getDefault()
            val dateFormat = SimpleDateFormat("MM/dd/yy", locale)
            val date = Date(selectTimeParam)

            if (isContact) {
                tempDayLogs.add(1,
                    CallHistoryBlockedStatus(isBlocked = analyzed?.isBlocked ?: false)
                )
            } else {
                val safetyStatus = getSafetyStatus(
                    isBlocked = analyzed?.isBlocked ?: false,
                    markedSafe = analyzed?.markedSafe ?: false
                )

                tempDayLogs.add(1,
                    CallHistorySafetyStatus(safetyStatus = safetyStatus ?: SafetyStatus.DEFAULT)
                )
            }

            tempDayLogs.add(2,
                CallHistorySelectTime(
                    date = dateFormat.format(date)
                )
            )

            tempDayLogs.add(CallHistoryFooter())

            setDayLogs(newDayLogs = tempDayLogs)
        }
    }

    /**
     * Changes the blocked status of the contact in our database when the blocked button is pressed
     * in the CallHistoryFragment.
     */
    fun changeIsBlockedWrapper(callHistoryBlockedStatus: CallHistoryBlockedStatus)  {
        CommonViewModelHelpers.changeIsBlocked(
            applicationContext = applicationContext,
            scope = scope,
            selectCIDSnapshot = getCurrentCID(),
            currentBlockedStatus = callHistoryBlockedStatus.isBlocked
        ) { actualBlockedStatus ->
            val newCallHistoryBlockedStatus = callHistoryBlockedStatus.copy(
                isBlocked = actualBlockedStatus
            )

            replaceInDayLogs(
                oldCallHistoryItem = callHistoryBlockedStatus,
                newCallHistoryItem = newCallHistoryBlockedStatus
            )
        }
    }

    /**
     * Changes the safety status of the number in our database when a safety button is pressed in
     * the CallHistoryFragment.
     */
    fun changeSafetyStatusWrapper(
        callHistorySafetyStatus: CallHistorySafetyStatus,
        newSafetyStatus: SafetyStatus
    ) {
        CommonViewModelHelpers.changeSafetyStatus(
            applicationContext = applicationContext,
            scope = scope,
            number = selectNumber,
            newSafetyStatus = newSafetyStatus
        ) { actualSafetyStatus ->
            val newCallHistorySafetyStatus = callHistorySafetyStatus.copy(
                safetyStatus = actualSafetyStatus
            )

            replaceInDayLogs(
                oldCallHistoryItem = callHistorySafetyStatus,
                newCallHistoryItem = newCallHistorySafetyStatus
            )
        }
    }

    fun clearCallHistoryLists() {
        _selectNumber = ""
        _selectTime = 0L
        startOfDay = 0L
        endOfDay = 0L
        _dayLogs = mutableListOf()

        Timber.e("$DBL: Clearing CallHistory data lists!")
    }

    /**
     * ID of top contact associated with [selectNumber]. May or may not exist.
     */
    fun getCurrentCID() : String? {
        val header = dayLogs.find { it is CallHistoryHeader } as CallHistoryHeader?
        return header?.defaultCID
    }

    fun hasHeader() : Boolean {
        val header = dayLogs.find { it is CallHistoryHeader }
        return header != null
    }


    private suspend fun setDayLogs(newDayLogs: MutableList<CallHistoryItem>) {
        mutexDay.withLock {
            _dayLogs = newDayLogs
            dayLogIndicatorLiveData.postValue(UUID.randomUUID())
        }
    }

    suspend fun replaceInDayLogs(
        oldCallHistoryItem: CallHistoryItem,
        newCallHistoryItem: CallHistoryItem
    ) {
        mutexDay.withLock {
            val index = _dayLogs.indexOfFirst { it === oldCallHistoryItem }
            if (index >= 0) {
                _dayLogs[index] = newCallHistoryItem
                dayLogIndicatorLiveData.postValue(UUID.randomUUID())
            }
        }
    }
}

class RecentsViewModelFactory(
    private val app: Application,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecentsViewModel::class.java)) {
            return RecentsViewModel(app) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}