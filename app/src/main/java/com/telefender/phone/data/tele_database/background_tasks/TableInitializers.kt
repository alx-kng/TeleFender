package com.telefender.phone.data.tele_database.background_tasks

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.ClientDatabase
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.data.tele_database.entities.ChangeType
import com.telefender.phone.helpers.TeleHelpers
import timber.log.Timber
import java.time.Instant
import java.util.*

object TableInitializers {

    /**
     * Called once to initialize the Instance table during database initialization.
     */
    
    @SuppressLint("MissingPermission")
    suspend fun initInstance(context: Context, database: ClientDatabase, instanceNumber: String) {
        val changeID = UUID.randomUUID().toString()
        val changeTime = Instant.now().toEpochMilli() // get epoch time

        database.changeAgentDao().changeFromClient(
            ChangeLog.create(
                changeID = changeID,
                changeTime = changeTime,
                type = ChangeType.INSTANCE_INSERT,
                instanceNumber = instanceNumber
            ),
            fromSync = false
        )
    }

    /**
     * Called once to initialize the Contact / ContactNumber table during the database's first
     * access. Uses implementation of TableSynchronizer.checkForInserts().
     */
    
    suspend fun initContacts(context: Context, database: ClientDatabase, contentResolver: ContentResolver) {
        if (!TeleHelpers.hasValidStatus(context,
                initializedRequired = true,
                contactPermission = true
            )
        ) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: Invalid status in initContacts()")
            return
        }

        TableSynchronizer.checkForInserts(context, database, contentResolver, firstAccess = true)
    }

    /**
     * Called once to initialize the CallDetail table during the database's first access. Uses
     * implementation of TableSynchronizer.syncCallLogs().
     */
    
    suspend fun initCallDetails(context: Context) {
        if (!TeleHelpers.hasValidStatus(context,
                initializedRequired = true,
                logPermission = true,
            )
        ) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: Invalid status in initCallDetails()")
            return
        }

        val repository = (context.applicationContext as App).repository
        TableSynchronizer.syncCallLogs(context, repository, context.contentResolver)
    }
}