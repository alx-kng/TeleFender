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
     * Called once to initialize the CallLog table for the first time during database initialization.
     *
     * Inserts user's call logs into CallLogs table using CallDetailHelper.getCallDetails()
     * to retrieve all logs and callLogDao().insertLog() to insert the log into the callLog table.
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
     * Called once to initialize the Instance table for the first time during database initialization.
     *
     * Calls changeFromClient() to insert the instance insert change into QueueToExecute and ChangeLog
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    suspend fun initInstance(context: Context, database: ClientDatabase) {
        val tMgr = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        val changeID = UUID.randomUUID().toString() // create a new UUID TODO should this be random or based on bytearray of args for easy recreation?
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
     * Called once to initialize the Contact table for the first time during database initialization.
     *
     * Uses getContactCursor() to get all Contacts from Android database and calls cursContactInsert
     * for every item in the cursor, which adds the change to the QueueToExecute and ChangeLog using
     * changeFromClient()
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
     * Called once to initialize the ContactNumber table for the first time during database initialization.
     *
     * Uses getContactNumberCursor() to get all ContactNumbers from Android database and calls
     * cursContactNumberInsert() for every item in the cursor, which adds the change to the QueuetoExecute
     * and ChangeLog using changeFromClient()
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

    /**
     * Helper function to initContact(), adds current cursor item's Contact to QueueToExecute and
     * ChangeLog using changeFromClient(), also responsible for generating changeID, changeTime, and CID.
     * changeID is random, but CID is based on the ByteArray of the original Contact's ID
     */
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

    /**
     * Helper function to initContactNumber(), adds current cursor item's ContactNumber to QueueToExecute and
     * ChangeLog using changeFromClient(), also responsible for generating changeID, changeTime, and CID.
     * changeID is random, but CID is based on the ByteArray of the original ContactNumbers ID
     */
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun cursContactNumberInsert(cursor: Cursor, context : Context, database: ClientDatabase) {

        val cnChangeID = UUID.randomUUID().toString()
        val changeTime = Instant.now().toEpochMilli().toString()
        val CID = UUID.nameUUIDFromBytes(cursor.getString(0).toByteArray()).toString()
        val number = cursor.getString(1)
        val versionNumber = cursor.getString(2).toInt()

        // To insert into ContactNumbers table
        database.changeAgentDao().changeFromClient(
            cnChangeID,
            null,
            changeTime,
            CHANGELOG_TYPE_CONTACT_NUMBER_INSERT,
            CID,
            null, // Still pass in name for clarity even though it's not used in CN table
            null,
            number,
            null,
            null,
            versionNumber // Use counterValue column to pass in versionNumber change
        )
    }
}