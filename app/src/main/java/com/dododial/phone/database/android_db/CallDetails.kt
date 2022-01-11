package com.dododial.phone.database.android_db

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.CallLog
import android.util.Log
import androidx.annotation.RequiresApi

object CallLogHelper{

    /**
     * Returns list of CallLog objects using contentResolver.query()
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun getCallDetails(context: Context): MutableList<com.dododial.phone.database.entities.CallLog> {

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.GEOCODED_LOCATION
            )

        val curs: Cursor = context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null)!!

        var calls: MutableList<com.dododial.phone.database.entities.CallLog> = mutableListOf()
        while (curs.moveToNext()) {
                val number = curs.getString(0)
                val type = curs.getInt(1).toString()
                val date = curs.getString(2)
                val duration = curs.getString(3)
                val location = curs.getString(4)
            var dir: String? = null
            val dircode = type.toInt()
            when (dircode) {
                CallLog.Calls.OUTGOING_TYPE -> dir = "OUTGOING"
                CallLog.Calls.INCOMING_TYPE -> dir = "INCOMING"
                CallLog.Calls.MISSED_TYPE -> dir = "MISSED"
            }

            var callLog = com.dododial.phone.database.entities.CallLog(number, type, date, duration, location, dir)
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
        var calls = getCallDetails(context)
        for (call in calls) {
            Log.i("CALL LOG: ", call.toString())
        }
    }
}
