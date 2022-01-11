package com.dododial.phone.database

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkInfo
import com.dododial.phone.database.background_tasks.TableInitializers
import com.dododial.phone.database.background_tasks.TableSynchronizer
import com.dododial.phone.database.background_tasks.WorkScheduler
import com.dododial.phone.database.background_tasks.WorkerStates
import com.dododial.phone.database.client_daos.*
import com.example.actualfinaldatabase.permissions.PermissionsRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.dododial.phone.database.entities.*
import java.util.*

@Database(entities = arrayOf(
    CallLog::class,
    KeyStorage::class,
    Instance::class,
    Contact::class,
    ContactNumbers::class,
    TrustedNumbers::class,
    Organizations::class,
    Miscellaneous::class,
    ChangeLog::class,
    QueueToExecute::class,
    QueueToUpload::class
), version = 1, exportSchema = false)
public abstract class ClientDatabase : RoomDatabase() {

    abstract fun callLogDao() : CallLogDao
    abstract fun changeAgentDao() : ChangeAgentDao
    abstract fun uploadAgentDao() : UploadAgentDao
    abstract fun changeLogDao() : ChangeLogDao
    abstract fun executeAgentDao() : ExecuteAgentDao
    abstract fun keyStorageDao() : KeyStorageDao
    abstract fun queueToExecuteDao() : QueueToExecuteDao
    abstract fun queueToUploadDao() : QueueToUploadDao
    abstract fun contactDao() : ContactDao
    abstract fun contactNumbersDao() : ContactNumbersDao

    private class ClientDatabaseCallback(
        private val scope: CoroutineScope,
        val context: Context,
        val contentResolver: ContentResolver
    ) : RoomDatabase.Callback() {

        @SuppressLint("LogNotTimber")
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            Log.i("DODODEBUG: ", "INSIDE DATABASE CALLBACK")
            INSTANCE?.let { database ->
                scope.launch {
                    while (context.getSystemService(TelecomManager::class.java).defaultDialerPackage != context.packageName
                        || !PermissionsRequester.hasPermissions(context, arrayOf(Manifest.permission.READ_CALL_LOG))) {
                        delay(500)
                        Log.i("DODODEBUG: ", "INSIDE COROUTINE | HAS CALL LOG PERMISSION: "  + PermissionsRequester.hasPermissions(context, arrayOf(Manifest.permission.READ_CALL_LOG)))
                    }
                    // Goes through each call log and inserts to db
                    TableInitializers.initCallLog(context, database)

                    // Inserts the single user instance with changeAgentDao
                    TableInitializers.initInstance(context, database)

                    // Goes through contacts and inserts contacts (and corresponding numbers) into db
                    TableInitializers.initContact(context, database, contentResolver)

                    // Goes through contact numbers and inserts numbers into db
                    TableInitializers.initContactNumber(context, database, contentResolver)

                    // Initialize other tables

                    WorkScheduler.initiateOneTimeExecuteWorker(context)
                    while(WorkerStates.initExecState != WorkInfo.State.SUCCEEDED) {

                        if (WorkerStates.initExecState == WorkInfo.State.FAILED
                            || WorkerStates.initExecState == WorkInfo.State.CANCELLED
                            || WorkerStates.initExecState == WorkInfo.State.BLOCKED) {
                            Log.i("DODODEBUG: ", "WORKER STATE BAD")
                            break
                        }
                        delay(500)
                        Log.i("DODODEBUG: ", "WORK STATE: " + WorkerStates.initExecState.toString())
                    }

                    val contacts = database.contactDao().getAllContacts()
                    Log.i("DODODEBUG: CONTACT SIZE: ", contacts.size.toString())
                    for (contact in contacts) {
                        Log.i("DODODEBUG: ", contact.toString())
                    }

                    val contactNumbers : List<ContactNumbers> = database.contactNumbersDao().getAllContactNumbers()
                    Log.i("DODODEBUG: CONTACT NUMBERS SIZE: ", contactNumbers.size.toString())
                    for (contactNumber in contactNumbers) {
                        Log.i("DODODEBUG: ", contactNumber.toString())
                    }

                    val changeLogs = database.changeLogDao().getAllChangeLogs()
                    Log.d("DODODEBUG: CHANGE LOG SIZE: ", changeLogs.size.toString())
//                    for (changeLog : ChangeLog in changeLogs) {
//                        Log.d("DODODEBUG: CHANGE LOG: ", changeLog.toString())
//
//                    }
//
                    val executeLogs = database.queueToExecuteDao().getAllQTEs()
                    Log.d("DODODEBUG: EXECUTE LOG SIZE: ", executeLogs.size.toString())
//                    for (executeLog : QueueToExecute in executeLogs) {
//                        Log.d("DODODEBUG: EXECUTE LOG: ",database.changeLogDao().getChangeLogRow(executeLog.changeID).toString())
//
//                    }
//                    val uploadLogs = database.queueToUploadDao().getAllQTU()
//                    Log.d("DODODEBUG: UPLOAD LOG SIZE: ", uploadLogs.size.toString())
//                    for (uploadLog : QueueToUpload in uploadLogs) {
//                        Log.d("DODODEBUG: UPLOAD LOG: ", uploadLog.toString())
//
//                    }
//
                    val callLogs = database.callLogDao().getCallLogs()
                    for (callLog: CallLog in callLogs) {
                        Log.i("DODODEBUG: CALL LOG: ", callLog.number + " " + callLog.callType + " "
                            + callLog.callEpochDate + " " + callLog.callDirection + " " + callLog.callLocation)
                    }
                    Log.i("DODODEBUG: ", "INITIALIZED")
                    initialized = true
                }
            }
        }
    }

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: ClientDatabase? = null

        private var initialized = false

        /**
         * Either creates new database instance if there is no initialized one available or returns
         * the initialized instance that already exists.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        fun getDatabase(
            context: Context,
            scope: CoroutineScope,
            contentResolver: ContentResolver
        ): ClientDatabase {


            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            val instanceTemp =  INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClientDatabase::class.java,
                    "client_database"
                ).addCallback(ClientDatabaseCallback(scope, context, contentResolver)).build()
                INSTANCE = instance
                // return instance
                instance
            }

            runBlocking {
                scope.launch {
                    while (initialized != true) {
                        delay(500)
                        Log.i("DODODEBUG: ", "INSIDE SYNC COROUTINE NOT INITIALIZED")
                    }
                    if (!instanceTemp.executeAgentDao().hasQTEs()) {
                        Log.i("DODODEBUG:", "SYNC STARTED")
                        TableSynchronizer.syncContacts(context, instanceTemp, contentResolver)
                        TableSynchronizer.syncCallLogs(context, instanceTemp, contentResolver)
                        Log.i("DODODEBUG:", "SYNC ENDED")
                    }
                }
            }

            // TODO something with periodic work

            return instanceTemp

        }
    }
}

private fun <T> LiveData<T>.observe(coroutineScope: CoroutineScope, observer: Observer) {

}
