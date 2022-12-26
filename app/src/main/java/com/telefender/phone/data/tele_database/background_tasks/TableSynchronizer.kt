package com.telefender.phone.data.tele_database.background_tasks

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.CallLog
import androidx.annotation.RequiresApi
import com.telefender.phone.data.default_database.DefaultContacts
import com.telefender.phone.data.tele_database.ClientDBConstants
import com.telefender.phone.data.tele_database.ClientDatabase
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.MutexType
import com.telefender.phone.data.tele_database.TeleLocks.mutexLocks
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.data.tele_database.entities.ContactNumber
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.Instant
import java.util.*

object TableSynchronizer {

    private const val checkBackPeriod = 5 * 60000

    /**
     * Syncs our CallDetail database with Android's CallDetail database. Used for both periodic and
     * immediate syncs. Periodic sync is for when user switches to different phone app for a certain
     * amount of time, and immediate sync for after every call (since it's important for our
     * algorithm to update logs immediately).
     *
     * Also, we've confirmed that checkBackPeriod is necessary.
    */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun syncCallLogs(context: Context, repository: ClientRepository, contentResolver: ContentResolver) : Boolean {

        val instanceNumber = MiscHelpers.getInstanceNumber(context)
        val lastSyncTime = repository.getLastSyncTime(instanceNumber)

        /*
        For retrieving voicemail logs, which have a small chance of being placed slightly before
        the corresponding missed / rejected / blocked call (epochDate wise).
         */
        val checkFromTime = if (lastSyncTime == 0L) lastSyncTime else lastSyncTime - checkBackPeriod

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.GEOCODED_LOCATION
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
                val number = MiscHelpers.cleanNumber(curs.getString(0))!!
                val typeInt = curs.getInt(1)
                val date = curs.getString(2).toLong()
                val duration = curs.getString(3).toLong()
                val location = curs.getString(4)
                val dir = MiscHelpers.getTrueDirection(typeInt, number)

                val callDetail = CallDetail(number, dir.toString(), date, duration, location, dir)

                try {
                    val inserted = repository.insertDetailSync(instanceNumber, callDetail)
                    if (inserted) {
                        Timber.i("${MiscHelpers.DEBUG_LOG_TAG} syncCallLogs(): SYNCED: $callDetail")
                    } else {
                        Timber.i("${MiscHelpers.DEBUG_LOG_TAG} syncCallLogs(): ALREADY SYNCED: $callDetail")
                    }
                } catch (e: Exception) {
                    return false
                }
            }
            curs.close()
        }

        return true
    }

    /**********************************************************************************************
     * TODO: Consider not inserting contacts already synced maybe?
     * TODO: Double check that we're not writing / inserting into tables too redundantly.
     * TODO: Put in mutexSync and sync double check. Refer to first note.
     * TODO: RETRIEVE DEFAULT CONTACT BY DEFAULT CID USING SAME METHOD AS syncCallLogs(). Also,
     *  summarize in document.
     *
     * Syncs our Contact / ContactNumber database with Android's default Contact / PhoneNumbers database
     *
     * Idea is that we iterate through our database and see if any changes to corresponding
     * rows in default database (checks for updates and deletes), and then we iterate through the default database and see
     * if the corresponding rows exist in our database (checks for inserts).
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
    ***********************************************************************************************/
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    suspend fun syncContacts(context: Context, database: ClientDatabase, contentResolver: ContentResolver) {
        val defaultContactHashMap = checkForInserts(context, database, contentResolver)
        checkForUpdatesAndDeletes(context, database, defaultContactHashMap, contentResolver)

    }

    /**
     * TODO: New sync safety logic already put in, but double check.
     *
     * Deals with any potential insertions into the Android default database and updates ours, as
     * well as returning a HashMap of all ContactNumber for use in checking for updates and deletes
    */
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    suspend fun checkForInserts(
        context: Context,
        database: ClientDatabase,
        contentResolver : ContentResolver
    ) : HashMap<String, MutableList<ContactNumber>> {

        val instanceNumber = MiscHelpers.getInstanceNumber(context)
        val mutexSync = mutexLocks[MutexType.SYNC]!!
        val curs: Cursor? = DefaultContacts.getContactNumberCursor(contentResolver)

        /**
         * We need to create a hash map of all the default database contact numbers (using the
         * CID as key) so that we can quickly find the correct rows / whether a row exists
         */
        val defaultContactHashMap = HashMap<String, MutableList<ContactNumber>>()

        if (curs == null) {
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: Contact Number cursor is null; BAD")
        } else {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: Inside table synchronizer")
            while (!curs.isAfterLast) {

                /**
                 * Need to turn default CID into UUID version of CID since default CID is not the
                 * same as the CIDs we store in our Contacts / ContactNumber tables
                 */
                val defaultCID = curs.getString(0)
                val teleCID = UUID.nameUUIDFromBytes((defaultCID + instanceNumber).toByteArray()).toString()
                val rawNumber = curs.getString(1)
                val cleanNumber = MiscHelpers.cleanNumber(rawNumber)
                val versionNumber = curs.getString(2).toInt()

                // Corresponding contact numbers (by CID) in our database
                val matchCID: List<ContactNumber> = database.contactNumberDao().getContactNumbers_CID(teleCID)

                // Corresponding contact numbers (by PK) in our database
                val matchPK: ContactNumber? = database.contactNumberDao().getContactNumbersRow(teleCID, cleanNumber)
0
                /**
                 * If no ContactNumber have the same CID, that means the corresponding contact
                 * doesn't even exist and thus needs to be inserted into the Contacts table.
                 * However, as mentioned in the main comment of syncContacts(), we need to double
                 * check that the contact still exists in the default database and wasn't deleted
                 * by something other client-side delete query before we continue with the insert.
                 * Lock is explained in changeFromClient() of ChangeAgentDao.
                 */
                if (matchCID.isEmpty()) {
                    mutexSync.withLock {
                        if (DefaultContacts.contactExists(contentResolver, defaultCID)) {
                            val changeID = UUID.randomUUID().toString()
                            val changeTime = Instant.now().toEpochMilli()

                            database.changeAgentDao().changeFromClient(
                                ChangeLog(
                                    changeID = changeID,
                                    changeTime = changeTime,
                                    type = ClientDBConstants.CHANGELOG_TYPE_CONTACT_INSERT,
                                    instanceNumber = instanceNumber,
                                    CID = teleCID
                                ),
                                fromSync = true
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
                        if (DefaultContacts.contactNumberExists(contentResolver, defaultCID, rawNumber)) {
                            val changeID = UUID.randomUUID().toString()
                            val changeTime = Instant.now().toEpochMilli()

                            database.changeAgentDao().changeFromClient(
                                ChangeLog(
                                    changeID = changeID,
                                    changeTime = changeTime,
                                    type = ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_INSERT,
                                    instanceNumber = instanceNumber,
                                    CID = teleCID,
                                    cleanNumber = cleanNumber,
                                    defaultCID = defaultCID,
                                    rawNumber = rawNumber,
                                    degree = 0,
                                    counterValue = versionNumber
                                ),
                                fromSync = true
                            )
                        }
                    }
                }

                val contactNumber = ContactNumber(
                    CID = teleCID,
                    cleanNumber = cleanNumber,
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
        Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: AFTER SYNC INSERTS")
        // Contains all CIDs and their lists of contact numbers from default database (in our format)
        return defaultContactHashMap
    }

    /**
     * TODO: Should we keep deleted numbers? That is, should we keep numbers for algorithm anyways?
     *  - I think no... (which we are already doing).
     *
     * TODO: Verify that contact number update works.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun checkForUpdatesAndDeletes(
        context: Context,
        database: ClientDatabase,
        defaultContactHashMap: HashMap<String, MutableList<ContactNumber>>,
        contentResolver: ContentResolver
    )  {
        val instanceNumber = MiscHelpers.getInstanceNumber(context)
        val teleCN: List<ContactNumber> = database.contactNumberDao().getAllContactNumbers()
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
                    if (!DefaultContacts.contactExists(contentResolver, defaultCID)) {
                        val changeID = UUID.randomUUID().toString()
                        val changeTime = Instant.now().toEpochMilli()

                        database.changeAgentDao().changeFromClient(
                            ChangeLog(
                                changeID = changeID,
                                changeTime = changeTime,
                                type = ClientDBConstants.CHANGELOG_TYPE_CONTACT_DELETE,
                                instanceNumber = instanceNumber,
                                CID = teleCID
                            ),
                            fromSync = true
                        )
                    }
                }

            } else {
                // Corresponding contact number (by PK) in android default database
                var matchPK: ContactNumber? = null

                for (matchNumbers: ContactNumber in matchCID) {
                    if (matchNumbers.equals(contactNumber)) {
                        matchPK = matchNumbers
                        break
                    }
                }
                // matchPK being null means that the corresponding contact number has been deleted
                if (matchPK == null) {
                    mutexSync.withLock {
                        if (!DefaultContacts.contactNumberExists(
                                contentResolver, defaultCID, contactNumber.rawNumber)
                        ) {
                            val changeID = UUID.randomUUID().toString()
                            val changeTime = Instant.now().toEpochMilli()

                            database.changeAgentDao().changeFromClient(
                                ChangeLog(
                                    changeID = changeID,
                                    changeTime = changeTime,
                                    type = ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_DELETE,
                                    instanceNumber = instanceNumber,
                                    CID = teleCID,
                                    cleanNumber = contactNumber.cleanNumber,
                                    degree = 0
                                ),
                                fromSync = true
                            )
                        }
                    }
                } else {

                    /*
                    TODO: Maybe we should make another default query in DefaultContacts to check
                     that the version number is really different. However, it may not be necessary
                     given that we aren't really using versionNumber.

                    Different version numbers mean that we have to update our row with theirs
                    Actually, versionNumber and this branch are currently unused, since when you
                    "update" a contact number in the default contacts, it's processed as a delete
                    of the old number and an insertion of the new number. However, we will keep
                    this branch here anyways just in case.
                     */
                    if (matchPK.versionNumber != contactNumber.versionNumber) {
                        val changeID = UUID.randomUUID().toString()
                        val changeTime = Instant.now().toEpochMilli()

                        database.changeAgentDao().changeFromClient(
                            ChangeLog(
                                changeID = changeID,
                                changeTime = changeTime,
                                type = ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_UPDATE,
                                instanceNumber = instanceNumber,
                                CID = teleCID,
                                cleanNumber = matchPK.cleanNumber, // new number
                                oldNumber = contactNumber.cleanNumber
                            ),
                            fromSync = true
                        )
                    }
                }
            }
        }
    }
}