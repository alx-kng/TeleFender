package com.telefender.phone.data.tele_database.background_tasks

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import androidx.work.WorkInfo
import com.telefender.phone.data.default_database.DefaultContacts
import com.telefender.phone.data.tele_database.ClientDatabase
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.MutexType
import com.telefender.phone.data.tele_database.TeleLocks.mutexLocks
import com.telefender.phone.data.tele_database.entities.*
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.Instant
import java.util.*


/**
 * TODO: FUCK FUCK FUCK FUCK FUCK FUCK FUCK FUCK. Look out for possible sync bug with contact
 *  number updates (something with duplicates). Probably checking version number wrong???? FUCK
 *  -> look below, think i summarized it. haven't fixed it yet tho.
 */
object TableSynchronizer {

    private const val retryAmount = 3
    private const val checkBackPeriod = 5 * 60000

    /**
     * TODO: Maybe move the default retrieval to DefaultCallDetails
     * TODO: Seems to be some duplicate logs during sync -> look into possible causes.
     *
     * Syncs our CallDetail database with Android's CallDetail database. Returns whether or not
     * the sync successfully finished without errors. Used for both periodic and
     * immediate syncs. Periodic sync is for when user switches to different phone app for a certain
     * amount of time, and immediate sync for after every call (since it's important for our
     * algorithm to update logs immediately).
     *
     * Also, we've confirmed that checkBackPeriod is necessary.
    */
    suspend fun syncCallLogs(context: Context, repository: ClientRepository, contentResolver: ContentResolver) {
        for (i in 1..retryAmount) {
            try {
                syncCallLogsHelper(context, repository, contentResolver)
                break
            } catch (e: Exception) {
                Timber.i("$DBL: syncCallImmediate() RETRYING...")
                delay(2000)
            }
        }
    }


    /**
     * TODO: All of a sudden, this has a problem. FIX YOU FUCKER!!!!
     */
    private suspend fun syncCallLogsHelper(context: Context, repository: ClientRepository, contentResolver: ContentResolver) {
        // Check for permissions even though syncCallLogs() would catch the permission error.
        if (!TeleHelpers.hasValidStatus(
                context,
                setupRequired = false,
                logRequired = true,
                phoneStateRequired = true
            )
        ) {
            Timber.e("$DBL: No log permissions in syncCallLogs()")
            return
        }

        val instanceNumber = TeleHelpers.getUserNumberStored(context)!!
        val lastLogSyncTime = repository.getLastLogSyncTime()!!

        /*
        For retrieving voicemail logs, which have a small chance of being placed slightly before
        the corresponding missed / rejected / blocked call (epochDate wise).
         */
        val checkFromTime = if (lastLogSyncTime == 0L) lastLogSyncTime else lastLogSyncTime - checkBackPeriod

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.GEOCODED_LOCATION,
        )

        val selection = "DATE > ?"

        val curs : Cursor? = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            arrayOf(checkFromTime.toString()),
            CallLog.Calls.DATE + " ASC"
        )

        if (curs != null) {
            while (curs.moveToNext()) {
                val rawNumber = curs.getString(0)
                // If normalizedNumber is null, use bareNumber() cleaning of rawNumber.
                val normalizedNumber = TeleHelpers.normalizedNumber(rawNumber)
                    ?: TeleHelpers.bareNumber(rawNumber)
                val typeInt = curs.getInt(1)
                // Epoch date is in milliseconds and is the creation time.
                val date = curs.getString(2).toLong()
                val duration = curs.getString(3).toLong()
                val location = curs.getString(4)
                val dir = TeleHelpers.getTrueDirection(context, typeInt, rawNumber)

                val callDetail = CallDetail(
                    rawNumber = rawNumber,
                    normalizedNumber = normalizedNumber,
                    callType = dir.toString(),
                    callEpochDate = date,
                    callDuration = duration,
                    callLocation = location,
                    callDirection = dir,
                    instanceNumber = instanceNumber
                )

                val inserted = repository.callFromClient(callDetail)
                if (inserted) {
                    Timber.i("$DBL syncCallLogs(): SYNCED: $callDetail")
                } else {
                    Timber.i("$DBL syncCallLogs(): ALREADY SYNCED: $callDetail")
                }
            }
            curs.close()

            /*
             Used to set the last time the logs were fully synced. That is, all the logs in the
             default database were at least gone through. This allows us to decide whether or not
             the database is up-to-date enough to be used for algorithm.
             */
            repository.updateStoredMap(lastLogFullSyncTime = Instant.now().toEpochMilli())
        }
    }

    suspend fun syncContactsMini(
        context: Context,
        database: ClientDatabase,
        contentResolver: ContentResolver,
    ) {

    }

    /**********************************************************************************************
     * TODO: HAVING THE EXISTING SYNC LOCK MECHANISM (IF YOU ALSO USE IF FOR THE OTHER FUNCTIONS)
     *  MAY MAKE OVERLAPPING SYNCS SAFE. -> Which should we go with?
     *
     * TODO: We seem to very occasionally get a default cnUpdate() for every contact number?
     *  Specifically, everytime the app is run (on some instances) all the contact numbers are
     *  duplicate inserted. There's probably something wrong with the sync process. -> Think it
     *  might be caused by our test contact insertions.
     *  ->
     *  Here's the problem. When version number is upped, sometimes the rawNumber isn't actually
     *  changed. However, in cnUpdate() since we just return if we see that the rawNumber is the
     *  same (since we think it's a duplicate), the versionNumber never gets updated. As a result,
     *  it continues to get the cnUpdate() execution command, as we never made the versionNumber
     *  current.
     *  ->
     *  Verify whether this has been fixed
     *
     * TODO: RETRIEVE DEFAULT CONTACT BY DEFAULT CID USING SAME METHOD AS syncCallLogs(). Also,
     *  summarize in document.
     *
     * TODO: Need to include default blocked update.
     *
     * Syncs our Contact / ContactNumber database with Android's default Contact / PhoneNumbers
     * database.
     *
     * Idea is that we iterate through our database and see if any changes to corresponding rows in
     * default database (checks for updates and deletes), and then we iterate through the default
     * database and see if the corresponding rows exist in our database (checks for inserts).
     *
     * NOTE: When the algorithm determines that an insert / update / delete is in order, we double
     * check the default database and change our database using one mutexSync. The basic idea is
     * this: if we see that the default database has a number that the tele database doesn't, then
     * we would normally think that an insert is in order. However, before we actually insert, we
     * will double check the default database to see if that number is still in the database or
     * not (it may no longer be in the default database due to a separate client side delete). If
     * it still is in the database, then will continue with the insert into our tele database.
     * Additionally, we don't have to worry about repeats of the same operation from happening
     * (e.g., sync orders a delete and a separate client side process orders a delete) since they
     * will be handled at a lower level in ExecuteAgentDao. Syncing deletes mirrors syncing inserts.
     *
     * NOTE: syncContacts() doesn't sync / add in default database contacts without
     * associated contact numbers. In TableInitializers, initContacts() DOES add in contacts
     * without numbers to our Tele database. However, this should not be an issue to the algorithm,
     * as only contacts with contact numbers affect the algorithm.
     *
     * NOTE: At most three SYNC_CONTACTS processes should be competing with each other at any given
     * time. Specifically, the only time SYNC_CONTACTS processes should need to wait for each other
     * is when the user initiated mini-sync and full sync (either ONE_TIME or PERIODIC or both)
     * overlap with each other.
    ***********************************************************************************************/
    
    suspend fun syncContacts(
        context: Context,
        database: ClientDatabase,
        contentResolver: ContentResolver
    ) {
        // Waits for any possible duplicate SYNC_CONTACTS processes to finish before continuing.
        val workInstanceKey = ExperimentalWorkStates.localizedCompete(
            workType = WorkType.SYNC_CONTACTS,
            runningMsg = "SYNC_CONTACTS",
            newWorkInstance = true
        )

        /*
        No need to check for contact permissions here because getContactNumberCursor() is already
        permission guarded.
         */
        for (i in 1..retryAmount) {
            // When retrying, the instance re-competes for the RUNNING state if not already RUNNING.
            ExperimentalWorkStates.localizedCompete(
                workType = WorkType.SYNC_CONTACTS,
                runningMsg = "SYNC_CONTACTS - syncContacts()",
                workInstanceKey = workInstanceKey
            )

            try {
                syncContactsHelper(context, database, contentResolver)
                ExperimentalWorkStates.localizedRemoveState(
                    workType = WorkType.SYNC_CONTACTS,
                    workInstanceKey = workInstanceKey
                )
                break
            } catch (e: Exception) {
                if (i < retryAmount) {
                    Timber.i("$DBL: syncContacts() RETRYING...")
                    delay(2000)
                } else {
                    ExperimentalWorkStates.localizedSetStateKey(
                        workType = WorkType.SYNC_CONTACTS,
                        workState = WorkInfo.State.FAILED,
                        workInstanceKey = workInstanceKey
                    )
                }
            }
        }
    }

    
    private suspend fun syncContactsHelper(
        context: Context,
        database: ClientDatabase,
        contentResolver: ContentResolver
    ) {
        val defaultContactHashMap = checkForInserts(context, database, contentResolver)
        checkForUpdatesAndDeletes(context, database, defaultContactHashMap, contentResolver)

        /*
         Used to set the last time the contacts were fully synced. That is, all the contacts in
         the default database were at least gone through. This allows us to decide whether or not
         the database is up-to-date enough to be used for algorithm.
         */
        database.storedMapDao().updateStoredMap(lastContactFullSyncTime = Instant.now().toEpochMilli())
    }

    /**
     * TODO: New sync safety logic already put in, but double check.
     * TODO: Maybe return / throw error if cursor is null.
     *
     * Deals with any potential insertions into the Android default database and updates ours, as
     * well as returning a HashMap of all ContactNumber for use in checking for updates and deletes
    */
    
    @SuppressLint("MissingPermission")
    suspend fun checkForInserts(
        context: Context,
        database: ClientDatabase,
        contentResolver : ContentResolver,
        firstAccess: Boolean = false
    ) : HashMap<String, MutableList<ContactNumber>> {

        val instanceNumber = TeleHelpers.getUserNumberStored(context)!!
        val mutexSync = mutexLocks[MutexType.SYNC]!!
        val curs: Cursor? = DefaultContacts.getContactNumberCursor(context, contentResolver)

        /**
         * We need to create a hash map of all the default database contact numbers (using the
         * CID as key) so that we can quickly find the correct rows / whether a row exists
         */
        val defaultContactHashMap = HashMap<String, MutableList<ContactNumber>>()

        if (curs == null) {
            Timber.i("$DBL: Contact Number cursor is null; BAD")
        } else {
            Timber.e("$DBL: Inside table synchronizer")
            while (!curs.isAfterLast) {
                /**
                 * Need to turn default CID into UUID version of CID since default CID is not the
                 * same as the CIDs we store in our Contacts / ContactNumber tables
                 */
                val defaultCID = curs.getString(0)
                val teleCID = TeleHelpers.defaultCIDToTeleCID(defaultCID, instanceNumber)
                val rawNumber = curs.getString(1)
                val normalizedNumber = curs.getString(2)
                    ?: TeleHelpers.normalizedNumber(rawNumber)
                    ?: TeleHelpers.bareNumber(rawNumber)
                val versionNumber = curs.getString(3).toInt()

                // Corresponding contact numbers (by CID) in our database
                val matchCID: List<ContactNumber> = database.contactNumberDao().getContactNumbersByCID(teleCID)

                // Corresponding contact numbers (by PK) in our database
                val matchPK: ContactNumber? = database.contactNumberDao().getContactNumberRow(teleCID, normalizedNumber)

                /**
                 * If no ContactNumber have the same CID, that means the corresponding contact
                 * doesn't even exist and thus needs to be inserted into our Contacts table.
                 * However, as mentioned in the main comment of syncContacts(), we need to double
                 * check that the contact still exists in the default database and wasn't deleted
                 * by something other client-side delete query before we continue with the insert.
                 * Lock is explained in changeFromClient() of ChangeAgentDao.
                 */
                if (matchCID.isEmpty()) {
                    mutexSync.withLock {
                        if (firstAccess || DefaultContacts.aggregateContactExists(contentResolver, defaultCID)) {
                            val changeID = UUID.randomUUID().toString()
                            val changeTime = Instant.now().toEpochMilli()

                            val change = Change.create(
                                CID = teleCID
                            )

                            database.changeAgentDao().changeFromClient(
                                ChangeLog.create(
                                    changeID = changeID,
                                    changeTime = changeTime,
                                    type = ChangeType.CONTACT_INSERT,
                                    instanceNumber = instanceNumber,
                                    changeJson = change.toJson()
                                ),
                                fromSync = true,
                                bubbleError = true
                            )
                        }
                    }
                }

                /**
                 * If no ContactNumber have the same CID and Number (PK), that means the corresponding
                 * contact number doesn't exist and thus needs to be inserted into the ContactNumber table.
                 * There's a similar double check before insert in the mutexSync lock as in the
                 * block above for contact inserts.
                 */
                if (matchPK == null) {
                    mutexSync.withLock {
                        if (firstAccess || DefaultContacts.contactNumberExists(contentResolver, defaultCID, rawNumber)) {
                            val changeID = UUID.randomUUID().toString()
                            val changeTime = Instant.now().toEpochMilli()

                            val change = Change.create(
                                CID = teleCID,
                                normalizedNumber = normalizedNumber,
                                defaultCID = defaultCID,
                                rawNumber = rawNumber,
                                degree = 0,
                                counterValue = versionNumber
                            )

                            database.changeAgentDao().changeFromClient(
                                ChangeLog.create(
                                    changeID = changeID,
                                    changeTime = changeTime,
                                    type = ChangeType.CONTACT_NUMBER_INSERT,
                                    instanceNumber = instanceNumber,
                                    changeJson = change.toJson()
                                ),
                                fromSync = true,
                                bubbleError = true
                            )
                        }
                    }
                }

                // No need add to HashMap if from first database access, as won't look at deletes.
                if (firstAccess) {
                    curs.moveToNext()
                    continue
                }

                val contactNumber = ContactNumber(
                    CID = teleCID,
                    normalizedNumber = normalizedNumber,
                    defaultCID = defaultCID,
                    rawNumber = rawNumber,
                    instanceNumber = instanceNumber,
                    versionNumber = versionNumber,
                    degree = 0
                )

                // get may return null if key doesn't yet have a list initialized with it
                if (defaultContactHashMap[teleCID] == null) {
                    defaultContactHashMap[teleCID] = mutableListOf(contactNumber)
                } else {
                    defaultContactHashMap[teleCID]!!.add(contactNumber)
                }

                curs.moveToNext()
            }
            curs.close()
        }
        Timber.e("$DBL: AFTER SYNC INSERTS")
        // Contains all CIDs and their lists of contact numbers from default database (in our format)
        return defaultContactHashMap
    }

    /**
     * TODO: Is it possible that the DELC might happen twice if there are two ContactNumbers under
     *  same CID. -> think it might be fixed, double check
     *
     * TODO: Double check logic.
     * TODO: Handle case where multiple contact numbers with same PK. -> might not be possible
     */
    private suspend fun checkForUpdatesAndDeletes(
        context: Context,
        database: ClientDatabase,
        defaultContactHashMap: HashMap<String, MutableList<ContactNumber>>,
        contentResolver: ContentResolver
    )  {
        val instanceNumber = TeleHelpers.getUserNumberStored(context)!!
        val teleCN: List<ContactNumber> = database.contactNumberDao().getContactNumbersByIns(instanceNumber)
        val mutexSync = mutexLocks[MutexType.SYNC]!!

        for (contactNumber: ContactNumber in teleCN) {

            val defaultCID = contactNumber.defaultCID
            val teleCID = contactNumber.CID

            // Corresponding contact numbers (by CID) in android default database can be null or empty?
            val matchCID: MutableList<ContactNumber>? = defaultContactHashMap[teleCID]

            /**
             * Means the corresponding entire contact (in the default database) has been deleted
             * However, as mentioned in the main comment of syncContacts(), we need to double
             * check that the contact is actually deleted in the default database and wasn't
             * re-inserted by something other client-side insert query before we continue with
             * the delete. Lock is explained in changeFromClient() of ChangeAgentDao.
             */
            if (matchCID == null || matchCID.size == 0) {
                mutexSync.withLock {
                    val teleCIDStillExists = database.contactDao().getContactRow(CID = teleCID) != null
                    val defaultCIDStillExists = DefaultContacts.aggregateContactExists(contentResolver, defaultCID)

                    if (teleCIDStillExists && !defaultCIDStillExists) {
                        val changeID = UUID.randomUUID().toString()
                        val changeTime = Instant.now().toEpochMilli()

                        val change = Change.create(
                            CID = teleCID
                        )

                        database.changeAgentDao().changeFromClient(
                            ChangeLog.create(
                                changeID = changeID,
                                changeTime = changeTime,
                                type = ChangeType.CONTACT_DELETE,
                                instanceNumber = instanceNumber,
                                changeJson = change.toJson()
                            ),
                            fromSync = true,
                            bubbleError = true
                        )
                    }
                }

                continue
            }

            // Corresponding contact number (by PK) in android default database
            var matchPK: ContactNumber? = null

            for (matchNumbers in matchCID) {
                // Uses custom .equals() implementation to check if their PKs are equal.
                if (matchNumbers == contactNumber) {
                    matchPK = matchNumbers
                    break
                }
            }

            // matchPK being null means that the corresponding contact number has been deleted
            if (matchPK == null) {
                mutexSync.withLock {
                    if (!DefaultContacts.contactNumberExists(
                            contentResolver,
                            defaultCID,
                            contactNumber.rawNumber
                        )
                    ) {
                        val changeID = UUID.randomUUID().toString()
                        val changeTime = Instant.now().toEpochMilli()

                        val change = Change.create(
                            CID = teleCID,
                            normalizedNumber = contactNumber.normalizedNumber,
                            degree = 0
                        )

                        database.changeAgentDao().changeFromClient(
                            ChangeLog.create(
                                changeID = changeID,
                                changeTime = changeTime,
                                type = ChangeType.CONTACT_NUMBER_DELETE,
                                instanceNumber = instanceNumber,
                                changeJson = change.toJson()
                            ),
                            fromSync = true,
                            bubbleError = true
                        )
                    }
                }

                continue
            }

            /**
             * TODO: Maybe we should make another default query in DefaultContacts to check
             *  that the version number is really different.
             *
             * TODO: See if versionNumber can ever be useful. -> Maybe if there was ever a need to
             *  store data other than the raw / normalized number, we could use versionNumber to
             *  detect changes in the default database. Ho
             *
             * If the code reaches here, then we know that matchPK and contactNumber must both have
             * the same PK (meaning same normalized number). So, if the default contact number has
             * a different rawNumber (almost definitely a small formatting change), then we update
             * the ContactNumber.
             *
             * NOTE: We don't base our check on versionNumber anymore. Although in MOST cases, a
             * change in versionNumber means a change in rawNumber; however, that is NOT ALWAYS the
             * case. For example, a change in phone number type (e.g., MOBILE, HOME, WORK) can also
             * cause a change in versionNumber.
             */
            if (matchPK.rawNumber != contactNumber.rawNumber) {
                val changeID = UUID.randomUUID().toString()
                val changeTime = Instant.now().toEpochMilli()

                val change = Change.create(
                    CID = teleCID,
                    normalizedNumber = contactNumber.normalizedNumber,
                    rawNumber = matchPK.rawNumber,
                    counterValue = matchPK.versionNumber
                )

                database.changeAgentDao().changeFromClient(
                    ChangeLog.create(
                        changeID = changeID,
                        changeTime = changeTime,
                        type = ChangeType.CONTACT_NUMBER_UPDATE,
                        instanceNumber = instanceNumber,
                        changeJson = change.toJson()
                    ),
                    fromSync = true,
                    bubbleError = true
                )
            }
        }
    }
}