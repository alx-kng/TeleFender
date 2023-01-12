package com.telefender.phone.data.tele_database

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.telefender.phone.data.tele_database.TeleLocks.mutexLocks
import com.telefender.phone.data.tele_database.background_tasks.TableInitializers
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.data.tele_database.background_tasks.workers.OmegaPeriodicScheduler
import com.telefender.phone.data.tele_database.background_tasks.workers.SetupScheduler
import com.telefender.phone.data.tele_database.client_daos.*
import com.telefender.phone.data.tele_database.entities.*
import com.telefender.phone.helpers.DatabaseLogFunctions
import com.telefender.phone.helpers.TeleHelpers
import com.telefender.phone.permissions.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@Database(entities = [
    ChangeLog::class,
    ExecuteQueue::class,
    UploadChangeQueue::class,
    UploadAnalyzedQueue::class,
    StoredMap::class,
    Parameters::class,
    CallDetail::class,
    Instance::class,
    Contact::class,
    ContactNumber::class,
    AnalyzedNumber::class
], version = 1, exportSchema = false)
abstract class ClientDatabase : RoomDatabase() {

    abstract fun changeAgentDao() : ChangeAgentDao
    abstract fun uploadAgentDao() : UploadAgentDao
    abstract fun executeAgentDao() : ExecuteAgentDao

    abstract fun uploadChangeQueueDao() : UploadChangeQueueDao
    abstract fun uploadAnalyzedQueueDao() : UploadAnalyzedQueueDao

    abstract fun changeLogDao() : ChangeLogDao
    abstract fun executeQueueDao() : ExecuteQueueDao
    abstract fun storedMapDao() : StoredMapDao
    abstract fun parametersDao() : ParametersDao

    abstract fun callDetailDao() : CallDetailDao
    abstract fun instanceDao() : InstanceDao
    abstract fun contactDao() : ContactDao
    abstract fun contactNumberDao() : ContactNumberDao
    abstract fun analyzedNumberDao() : AnalyzedNumberDao

    private class ClientDatabaseCallback(
        private val scope: CoroutineScope,
        val context: Context,
        val contentResolver: ContentResolver
    ) : RoomDatabase.Callback() {

        /**
         * Note that onCreate() is only called the VERY FIRST time that the database is created
         * (basically, the first time the app is downloaded and opened). Additionally, if the user
         * leaves during the middle of onCreate(), the rest of the code / database stuff will not
         * continue. So, waitForInitialization(), waitForSetup(), waitForFirebase() are restarted
         * inside getDatabase(), which is called everytime the app is freshly opened (no task).
         */
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: INSIDE DATABASE CALLBACK")
            INSTANCE?.let { database ->
                scope.launch {
                    firstTimeAccess = true

                    database.waitForLogPermissions(context, "CALLBACK")

                    database.initCoreDatabase(context)
                    database.waitForInitialization(context, scope)

                    // User setup goes before rest of database, because that may take a long time.
                    database.userSetup(context)

                    // TODO: Fix Firebase
//                    database.initFirebase()

                    database.initRestOfDatabase(context)

                    /*
                     Unfortunately, doing setup before results in the waiter in getDatabase() to
                     be lifted first, but rest of database should still be initialized during the
                     time of the omega worker. Also, even if rest of database doesn't finish, it
                     should be taken care of during sync.
                     */
                    firstTimeAccess = false
                }
            }
        }
    }

    /**
     * Makes sure that most important part of database is initialized (particularly, the
     * instance and stored map tables). The instance table stores the foreign key to the contact
     * and contact number tables, so it's crucial that the instance table is created first. The
     * stored map table stores the initialization status of the database as well as other important
     * key-value pair data (e.g., server request key, firebase token).
     */
    protected suspend fun initCoreDatabase(context: Context) {
        initializationRunning = true

        if (!Permissions.hasLogPermissions(context)) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: No log permissions in initCoreDatabase()")
            return
        }

        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: initCoreDatabase()")

        val instanceNumber = TeleHelpers.getUserNumberUncertain(context) ?: return

        // Makes sure that retrieved instanceNumber isn't invalid (due to errors or something).
        if (instanceNumber != TeleHelpers.UNKNOWN_NUMBER) {
            // Initializes stored map table with user number. Lock for extra safety.
            mutexLocks[MutexType.STORED_MAP]!!.withLock {
                this.storedMapDao().initStoredMap(instanceNumber)
            }

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: " +
                "Initial StoredMap - ${this.storedMapDao().getStoredMap()?.userNumber}")

            // Inserts the single user instance with changeAgentDao
            TableInitializers.initInstance(context, this, instanceNumber)

            // Set databaseInitialized status in StoredMap table if Instance successfully inserted.
            if (this.instanceDao().hasInstance(instanceNumber)) {
                this.storedMapDao().updateStoredMap(databaseInitialized = true)
            }
        }

        initializationRunning = false
    }

    protected suspend fun userSetup(context: Context) {
        // Don't continue if initialization wasn't successful.
        if (!isInitialized()) return

        SetupScheduler.initiateSetupWorker(context)
        WorkStates.workWaiter(WorkType.SETUP)
    }

    /**
     * Initializes the contacts, contact numbers, and call logs tables. Requires call log and
     * contact permissions.
     */
    protected suspend fun initRestOfDatabase(context: Context) {
        TableInitializers.initContacts(context, this, context.contentResolver)
        TableInitializers.initCallDetails(context)
    }

    protected fun initFirebase() {
        // Gets firebase token
        Firebase.messaging.token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: Fetching FCM registration token failed %s", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: TOKEN: %s", token ?: "TOKEN WAS NULL")
        })
    }

    /**
     * DANGEROUS! Waits for call log permission before continuing. This can cause an infinite
     * loop if not called at the right location.
     */
    protected suspend fun waitForLogPermissions(context: Context, invokeLocation: String) {
        while (!Permissions.hasLogPermissions(context)) {
            delay(500)
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: INSIDE $invokeLocation | HAS CALL LOG PERMISSION: " +
                "${Permissions.hasLogPermissions(context)}")
        }
    }

    /**
     * Not only waits for core database initialization, but also restarts initialization if it not
     * initialized and no exec worker running.
     */
    private suspend fun waitForInitialization(context: Context, scope: CoroutineScope) {
        while (!isInitialized()) {
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: getDatabase() - DATABASE INITIALIZED = FALSE")
            delay(500)

            /*
             * initializationRunning = true not put in scope so that initializationRunning is
             * up-to-date for check in next while loop iteration.
             */
            if (!initializationRunning && !isInitialized()) {
                initializationRunning = true
                scope.launch {
                    initCoreDatabase(context)
                }
            }
        }
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: getDatabase() - DATABASE INITIALIZED = TRUE")
    }

    /**
     * Checks if database is initialized.
     */
    private suspend fun isInitialized() : Boolean {
        return this.storedMapDao().databaseInitialized()
    }

    /**
     * Not only waits for user setup, but also restarts setup if user is not setup and
     * no setup worker running.
     *
     * The worry is that in between the time of the while loop check and if check, isSetup()
     * may become true and worker state may be reset to null. However, we don't need to recheck
     * isSetup() before restart block, since that will be checked inside doWork() of worker, and
     * we know in this edge case that the setup worker is either not running or is successful.
     *
     * Also, we don't need to consider case where isSetup() is false but worker state is null,
     * because the setup status is changed in the database before the worker state is changed.
     */
    private suspend fun waitForSetup(context: Context) {
        while (!isSetup()) {
            delay(500)
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: getDatabase() - USER SETUP = FALSE")

            if (WorkStates.getState(WorkType.SETUP) == null) {
                SetupScheduler.initiateSetupWorker(context)
            }
        }
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: getDatabase() - USER SETUP = TRUE")
    }

    /**
     * Checks if user is setup.
     */
    private suspend fun isSetup() : Boolean {
        return this.storedMapDao().getStoredMap()?.clientKey != null
    }

    companion object {

        // Used to stall Omega workers until initRestOfDatabase() finishes on first ever access.
        var firstTimeAccess = false

        // true if initCoreDatabase() is running.
        var initializationRunning = false

        // Singleton prevents multiple instances of database from opening at the same time.
        @Volatile
        private var INSTANCE: ClientDatabase? = null

        /**
         * TODO: YOU LISTEN TO ME RIGHT NOW!!! FIND OUT WHAT HAPPENS IF YOU CALL BEFORE USER SETUP
         *  FINISHES AND THE SYNC OBSERVER IS LAUNCHED. FROM PRELIMINARY TESTING, IT SEEMS LIKE
         *  THE DATABASE CAN STILL BE ACCESSED EVEN BEFORE THE SETUP / getDatabase() returns.
         *  MAYBE IT'S CALLING A getDatabase() a SECOND TIME AND SOMEHOW RECEIVING THE INSTANCE???
         *  ALSO, WHAT'S WORSE IS WHAT HAPPENS IF YOU GET A CALL BEFORE THE INITIALIZATION
         *  FINISHES, WHAT THEN???? IF IT'S POSSIBLE, THEN MAKE SURE TO INITIALIZATION GUARD ALL
         *  POSSIBLE PLACES WHERE TELE DATABASE USAGE CAN BE INITIATED!!!
         *
         * TODO: Do sync somewhere on reentering app (or maybe don't because of periodic worker),
         *  since getDatabase() isn't always called.
         *
         * TODO: Probably need more permission checks in workers just in case permission changes
         *  mid way. -> Do permission checks at crucial places like default queries and instance
         *  number retrieval (uncertain).
         *
         * TODO: REDO IMPORTANT TRANSACTIONS THAT FAIL!!!! -> Think we've mostly covered everything,
         *  but double check.
         *
         * Either creates new database instance if there is no initialized one available or returns
         * the initialized instance that already exists. Called everytime the app is freshly
         * opened (no task).
         */
        @SuppressLint("MissingPermission")
        @RequiresApi(Build.VERSION_CODES.O)
        fun getDatabase(
            context: Context,
            scope: CoroutineScope,
            contentResolver: ContentResolver
        ): ClientDatabase {

            // if the INSTANCE is not null, return it; otherwise, create the database
            val instanceTemp = INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClientDatabase::class.java,
                    "client_database"
                ).addCallback(ClientDatabaseCallback(scope, context, contentResolver)).build()
                INSTANCE = instance
                // return instance
                instance
            }

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: GET DATABASE CALLED!")

            runBlocking {
                scope.launch {
                    instanceTemp.waitForLogPermissions(context, "getDatabase()")

                    // TODO: Probably need to restart firebase initialization process too.
                    // Waits for / restarts core database initialization and user setup.
                    instanceTemp.waitForInitialization(context, scope)
                    instanceTemp.waitForSetup(context)

                    // Wait for other possible initialization.
                    while (firstTimeAccess) {
                        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: Waiting for rest of database!")
                        delay(500)
                    }

                    OmegaPeriodicScheduler.initiateOneTimeOmegaWorker(context)
                    WorkStates.workWaiter(WorkType.ONE_TIME_OMEGA)

                    // Initialize Omega Periodic Worker (sync, download, execute, upload)
                    OmegaPeriodicScheduler.initiatePeriodicOmegaWorker(context)

                    DatabaseLogFunctions.logSelect(instanceTemp, null, listOf(0, 1, 2, 3, 4, 5, 6))
                }
            }

            return instanceTemp
        }
    }
}

