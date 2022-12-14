package com.telefender.phone.data.default_database

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.CallLog
import android.util.Log
import androidx.annotation.RequiresApi
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


object DefaultCallDetails{

    /**
     * Returns list of CallDetail objects using contentResolver.query()
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getDefaultCallDetails(context: Context): MutableList<CallDetail> {

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
                val duration = curs.getString(3).toLong()
                val location = curs.getString(4)
                val dir: String = getCallDirection(type.toInt())

                val callDetail = CallDetail(number, type, date, duration, location, dir)
                calls.add(callDetail)
            }
            curs.close()

            Log.i("${MiscHelpers.DEBUG_LOG_TAG}","CALL LOG RETRIEVAL FINISHED")
            return@withContext calls
        }
    }

    fun getCallDirection(directionCode: Int) : String {
        return when (directionCode) {
            CallLog.Calls.INCOMING_TYPE -> "INCOMING"
            CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
            CallLog.Calls.MISSED_TYPE -> "MISSED"
            CallLog.Calls.VOICEMAIL_TYPE -> "VOICEMAIL"
            CallLog.Calls.REJECTED_TYPE -> "REJECTED"
            CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
            else -> "UNKNOWN DIRECTION"
        }
    }


    /**
     * Returns list of CallDetail objects using contentResolver.query()
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun getCallLogs(context: Context): MutableList<CallDetail> {

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.GEOCODED_LOCATION
            )

        val curs: Cursor = context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null)!!

        var calls: MutableList<CallDetail> = mutableListOf()
        while (curs.moveToNext()) {
            val number = curs.getString(0)
            val type = curs.getInt(1).toString()
            val date = curs.getString(2).toLong()
            val duration = curs.getString(3).toLong()
            val location = curs.getString(4)
            val dir: String = getCallDirection(type.toInt())

            val callDetail = CallDetail(number, type, date, duration, location, dir)
            calls.add(callDetail)
        }
        curs.close()
        return calls
    }

    /**
     * Writes all Call logs to LogCat console
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun logCallDetails(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val calls = getDefaultCallDetails(context)
            for (call in calls) {
                Timber.i("CALL LOG: %s", call.toString())
            }
        }
    }
}
