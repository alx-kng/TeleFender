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
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.data.tele_database.entities.ContactNumbers
import com.telefender.phone.helpers.MiscHelpers
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
        val lastSyncTime = repository.getLastSyncTime(instanceNumber!!)

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
     *
     * Syncs our Contact / ContactNumbers database with Android's default Contact / PhoneNumbers database
     *
     * Idea is that we iterate through our database and see if any changes to corresponding
     * rows in default database (checks for updates and deletes), and then we iterate through the default database and see
     * if the corresponding rows exist in our database (checks for inserts)
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
        checkForUpdatesAndDeletes(database, defaultContactHashMap)

    }
    /**
     * Deals with any potential insertions into the Android default database and updates ours, as
     * well as returning a HashMap of all ContactNumbers for use in checking for updates and deletes
    */
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    suspend fun checkForInserts(context: Context, database: ClientDatabase, contentResolver : ContentResolver) : HashMap<String, MutableList<ContactNumbers>> {
        val parentNumber = MiscHelpers.getInstanceNumber(context)!!

        val curs: Cursor? = DefaultContacts.getContactNumberCursor(contentResolver)

        /**
         * We need to create a hash map of all the default database contact numbers (using the
         * CID as key) so that we can quickly find the correct rows / whether a row exists
         */
        val defaultContactHashMap = HashMap<String, MutableList<ContactNumbers>>()

        if (curs == null) {
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: Contact Number cursor is null; BAD")
        } else {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: Inside table synchronizer")
            while (!curs.isAfterLast) {

                /**
                 * Need to turn default CID into UUID version of CID since default CID is not the
                 * same as the CIDs we store in our Contacts / ContactNumbers tables
                 */
                val defCID = UUID.nameUUIDFromBytes((curs.getString(0) + parentNumber).toByteArray()).toString()
                val defNumber = MiscHelpers.cleanNumber(curs.getString(1))!!
                val defVersionNumber = curs.getString(2).toInt()

                // Corresponding contact numbers (by CID) in our database
                val matchCID: List<ContactNumbers> = database.contactNumbersDao().getContactNumbers_CID(defCID)

                // Corresponding contact numbers (by PK) in our database
                val matchPK: ContactNumbers? = database.contactNumbersDao().getContactNumbersRow(defCID, defNumber)

                /**
                 * If no ContactNumbers have the same CID, that means the corresponding contact
                 * doesn't even exist and thus needs to be inserted into the Contacts table
                 */
                if (matchCID.isEmpty()) {
                    val changeID = UUID.randomUUID().toString()
                    val changeTime = Instant.now().toEpochMilli()

                    database.changeAgentDao().changeFromClient(
                        changeID,
                        null,
                        changeTime,
                        ClientDBConstants.CHANGELOG_TYPE_CONTACT_INSERT,
                        defCID,
                        null,
                        null,
                        parentNumber,
                        null,
                        null
                    )
                }

                /**
                 * If no ContactNumbers have the same CID and Number (PK), that means the corresponding
                 * contact number doesn't exist and thus needs to be inserted into the ContactNumbers table
                 */
                if (matchPK == null) {
                    val changeID = UUID.randomUUID().toString()
                    val changeTime = Instant.now().toEpochMilli()

                    database.changeAgentDao().changeFromClient(
                        changeID,
                        null,
                        changeTime,
                        ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_INSERT,
                        defCID,
                        null,
                        defNumber,
                        null,
                        null,
                        defVersionNumber
                    )
                }

                val contactNumber = ContactNumbers(
                    defCID,
                    defNumber,
                    defVersionNumber
                )

                // get may return null if key doesn't yet have a list initialized with it
                if (defaultContactHashMap[defCID] == null) {
                    defaultContactHashMap[defCID] = mutableListOf(contactNumber)
                } else {
                    defaultContactHashMap[defCID]!!.add(contactNumber)
                }

                curs.moveToNext()
            }
            curs.close()
        }
        Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: AFTER SYNC INSERTS")
        // Now all CIDs and there lists of contact numbers from their database (in our format)
        return defaultContactHashMap
    }

    /**
     * TODO: Should we keep deleted numbers? That is, should we keep numbers for algorithm anyways?
     *  - I think no... (which we are already doing).
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun checkForUpdatesAndDeletes(database: ClientDatabase, defaultContactHashMap: HashMap<String, MutableList<ContactNumbers>>)  {
        val dodoCN: List<ContactNumbers> = database.contactNumbersDao().getAllContactNumbers()

        for (contactNumbers: ContactNumbers in dodoCN) {

            val dodoCID = contactNumbers.CID

            // Corresponding contact numbers (by CID) in android default database can be null or empty?
            val matchCID: MutableList<ContactNumbers>? = defaultContactHashMap.get(dodoCID)

            /*
             * Means the corresponding entire contact has been deleted
             */
            if (matchCID == null || matchCID.size == 0) {
                val changeID = UUID.randomUUID().toString()
                val changeTime = Instant.now().toEpochMilli()

                database.changeAgentDao().changeFromClient(
                    changeID,
                    null,
                    changeTime,
                    ClientDBConstants.CHANGELOG_TYPE_CONTACT_DELETE,
                    dodoCID,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            } else {
                // Corresponding contact number (by PK) in android default database
                var matchPK: ContactNumbers? = null

                for (matchNumbers: ContactNumbers in matchCID) {
                    if (matchNumbers.equals(contactNumbers)) {
                        matchPK = matchNumbers
                        break
                    }
                }
                // matchPK being null means that the corresponding contact number has been deleted
                if (matchPK == null) {
                    val changeID = UUID.randomUUID().toString()
                    val changeTime = Instant.now().toEpochMilli()

                    database.changeAgentDao().changeFromClient(
                        changeID,
                        null,
                        changeTime,
                        ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_DELETE,
                        dodoCID,
                        null,
                        contactNumbers.number,
                        null,
                        null,
                        null
                    )
                } else {

                    //Different version numbers mean that we have to update our row with theirs
                    if (matchPK.versionNumber != contactNumbers.versionNumber) {
                        val cnChangeID = UUID.randomUUID().toString()
                        val changeTime = Instant.now().toEpochMilli()

                        database.changeAgentDao().changeFromClient(
                            cnChangeID,
                            null,
                            changeTime,
                            ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_UPDATE,
                            dodoCID,
                            contactNumbers.number, // oldNumber
                            matchPK.number, // new number
                            null,
                            null,
                            matchPK.versionNumber
                        )
                    }
                }
            }
        }
    }
}