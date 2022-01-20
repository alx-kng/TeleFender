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
import com.dododial.phone.database.background_tasks.WorkerStates
import com.dododial.phone.database.background_tasks.background_workers.*
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

    abstract fun instanceDao() : InstanceDao
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
                    ExecuteScheduler.initiateOneTimeExecuteWorker(context)

                    while(WorkerStates.oneTimeExecState != WorkInfo.State.SUCCEEDED) {
                        delay(500)
                        Log.i("DODODEBUG: ", "EXECUTE WORK STATE: " + WorkerStates.oneTimeExecState.toString())
                    }
                    WorkerStates.oneTimeExecState = null

                    // UserSetup
                    SetupScheduler.initiateSetupWorker(context)

                    while(WorkerStates.setupState != WorkInfo.State.SUCCEEDED) {
                        delay(500)
                        Log.i("DODODEBUG: ", "SETUP WORK STATE: " + WorkerStates.setupState.toString())
                    }
                    WorkerStates.setupState = null

                }
            }
        }
    }

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: ClientDatabase? = null

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

            Log.i("DODODEBUG: ", "GET DATABASE CALLED!")

            runBlocking {
                scope.launch {

                    // Checks if database is fully initialized
                    var isInitialized = !instanceTemp.executeAgentDao().hasQTEs()
                        && instanceTemp.instanceDao().hasInstance()

                    // TODO update the is setup to automatically use user's number instead of
                    //  hardcoded value
                    var isSetup = instanceTemp.keyStorageDao().hasCredKey("4436996212")

                    while (!isInitialized || !isSetup) {
                        delay(500)
                        Log.i("DODODEBUG: ", "INSIDE GET DATABASE COROUTINE. DATABASE INITIALIZED = " + isInitialized)
                        Log.i("DODODEBUG: ", "INSIDE GET DATABASE COROUTINE. USER SETUP = " + isSetup)

                        isInitialized = !instanceTemp.executeAgentDao().hasQTEs()
                            && instanceTemp.instanceDao().hasInstance()

                        isSetup = instanceTemp.keyStorageDao().hasCredKey("4436996212")
                    }

                    Log.i("DODODEBUG: ", "BEFORE ONE TIMES")
                    OmegaPeriodicScheduler.initiateOneTimeOmegaWorker(context)

                    while(WorkerStates.oneTimeOmegaState != WorkInfo.State.SUCCEEDED) {
                        if (WorkerStates.oneTimeOmegaState == WorkInfo.State.FAILED
                            || WorkerStates.oneTimeOmegaState == WorkInfo.State.CANCELLED
                            || WorkerStates.oneTimeOmegaState == WorkInfo.State.BLOCKED) {
                            Log.i("DODODEBUG: ", "WORKER STATE BAD")
                            break
                        }
                        delay(500)
                        Log.i("DODODEBUG: ", "ONE TIME OMEGA WORK STATE: " + WorkerStates.oneTimeOmegaState.toString())
                    }
                    WorkerStates.oneTimeOmegaState = null

                    Log.i("DODODEBUG: ", "AFTER ONE TIMES")

                    DatabaseLogFunctions.logContacts(instanceTemp)
                    DatabaseLogFunctions.logContactNumbers(instanceTemp)
                    DatabaseLogFunctions.logChangeLogs(instanceTemp)
                    DatabaseLogFunctions.logExecuteLogs(instanceTemp)
                    DatabaseLogFunctions.logUploadLogs(instanceTemp)

                    // Initialize Omega Periodic Worker (sync, download, execute, upload)
                    OmegaPeriodicScheduler.initiatePeriodicOmegaWorker(context)
                }
            }

            return instanceTemp

        }
    }
}

