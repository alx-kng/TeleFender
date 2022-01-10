package com.dododial.phone.database

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.database.Cursor
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_INSERT
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_INSERT
import com.dododial.phone.database.android_db.CallDetailHelper
import com.dododial.phone.database.android_db.ContactDetailsHelper
import java.time.Instant
import java.util.*

object TableInitializers {

    // TODO Possibly put initializer for Misc numbers? Or better, synchronize / analyze
    //  somewhere separate

    /**
     * Inserts user's call logs into CallLogs table
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun initCallLog(context: Context, database: ClientDatabase) {

        val calls = CallDetailHelper.getCallDetails(context)

        for (call in calls) {
            val log = CallLog(
                call.number,
                call.callType,
                call.callEpochDate,
                call.callDuration,
                call.callLocation,
                call.callDirection
            )

            database.callLogDao().insertLog(log)
        }
    }

    /**
     * Inserts users number as a row to the instance table
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    suspend fun initInstance(context: Context, database: ClientDatabase) {
        val tMgr = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        val changeID = UUID.randomUUID().toString() // create a new UUID
        val instanceNumber = tMgr.line1Number // get phone # of user
        val changeTime = Instant.now().toEpochMilli().toString() // get epoch time
        val type = "insInsert"

        database.changeAgentDao().changeFromClient(
            changeID,
            instanceNumber,
            changeTime,
            type,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )
    }

    /**
     * For each contact on device, create ChangeLog for cInsert
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("LogNotTimber")
    suspend fun initContact(context: Context, database: ClientDatabase, contentResolver: ContentResolver) {

        val curs: Cursor? = ContactDetailsHelper.getContactCursor(contentResolver)

        if (curs == null) {
            Log.i("DODODEBUG: ", "Contact cursor is null; BAD")
        } else {
            while (!curs.isAfterLast) {
                cursContactInsert(curs, context, database)

                curs.moveToNext()
            }
        }
    }

    /**
     * For each contact number on device, create ChangeLog for cnInsert
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("LogNotTimber")
    suspend fun initContactNumber(context: Context, database: ClientDatabase, contentResolver: ContentResolver) {

        val curs: Cursor? = ContactDetailsHelper.getContactNumberCursor(contentResolver)

        if (curs == null) {
            Log.i("DODODEBUG: ", "Contact Number cursor is null; BAD")
        } else {
            while (!curs.isAfterLast) {
                cursContactNumberInsert(curs, context, database)

                curs.moveToNext()
            }
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun cursContactInsert(cursor: Cursor, context : Context, database: ClientDatabase) {
        val tMgr = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        val cChangeID = UUID.randomUUID().toString()
        val changeTime = Instant.now().toEpochMilli().toString()
        val CID = UUID.nameUUIDFromBytes(cursor.getString(0).toByteArray()).toString()
        val name = cursor.getString(1)
        val parentNumber = tMgr.line1Number

        // To insert into Contacts table
        database.changeAgentDao().changeFromClient(
            cChangeID,
            null, // instanceNumber is only for inserting into the Instance table
            changeTime,
            CHANGELOG_TYPE_CONTACT_INSERT,
            CID,
            name,
            null,
            null,
            parentNumber,
            null,
            null
        )
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun cursContactNumberInsert(cursor: Cursor, context : Context, database: ClientDatabase) {

        val cnChangeID = UUID.randomUUID().toString()
        val changeTime = Instant.now().toEpochMilli().toString()
        val CID = UUID.nameUUIDFromBytes(cursor.getString(0).toByteArray()).toString()
        val number = cursor.getString(1)
        val name: String? = cursor.getString(2)
        val versionNumber = cursor.getString(3).toInt()

        // To insert into ContactNumbers table
        database.changeAgentDao().changeFromClient(
            cnChangeID,
            null,
            changeTime,
            CHANGELOG_TYPE_CONTACT_NUMBER_INSERT,
            CID,
            name,
            null,
            number,
            null,
            null,
            versionNumber // Use counterValue column to pass in versionNumber change
        )
    }
}