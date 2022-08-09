package com.dododial.phone.gui.model

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.dododial.phone.data.default_database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

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
    private var _dayLogs : MutableList<CallLogItem> = mutableListOf()
    val dayLogs : List<CallLogItem>
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
     * Groups call logs by date, type, and number. Basically, this gives the (#calls) value
     * displayed on the right of each call log number.
     */
    private suspend fun groupCallLogs(logs: List<CallDetail>) {
        Log.i("DODODEBUG", "GROUP CALL LOG START")
        val tempGroups = mutableListOf<GroupedCallDetail>()

        withContext(Dispatchers.Default) {
            Log.i("DODODEBUG", "GROUP CALL LOG MIDDLE")

            var prevLog: CallDetail? = null
            var currGroup: GroupedCallDetail? = null

            for (log in logs) {

                // If the log fits in the current group, then increment the group size.
                if (prevLog != null && currGroup != null
                    && log.number == prevLog.number
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

        Log.i("DODODEBUG", "GROUP CALL LOG END")
        _groupedCallLogs = tempGroups
    }

    fun updateCallLogs() {
        viewModelScope.launch {
            val tempLogs = CallLogHelper.getCallDetails(context)
            groupCallLogs(tempLogs)

            Log.i("DODODEBUG", "ABOUT TO ASSIGN LOGS VALUE")
            _callLogs.value = tempLogs
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
            ?.filter { it.number == selectNumber }
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

class RecentsViewModelFactory(
    private val app: Application)
    : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecentsViewModel::class.java)) {
            return RecentsViewModel(app) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}