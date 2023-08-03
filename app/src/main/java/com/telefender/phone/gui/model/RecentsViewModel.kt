package com.telefender.phone.gui.model

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.*
import com.telefender.phone.data.default_database.DefaultCallDetails
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.gui.adapters.recycler_view_items.CallDetailItem
import com.telefender.phone.gui.adapters.recycler_view_items.CallHistoryFooter
import com.telefender.phone.gui.adapters.recycler_view_items.CallHistoryHeader
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId


/**
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
    private val context = getApplication<Application>().applicationContext

    private var _callLogs = MutableLiveData<List<CallDetail>>()
    val callLogs : LiveData<List<CallDetail>> = _callLogs

    private var _groupedCallLogs : MutableList<GroupedCallDetail> = mutableListOf()
    val groupedCallLogs : List<GroupedCallDetail>
        get() = _groupedCallLogs

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
    private var _dayLogs : MutableList<CallDetailItem> = mutableListOf()
    val dayLogs : List<CallDetailItem>
        get() = _dayLogs

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

    /**
     * TODO: Add unallowed to grouped call logs.
     *
     * Groups call logs by date, type, and number. Basically, this gives the (#calls) value
     * displayed on the right of each call log number.
     */
    private suspend fun groupCallLogs(logs: List<CallDetail>) {
        Timber.i("$DBL GROUP CALL LOG STARTING...")
        val tempGroups = mutableListOf<GroupedCallDetail>()

        withContext(Dispatchers.Default) {
            var prevLog: CallDetail? = null
            var currGroup: GroupedCallDetail? = null

            for (log in logs) {
                // If the log fits in the current group, then increment the group size.
                if (prevLog != null && currGroup != null
                    && log.rawNumber == prevLog.rawNumber
                    && log.callDirection == prevLog.callDirection
                    && log.callEpochDate in dayPeriod(prevLog.callEpochDate)) {

                    currGroup.amount = currGroup.amount + 1
                    currGroup.firstEpochID = log.callEpochDate
                    currGroup.callLocation = log.callLocation
                } else {
                    // If the log doesn't fit in the current group, create a new group.
                    tempGroups.add(log.createGroup())
                    currGroup = tempGroups.last()
                }
                prevLog = log
            }
        }

        _groupedCallLogs = tempGroups
    }

    // TODO: Consider not querying all call logs when updating from observer for performance.
    //  Also, consider canceling viewModelScope in onCleared() if the RecentsViewModel is destroyed.
    fun updateCallLogs() {
        viewModelScope.launch(Dispatchers.Default) {
            val tempLogs = DefaultCallDetails.getDefaultCallDetails(context)
            groupCallLogs(tempLogs)

            Timber.i("$DBL: ABOUT TO ASSIGN LOGS VALUE")
            _callLogs.postValue(tempLogs)
        }
    }

    /**
     * Filters the call logs by the selected epoch milli day range and selected number. The
     * resulting logs are assigned to [_dayLogs], which are the call logs associated with a
     * specific number on a specific day.
     */
    private fun dayLogFilter() {
        _dayLogs = _callLogs.value
            ?.filter { it.callEpochDate in startOfDay..endOfDay}
            ?.filter { it.rawNumber == selectNumber }
            ?.toMutableList()
            ?: mutableListOf()

        /**
         * Adds the CallHistoryHeader and CallHistoryFooter to data list so that the CallHistory
         * RecyclerView adapter can correctly show the header and footer views.
         */
        _dayLogs.add(0, CallHistoryHeader)
        _dayLogs.add(CallHistoryFooter)
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

        dayLogFilter()
    }

    /**
     * Updates the day logs. Useful when the displayed day logs are of the current day and a new
     * call log is created.
     */
    fun updateDayLogs() {
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
            dayLogFilter()
        }
    }
}

/***************************************************************************************************
 * For RecentsFragment
 **************************************************************************************************/

// TODO: Make this also include normalized number for UI.
data class GroupedCallDetail(
    val rawNumber: String,
    var callEpochDate: Long,
    var callLocation: String?,
    val callDirection: Int,
    val unallowed: Boolean,
    var amount: Int,
    var firstEpochID: Long
    ) {

    override fun toString() : String {
        return "rawNumber: $rawNumber callEpochDate: $callEpochDate" +
            " callLocation: $callLocation callDirection: ${TeleHelpers.getDirectionString(callDirection)}" +
            " unallowed: $unallowed"
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