package com.dododial.phone.database.android_db

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.CallLog
import android.util.Log
import androidx.annotation.RequiresApi

data class CallDetails(
    val number: String,
    val callType: String,
    val callEpochDate: String,
    val callDuration: String,
    val callLocation: String?,
    val callDirection: String?
) {
    override fun toString() : String {
        return this.number + " callType: " + this.callType + " callEpochDate: " +
            this.callEpochDate + " callDuration: " + this.callDuration + " callLocation: " +
            this.callLocation.toString() + " callDirection: " + this.callDirection.toString()

    }

}

object CallDetailHelper{

    /**
     * Returns list of CallDetails (call log) objects using contentResolver.query()
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun getCallDetails(context: Context): MutableList<CallDetails> {

        val managedCursor: Cursor =
            context.contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null)!!

        val number: Int = managedCursor.getColumnIndex(CallLog.Calls.NUMBER)
        val type: Int = managedCursor.getColumnIndex(CallLog.Calls.TYPE)
        val date: Int = managedCursor.getColumnIndex(CallLog.Calls.DATE)
        val duration: Int = managedCursor.getColumnIndex(CallLog.Calls.DURATION)
        val location: Int = managedCursor.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)
        //sb.append("Call Details :")

        var calls: MutableList<CallDetails> = mutableListOf()
        while (managedCursor.moveToNext()) {
            val phNumber: String = managedCursor.getString(number)
            val callType: String = managedCursor.getString(type)
            val callEpochDate: String = managedCursor.getString(date)
            //val callDayTime = Date(Long.valueOf(callDate))
            val callDuration: String = managedCursor.getString(duration)
            val callLocation: String? = managedCursor.getString(location) ?: "Unknown location"
            var dir: String? = null
            val dircode = callType.toInt()
            when (dircode) {
                CallLog.Calls.OUTGOING_TYPE -> dir = "OUTGOING"
                CallLog.Calls.INCOMING_TYPE -> dir = "INCOMING"
                CallLog.Calls.MISSED_TYPE -> dir = "MISSED"
            }

            var callDetails =
                CallDetails(phNumber, callType, callEpochDate, callDuration, callLocation, dir)
            calls.add(callDetails)
        }
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
