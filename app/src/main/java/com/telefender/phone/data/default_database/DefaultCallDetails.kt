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
     * TODO: Use cached normalizedNumber
     *
     * Returns list of CallDetail objects using contentResolver.query()
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getDefaultCallDetails(context: Context): MutableList<CallDetail> {

        return withContext(Dispatchers.IO) {
            val instanceNumber = MiscHelpers.getUserNumberStored(context)

            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.GEOCODED_LOCATION,
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
                    val rawNumber = curs.getString(0)
                    // If normalizedNumber is null, use bareNumber() cleaning of rawNumber.
                    val normalizedNumber = MiscHelpers.normalizedNumber(rawNumber)
                        ?: MiscHelpers.bareNumber(rawNumber)
                    val typeInt = curs.getInt(1)
                    // Epoch date is in milliseconds and is the creation time.
                    val date = curs.getString(2).toLong()
                    val duration = curs.getString(3).toLong()
                    val location = curs.getString(4)
                    val dir = MiscHelpers.getTrueDirection(typeInt, rawNumber)

                    val callDetail = CallDetail(
                        rawNumber = rawNumber,
                        normalizedNumber = normalizedNumber,
                        callType = dir.toString(),
                        callEpochDate = date,
                        callDuration = duration,
                        callLocation = location,
                        callDirection = dir,
                        instanceNumber = instanceNumber
                    )
                    calls.add(callDetail)
                }
                curs.close()
            }

            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: CALL LOG RETRIEVAL FINISHED")
            return@withContext calls
        }
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
