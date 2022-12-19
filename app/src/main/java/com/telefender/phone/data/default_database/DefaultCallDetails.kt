package com.telefender.phone.data.default_database

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.CallLog
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
     * TODO: Need to find out if date is creation time or connect time.
     * TODO: UI SHOWING WRONG ORDER (Recents should be descending instead of ascending).
     *
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

            val curs : Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                null
            )

            val calls: MutableList<CallDetail> = mutableListOf()

            if (curs != null) {
                while (curs.moveToNext()) {
                    val number = curs.getString(0)
                    val typeInt = curs.getInt(1)
                    // Epoch date is in milliseconds
                    val date = curs.getString(2).toLong()
                    val duration = curs.getString(3).toLong()
                    val location = curs.getString(4)
                    val dir = MiscHelpers.getTrueDirection(typeInt, number)

                    val callDetail = CallDetail(number, dir.toString(), date, duration, location, dir)
                    calls.add(callDetail)
                }
                curs.close()
            }

            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: CALL LOG RETRIEVAL FINISHED")
            return@withContext calls
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
            val typeInt = curs.getInt(1)
            val date = curs.getString(2).toLong()
            val duration = curs.getString(3).toLong()
            val location = curs.getString(4)
            val dir = MiscHelpers.getTrueDirection(typeInt, number)

            val callDetail = CallDetail(number, dir.toString(), date, duration, location, dir)
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
