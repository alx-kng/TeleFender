package com.dododial.phone.database.background_tasks

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.database.Cursor
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.dododial.phone.database.entities.CallLog
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_INSERT
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_INSERT
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_INSTANCE_INSERT
import com.dododial.phone.database.ClientDatabase
import com.dododial.phone.database.MiscHelpers
import com.dododial.phone.database.android_db.CallLogHelper
import com.dododial.phone.database.android_db.ContactDetailsHelper
import com.dododial.phone.database.entities.ChangeLog
import com.dododial.phone.database.entities.QueueToExecute
import timber.log.Timber
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

        val calls = CallLogHelper.getCallDetails(context)

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

        val changeID = UUID.randomUUID().toString() // create a new changeLog
        val instanceNumber = MiscHelpers.cleanNumber(tMgr.line1Number) // get phone # of user
        val changeTime = Instant.now().toEpochMilli() // get epoch time
        val type = CHANGELOG_TYPE_INSTANCE_INSERT

        val changeLog = ChangeLog(
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

        database.changeLogDao().insertChangeLog(changeLog)

        val execLog = QueueToExecute(changeID, changeTime)
        database.queueToExecuteDao().insertQTE(execLog)
    }

    /**
     * Called once to initialize the Contact table for the first time during database initialization.
     *
     * Uses getContactCursor() to get all Contacts from Android database and calls cursContactInsert
     * for every item in the cursor, which adds the change to the QueueToExecute and ChangeLog using
     * changeFromClient()
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun initContact(context: Context, database: ClientDatabase, contentResolver: ContentResolver) {

        val curs: Cursor? = ContactDetailsHelper.getContactCursor(contentResolver)

        if (curs == null) {
            Timber.e("DODODEBUG: Contact cursor is null; BAD")
        } else {
            while (!curs.isAfterLast) {
                cursContactInsert(curs, context, database)

                curs.moveToNext()
            }
        }
        curs?.close()
    }

    /**
     * Called once to initialize the ContactNumber table for the first time during database initialization.
     *
     * Uses getContactNumberCursor() to get all ContactNumbers from Android database and calls
     * cursContactNumberInsert() for every item in the cursor, which adds the change to the QueuetoExecute
     * and ChangeLog using changeFromClient()
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun initContactNumber(context: Context, database: ClientDatabase, contentResolver: ContentResolver) {

        val curs: Cursor? = ContactDetailsHelper.getContactNumberCursor(contentResolver)
        if (curs == null) {
            Timber.e("DODODEBUG: Contact Number cursor is null; BAD")
        } else {
            while (!curs.isAfterLast) {
                cursContactNumberInsert(curs, context, database)
                curs.moveToNext()
            }
        }
        curs?.close()
    }

    /**
     * Helper function to initContact(), adds current cursor item's Contact to QueueToExecute and
     * ChangeLog using changeFromClient(), also responsible for generating changeID, changeTime, and CID.
     * changeID is random, but CID is based on the ByteArray of the original Contact's ID along with parentNumber
     */
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun cursContactInsert(cursor: Cursor, context : Context, database: ClientDatabase) {
        val tMgr = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val parentNumber = MiscHelpers.cleanNumber(tMgr.line1Number)


        val cChangeID = UUID.randomUUID().toString()
        val changeTime = Instant.now().toEpochMilli()
        val CID = UUID.nameUUIDFromBytes((cursor.getString(0) + parentNumber).toByteArray()).toString()
        val name = cursor.getString(1)

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
     * changeID is random, but CID is based on the ByteArray of the original ContactNumbers ID and parentNumber
     */
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun cursContactNumberInsert(cursor: Cursor, context : Context, database: ClientDatabase) {
        val tMgr = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val parentNumber = MiscHelpers.cleanNumber(tMgr.line1Number)

        val cnChangeID = UUID.randomUUID().toString()
        val changeTime = Instant.now().toEpochMilli()
        val CID = UUID.nameUUIDFromBytes((cursor.getString(0) + parentNumber).toByteArray()).toString()
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