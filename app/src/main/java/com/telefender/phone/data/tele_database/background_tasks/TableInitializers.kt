package com.telefender.phone.data.tele_database.background_tasks

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Build
import androidx.annotation.RequiresApi
import com.telefender.phone.data.default_database.DefaultContacts
import com.telefender.phone.data.tele_database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_INSERT
import com.telefender.phone.data.tele_database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_INSERT
import com.telefender.phone.data.tele_database.ClientDBConstants.CHANGELOG_TYPE_INSTANCE_INSERT
import com.telefender.phone.data.tele_database.ClientDatabase
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.entities.Change
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.helpers.MiscHelpers
import com.telefender.phone.permissions.PermissionRequester
import timber.log.Timber
import java.time.Instant
import java.util.*

object TableInitializers {

    private const val retryAmount = 5

    /**
     * TODO: Changed code to basically just use TableSynchronizer. Even though pretty much all the
     *  initializers EXCEPT initInstance() are replaceable by the synchronizer, we'll just keep
     *  them here anyways just in case. ALTHOUGH, technically the initializers' implementations
     *  are probably more than x2 times as fast. So, maybe it's still possible to incorporate into
     *  first database initialization, where there is a lot of un-synced default data upfront
     *
     * Called once to initialize the CallDetail table for the first time during database initialization.
     *
     * Inserts user's call logs into CallLogs table using DefaultCallDetails.getCallDetails()
     * to retrieve all logs and callLogDao().insertLog() to insert the log into the callLog table.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun initCallDetails(context: Context, repository: ClientRepository) {
        TableSynchronizer.syncCallLogs(context, repository, context.contentResolver)
    }

    /**
     * Called once to initialize the Instance table for the first time during database initialization.
     * Calls changeFromClient() to insert the instance insert change into ExecuteQueue and ChangeLog
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    suspend fun initInstance(context: Context, database: ClientDatabase, instanceNumber: String) {
        val changeID = UUID.randomUUID().toString()
        val changeTime = Instant.now().toEpochMilli() // get epoch time

        database.changeAgentDao().changeFromClient(
            ChangeLog(
                changeID = changeID,
                changeTime = changeTime,
                type = CHANGELOG_TYPE_INSTANCE_INSERT,
                instanceNumber = instanceNumber
            ),
            fromSync = false
        )
    }

    /**
     * Called once to initialize the Contact table for the first time during database initialization.
     *
     * Uses getContactCursor() to get all Contacts from Android database and calls cursContactInsert
     * for every item in the cursor, which adds the change to the ExecuteQueue and ChangeLog using
     * changeFromClient()
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun initContact(context: Context, database: ClientDatabase, contentResolver: ContentResolver) {

        if (!PermissionRequester.hasContactPermissions(context)) {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: No contact permissions in initContact()")
            return
        }

        val curs: Cursor? = DefaultContacts.getContactCursor(contentResolver)

        if (curs == null) {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: Contact cursor is null; BAD")
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
     * Uses getContactNumberCursor() to get all ContactNumber from Android database and calls
     * cursContactNumberInsert() for every item in the cursor, which adds the change to the QueuetoExecute
     * and ChangeLog using changeFromClient()
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun initContactNumber(context: Context, database: ClientDatabase, contentResolver: ContentResolver) {

        if (!PermissionRequester.hasContactPermissions(context)) {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: No contact permissions in initContactNumber()")
            return
        }

        val curs: Cursor? = DefaultContacts.getContactNumberCursor(contentResolver)
        if (curs == null) {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: Contact Number cursor is null; BAD")
        } else {
            while (!curs.isAfterLast) {
                cursContactNumberInsert(curs, context, database)
                curs.moveToNext()
            }
        }
        curs?.close()
    }

    /**
     * TODO: Retrieve blocked status from default database?
     *
     * Helper function to initContact(), adds current cursor item's Contact to ExecuteQueue and
     * ChangeLog using changeFromClient(), also responsible for generating changeID, changeTime, and CID.
     * changeID is random, but CID is based on the ByteArray of the original Contact's ID along with parentNumber
     */
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun cursContactInsert(cursor: Cursor, context : Context, database: ClientDatabase) {
        if (!PermissionRequester.hasLogPermissions(context)) {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: No log permissions in cursContactInsert()")
            return
        }

        val changeID = UUID.randomUUID().toString()
        val instanceNumber = MiscHelpers.getInstanceNumber(context)
        val teleCID = UUID.nameUUIDFromBytes((cursor.getString(0) + instanceNumber).toByteArray()).toString()
        val changeTime = Instant.now().toEpochMilli()

        val change = Change(
            CID = teleCID
        )

        // To insert into Contacts table
        database.changeAgentDao().changeFromClient(
            ChangeLog(
                changeID = changeID,
                changeTime = changeTime,
                type = CHANGELOG_TYPE_CONTACT_INSERT,
                instanceNumber = instanceNumber,
                changeJson = change.toJson(),
            ),
            fromSync = false
        )
    }

    /**
     * Helper function to initContactNumber(), adds current cursor item's ContactNumber to ExecuteQueue and
     * ChangeLog using changeFromClient(), also responsible for generating changeID, changeTime, and CID.
     * changeID is random, but CID is based on the ByteArray of the original ContactNumber ID and parentNumber
     */
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun cursContactNumberInsert(cursor: Cursor, context : Context, database: ClientDatabase) {
        if (!PermissionRequester.hasLogPermissions(context)) {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: No log permissions in cursContactNumberInsert()")
            return
        }

        val changeID = UUID.randomUUID().toString()
        val changeTime = Instant.now().toEpochMilli()
        val instanceNumber = MiscHelpers.getInstanceNumber(context)

        val defaultCID = cursor.getString(0)
        val teleCID = UUID.nameUUIDFromBytes((defaultCID + instanceNumber).toByteArray()).toString()
        val rawNumber = cursor.getString(1)
        val normalizedNumber = cursor.getString(2)
            ?: MiscHelpers.normalizedNumber(rawNumber)
            ?: MiscHelpers.bareNumber(rawNumber)
        val versionNumber = cursor.getString(3).toInt()

        val change = Change(
            CID = teleCID,
            normalizedNumber = normalizedNumber,
            defaultCID = defaultCID,
            rawNumber = rawNumber,
            degree = 0, // 0 means direct contact number
            counterValue = versionNumber // Use counterValue column to pass in versionNumber change
        )

        // To insert into ContactNumber table
        database.changeAgentDao().changeFromClient(
            ChangeLog(
                changeID = changeID,
                changeTime = changeTime,
                type = CHANGELOG_TYPE_CONTACT_NUMBER_INSERT,
                instanceNumber = instanceNumber,
                changeJson = change.toJson()
            ),
            fromSync = false
        )
    }
}