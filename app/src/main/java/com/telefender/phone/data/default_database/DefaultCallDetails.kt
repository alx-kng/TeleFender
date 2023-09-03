package com.telefender.phone.data.default_database

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import com.telefender.phone.call_related.CallManager.calls
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber


/**
 * TODO: Consider changing this default database retrieval structure to a ContentProvider, but it
 *  may or may not be necessary seeing as that ContentProvider is often used to expose data to
 *  other applications.
 */
object DefaultCallDetails{

    /**
     * Returns list of CallDetail objects using contentResolver.query()
     */
    suspend fun getDefaultCallDetails(context: Context): MutableList<CallDetail> {
        return withContext(Dispatchers.IO) {
            // If user number retrieval fails somehow, return empty list.
            val instanceNumber = TeleHelpers.getUserNumberUncertain(context)
                ?: return@withContext mutableListOf()

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
                    calls.add(
                        currentCursorCallDetail(
                            context = context,
                            instanceNumber = instanceNumber,
                            curs = curs,
                        )
                    )
                }
                curs.close()
            }

            Timber.i("$DBL: CALL LOG RETRIEVAL FINISHED")
            return@withContext calls
        }
    }

    /**
     * Returns a cursor to all of the CallDetail objects using contentResolver.query().
     */
    suspend fun getDefaultCallDetailCursor(context: Context): Cursor? {
        return withContext(Dispatchers.IO) {
            // If user number retrieval fails somehow, return null cursor.
            TeleHelpers.getUserNumberUncertain(context) ?: return@withContext null

            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.GEOCODED_LOCATION,
            )

            val sortOrder = "${CallLog.Calls.DATE} DESC"

            var curs : Cursor? = null

            try {
                curs = context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )
            } catch (e: Exception) {
                Timber.e("$DBL: getDefaultCallDetailCursor() - ${e.message}")
            }

            Timber.i("$DBL: CALL CURSOR RETRIEVAL FINISHED")
            return@withContext curs
        }
    }

    fun currentCursorCallDetail(
        context: Context,
        instanceNumber: String,
        curs: Cursor
    ) : CallDetail{
        val rawNumber = curs.getString(0)
        // If normalizedNumber is null, use bareNumber() cleaning of rawNumber.
        val normalizedNumber = TeleHelpers.normalizedNumber(rawNumber)
            ?: TeleHelpers.bareNumber(rawNumber)
        val typeInt = curs.getInt(1)
        // Epoch date is in milliseconds and is the creation time.
        val date = curs.getString(2).toLong()
        val duration = curs.getString(3).toLong()
        val location = curs.getString(4)
        val dir = TeleHelpers.getTrueDirection(context, typeInt, rawNumber)

        return CallDetail(
            rawNumber = rawNumber,
            normalizedNumber = normalizedNumber,
            callType = dir.toString(),
            callEpochDate = date,
            callDuration = duration,
            callLocation = location,
            callDirection = dir,
            instanceNumber = instanceNumber
        )
    }
}
