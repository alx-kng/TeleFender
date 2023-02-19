package com.telefender.phone.data.tele_database

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.TeleLocks.mutexLocks
import com.telefender.phone.data.tele_database.background_tasks.TableInitializers
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.data.tele_database.background_tasks.workers.DebugScheduler
import com.telefender.phone.data.tele_database.background_tasks.workers.OmegaScheduler
import com.telefender.phone.data.tele_database.background_tasks.workers.SetupScheduler
import com.telefender.phone.data.tele_database.client_daos.*
import com.telefender.phone.data.tele_database.entities.*
import com.telefender.phone.helpers.DatabaseLogger
import com.telefender.phone.helpers.PrintTypes
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
    ErrorQueue::class,
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
    abstract fun errorQueueDao() : ErrorQueueDao

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
         * TODO: Double check callback exit behavior. Particularly, check if there is some other
         *  possible event that can cause the callback to preemptively exit, as we need to know
         *  if firstTimeAccess can cause an infinite loop if not set back to false. For example,
         *  if the callback ONLY exits when the app also closes, then we can assure that
         *  firstTimeAccess is initialized back to false (due to being an object variable).
         *
         * Note that onCreate() is only called the VERY FIRST time that the database is created
         * (basically, the first time the app is downloaded and opened). Additionally, if the user
         * leaves (app closes) during the middle of onCreate(), the rest of the code / database
         * stuff will not continue. So, waitForInitialization(), waitForSetup(), waitForFirebase()
         * are restarted inside getDatabase(), which is called everytime the app is freshly opened
         * (no task).
         *
         * NOTE: According to my current understanding, the callback is ONLY exited if the app is
         * exited. This means that events like calls and the starting of other activities shouldn't
         * affect the callback.
         */
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: INSIDE DATABASE CALLBACK")
            INSTANCE?.let { database ->
                scope.launch {
                    firstTimeAccess = true

                    database.waitForCorePermissions(context, "CALLBACK     ")

                    database.initCoreDatabase(context)
                    database.waitForInitialization(context, scope)

                    /*
                    User setup goes before rest of database, because that may take a long time.
                    Note that userSetup() uses a waiter, so everything after it should be setup.
                     */
                    database.userSetup(context)

                    // Rest of database initialization only requires that the core is initialized.
                    database.initRestOfDatabase(context)

                    // Firebase token retrieval and subsequent server upload requires user setup.
                    database.initFirebase(context)

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

        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: initCoreDatabase()")

        val userNumber = TeleHelpers.getUserNumberUncertain(context) ?: return

        // Makes sure that retrieved instanceNumber isn't invalid (due to errors or something).
        if (userNumber != TeleHelpers.UNKNOWN_NUMBER) {

            // Initializes StoredMap table with user number. Lock for extra safety.
            mutexLocks[MutexType.STORED_MAP]!!.withLock {
                this.storedMapDao().initStoredMap(userNumber)
            }

            // Initializes Parameters table with user number. Lock for extra safety.
            mutexLocks[MutexType.PARAMETERS]!!.withLock {
                this.parametersDao().initParameters(userNumber)
            }

            // Inserts the single user instance with ChangeAgentDao
            TableInitializers.initInstance(context, this, userNumber)
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

    /**
     * TODO: NOTE THAT WE WILL ACTUALLY NOT BE USING PUSH NOTIFICATIONS FOR NOW DUE TO ITS
     *  LIMITATIONS.
     *
     * TODO: Note that the Firebase token isn't crucial to user-flow, as the token is mainly used
     *  for SMS_VERIFY verification of incoming calls. If we don't have token, then just don't request
     *  SMS_VERIFY verification from server.
     *
     * TODO: Actually do upload. Put in better token insert flow to account for whether server
     *  received it or not.
     *
     * Retrieves current Firebase token. The initial uploading of the token / any additional actions
     * also happen here.
     *
     * NOTE: Firebase.messaging.token actually just retrieves the current registration token.
     * It doesn't actually "request" a new token. New token requests are pretty much done
     * automatically on app start without our control (unless we prefer the token to not
     * autogenerate, which would require a few manifest changes). So, this pattern can be reused.
     */
    protected suspend fun initFirebase(context: Context) {
        Firebase.messaging.token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: " +
                    "Fetching FCM registration token failed ${task.exception}")
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: TOKEN: $token")

            (context.applicationContext as App).applicationScope.launch {
                this@ClientDatabase.storedMapDao().updateStoredMap(firebaseToken = token)
            }
        })
    }

    /**
     * Checks if user is setup.
     */
    private suspend fun hasFirebaseToken() : Boolean {
        return this.storedMapDao().getStoredMap()?.firebaseToken != null
    }

    /**
     * DANGEROUS! Waits for CALL_LOG and PHONE_STATE permissions before continuing. This can cause
     * an infinite loop if not called at the right location.
     */
    protected suspend fun waitForCorePermissions(context: Context, invokeLocation: String) {
        while (!Permissions.hasLogPermissions(context)
            || !Permissions.hasPhoneStatePermissions(context)
        ) {
            delay(500)
            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: INSIDE $invokeLocation | " +
                "HAS CALL LOG PERMISSION: ${Permissions.hasLogPermissions(context)} | " +
                "HAS PHONE STATE PERMISSION: ${Permissions.hasPhoneStatePermissions(context)}")
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
     * Checks if database is initialized. Requires that the singleton StoredMap row exists (which
     * contains the user's number), an Instance row with the user's number exists, and the
     * singleton Parameters row exists.
     */
    private suspend fun isInitialized() : Boolean {
        val userNumber = this.storedMapDao().getStoredMap()?.userNumber
        return userNumber != null
            && this.instanceDao().hasInstance(userNumber)
            && this.parametersDao().getParameters() != null
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

        /**
         * Used to stall Omega workers until initRestOfDatabase() finishes on first ever access.
         * Also, even if rest of database doesn't finish, it should be taken care of during sync.
         */
        private var firstTimeAccess = false

        /**
         * True if initCoreDatabase() is running.
         */
        private var initializationRunning = false

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
         * TODO: PROBLEM WITH PERIODIC OMEGA WORKER STATE INFINITELY RUNNING!
         *
         * Either creates new database instance if there is no initialized one available or returns
         * the initialized instance that already exists. Called everytime the app is freshly
         * opened (no task).
         */
        @SuppressLint("MissingPermission")
        
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
                    instanceTemp.waitForCorePermissions(context, "getDatabase()")

                    // TODO: Probably need to restart firebase initialization process too.
                    // Waits for / restarts core database initialization and user setup.
                    instanceTemp.waitForInitialization(context, scope)
                    instanceTemp.waitForSetup(context)

                    // Wait for other possible initialization.
                    while (firstTimeAccess) {
                        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: Waiting for rest of database!")
                        delay(500)
                    }

                    if (!instanceTemp.hasFirebaseToken()) {
                        instanceTemp.initFirebase(context)
                    }

                    OmegaScheduler.initiateOneTimeOmegaWorker(context)
                    WorkStates.workWaiter(WorkType.ONE_TIME_OMEGA)

                    val loggerJob = DatabaseLogger.omegaLogger(
                        database = instanceTemp,
                        logSelect = listOf(
                            PrintTypes.CALL_LOG,
                            PrintTypes.INSTANCE,
                            PrintTypes.CONTACT,
                            PrintTypes.CONTACT_NUMBER,
                            PrintTypes.CHANGE_LOG,
                            PrintTypes.ERROR_LOG,
                            PrintTypes.EXECUTE_LOG,
                            PrintTypes.UPLOAD_CHANGE,
                            PrintTypes.UPLOAD_ANALYZED,
                        )
                    )

                    // Wait for logger to finish before initiating OmegaPeriodic.
                    loggerJob?.join()

                    // Initialize Omega Periodic Worker (sync, download, execute, upload)
                    OmegaScheduler.initiatePeriodicOmegaWorker(context)
                    WorkStates.workWaiter(WorkType.PERIODIC_OMEGA, "OMEGA PERIODIC WAIT")

                    /*
                    TODO: See if there's a need for a one time debug worker later on.

                    Initialize One Time Debug Worker (currently for testing, as we want to be able
                    to test debug without having to wait 15 min / reinstall app).
                     */
                    DebugScheduler.initiateOneTimeDebugWorker(context)

                    // Initialize Periodic Debug Worker
                    DebugScheduler.initiatePeriodicDebugWorker(context)
                }
            }

            return instanceTemp
        }
    }
}

