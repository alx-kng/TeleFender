package com.dododial.phone.data.dodo_database

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkInfo
import com.dododial.phone.data.dodo_database.background_tasks.TableInitializers
import com.dododial.phone.data.dodo_database.background_tasks.WorkerStates
import com.dododial.phone.data.dodo_database.background_tasks.workers.*
import com.dododial.phone.data.dodo_database.client_daos.*
import com.dododial.phone.permissions.PermissionsRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.dododial.phone.data.dodo_database.entities.*
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import timber.log.Timber

@Database(entities = [
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
], version = 1, exportSchema = false)
abstract class ClientDatabase : RoomDatabase() {

    abstract fun instanceDao() : InstanceDao
    abstract fun callLogDao() : CallLogDao
    abstract fun changeAgentDao() : ChangeAgentDao
    abstract fun changeLogDao() : ChangeLogDao
    abstract fun uploadAgentDao() : UploadAgentDao
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


        //TODO Note that sync is not always called, as onCreate is only called on entering the app,
        // so changes mid app lifecycle to contacts will not be caught.

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            Timber.i("DODODEBUG: INSIDE DATABASE CALLBACK")
            INSTANCE?.let { database ->
                scope.launch {
                    while (context.getSystemService(TelecomManager::class.java).defaultDialerPackage != context.packageName
                        || !PermissionsRequester.hasPermissions(context, arrayOf(Manifest.permission.READ_CALL_LOG))) {
                        delay(500)
                        Timber.i("DODODEBUG: INSIDE COROUTINE | HAS CALL LOG PERMISSION: %s", PermissionsRequester.hasPermissions(context, arrayOf(Manifest.permission.READ_CALL_LOG)))
                    }

                    Timber.e("DODODEBUG:", "CAME INTO ClientDatabaseCallback onCreate()")

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
                        Timber.i("DODODEBUG: EXECUTE WORK STATE: %s", WorkerStates.oneTimeExecState.toString())
                    }
                    WorkerStates.oneTimeExecState = null

                    // Set databaseInitialized status in Instance table
                    val tMgr = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                    val instanceNumber = MiscHelpers.cleanNumber(tMgr.line1Number)

                    database.instanceDao().updateInstanceInitialized(instanceNumber!!, true)

                    // UserSetup
                    SetupScheduler.initiateSetupWorker(context)

                    while(WorkerStates.setupState != WorkInfo.State.SUCCEEDED) {
                        delay(500)
                        Timber.i("DODODEBUG: SETUP WORK STATE: %s", WorkerStates.setupState.toString())
                    }
                    WorkerStates.setupState = null

                    // Gets firebase token
                    Firebase.messaging.token.addOnCompleteListener(OnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            Timber.e("DODODEBUG: Fetching FCM registration token failed %s", task.exception)
                            return@OnCompleteListener
                        }

                        // Get new FCM registration token
                        val token = task.result
                        Timber.i("DODODEBUG: TOKEN: %s", token ?: "TOKEN WAS NULL")
                    })

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
        @SuppressLint("MissingPermission")
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

            Timber.i("DODODEBUG: GET DATABASE CALLED!")

            runBlocking {
                scope.launch {

                    while (context.getSystemService(TelecomManager::class.java).defaultDialerPackage != context.packageName
                        || !PermissionsRequester.hasPermissions(context, arrayOf(Manifest.permission.READ_CALL_LOG))) {
                        delay(1000)
                        Timber.i("DODODEBUG: INSIDE getDatabase() | Waiting for permissions")
                    }

//                    OmegaPeriodicScheduler.cancelOneTimeOmegaWorker(context)
//                    OmegaPeriodicScheduler.cancelPeriodicOmegaWorker(context)

                    val tMgr = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                    val instanceNumber = MiscHelpers.cleanNumber(tMgr.line1Number) // get phone # of user

                    // Checks if database is fully initialized
                    var isInitialized = instanceTemp.instanceDao().hasInstance()
                        && instanceTemp.instanceDao().isInitialized(instanceNumber!!)

                    while (!isInitialized) {
                        delay(500)
                        Timber.i("DODODEBUG: INSIDE GET DATABASE COROUTINE. DATABASE INITIALIZED = %s", isInitialized)

                        isInitialized = instanceTemp.instanceDao().hasInstance()
                            && instanceTemp.instanceDao().isInitialized(instanceNumber!!)
                    }

                    var isSetup = instanceTemp.keyStorageDao().hasCredKey(instanceNumber!!)

                    while (!isSetup) {
                        delay(500)
                        Timber.i("DODODEBUG: INSIDE GET DATABASE COROUTINE. USER SETUP = %s", isSetup)

                        isSetup = instanceTemp.keyStorageDao().hasCredKey(instanceNumber)
                    }


                    Timber.i("DODODEBUG: BEFORE ONE TIMES")
                    OmegaPeriodicScheduler.initiateOneTimeOmegaWorker(context)

                    while(WorkerStates.oneTimeOmegaState != WorkInfo.State.SUCCEEDED) {
                        if (WorkerStates.oneTimeOmegaState == WorkInfo.State.FAILED
                            || WorkerStates.oneTimeOmegaState == WorkInfo.State.CANCELLED
                            || WorkerStates.oneTimeOmegaState == WorkInfo.State.BLOCKED) {
                            Timber.i("DODODEBUG: WORKER STATE BAD")
                            break
                        }
                        delay(500)
                        Timber.i("DODODEBUG: ONE TIME OMEGA WORK STATE: %s", WorkerStates.oneTimeOmegaState.toString())
                    }
                    WorkerStates.oneTimeOmegaState = null

                    Timber.i("DODODEBUG: AFTER ONE TIMES")

                    // Initialize Omega Periodic Worker (sync, download, execute, upload)
                    OmegaPeriodicScheduler.initiatePeriodicOmegaWorker(context)
                }
            }

            return instanceTemp

        }
    }
}
