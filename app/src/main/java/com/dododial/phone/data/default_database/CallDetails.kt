package com.dododial.phone.data.default_database

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.CallLog
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber


data class GroupedCallDetail(
    val number: String,
    val callType: String,
    var callEpochDate: Long,
    var callLocation: String?,
    val callDirection: String?,
    var amount: Int,
    var firstEpochID: Long) {

    override fun toString() : String {
        return "number: " + number + " callType: " + callType + " callEpochDate: " +
            callEpochDate + " callLocation: " + callLocation + " callDirection: " +
            callDirection + " amount: " + amount + " firstEpochID: " + firstEpochID
    }
}

sealed interface CallLogItem

object CallHistoryHeader : CallLogItem

object CallHistoryFooter : CallLogItem

data class CallDetail(
    val number: String,
    val callType: String,
    val callEpochDate: Long,
    val callDuration: String,
    val callLocation: String?,
    val callDirection: String?
) : CallLogItem {

    override fun toString() : String {
        return "number: " + number + " callType: " + callType + " callEpochDate: " +
            callEpochDate + " callDuration: " + callDuration + " callLocation: " +
            callLocation + " callDirection: " + callDirection
    }

    fun createGroup() : GroupedCallDetail {
        return GroupedCallDetail(
            number,
            callType,
            callEpochDate,
            callLocation,
            callDirection,
            1,
            callEpochDate
        )
    }
}

object CallLogHelper{

    /**
     * Returns list of CallLog objects using contentResolver.query()
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getCallDetails(context: Context): MutableList<CallDetail> {

        return withContext(Dispatchers.IO) {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.GEOCODED_LOCATION
            )

            val curs: Cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null
            )!!

            val calls: MutableList<CallDetail> = mutableListOf()

            while (curs.moveToNext()) {
                val number = curs.getString(0)
                val type = curs.getInt(1).toString()
                // Epoch date is in milliseconds
                val date = curs.getString(2).toLong()
                val duration = curs.getString(3)
                val location = curs.getString(4)
                var dir: String? = null
                val dircode = type.toInt()
                when (dircode) {
                    CallLog.Calls.INCOMING_TYPE -> dir = "INCOMING"
                    CallLog.Calls.OUTGOING_TYPE -> dir = "OUTGOING"
                    CallLog.Calls.MISSED_TYPE -> dir = "MISSED"
                    CallLog.Calls.VOICEMAIL_TYPE -> dir = "VOICEMAIL"
                    CallLog.Calls.REJECTED_TYPE -> dir = "REJECTED"
                    CallLog.Calls.BLOCKED_TYPE -> dir = "BLOCKED"
                }

                val callLog = CallDetail(number, type, date, duration, location, dir)
                calls.add(callLog)
            }
            curs.close()

            Log.i("DODODEBUG","CALL LOG RETRIEVAL FINISHED")
            return@withContext calls
        }
    }

    /**
     * Returns list of CallLog objects using contentResolver.query()
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun getCallLogs(context: Context): MutableList<com.dododial.phone.data.dodo_database.entities.CallLog> {

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.GEOCODED_LOCATION
            )

        val curs: Cursor = context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null)!!

        var calls: MutableList<com.dododial.phone.data.dodo_database.entities.CallLog> = mutableListOf()
        while (curs.moveToNext()) {
                val number = curs.getString(0)
                val type = curs.getInt(1).toString()
                val date = curs.getString(2).toLong()
                val duration = curs.getString(3)
                val location = curs.getString(4)
            var dir: String? = null
            val dircode = type.toInt()
            when (dircode) {
                CallLog.Calls.OUTGOING_TYPE -> dir = "OUTGOING"
                CallLog.Calls.INCOMING_TYPE -> dir = "INCOMING"
                CallLog.Calls.MISSED_TYPE -> dir = "MISSED"
            }

            var callLog = com.dododial.phone.data.dodo_database.entities.CallLog(number, type, date, duration, location, dir)
            calls.add(callLog)
        }
        curs.close()
        return calls
    }

    /**
     * Writes all Call logs to LogCat console
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun callsLogCat(context: Context) {
        var calls = getCallLogs(context)
        for (call in calls) {
            Timber.i("CALL LOG: %s", call.toString())
        }
    }
}
