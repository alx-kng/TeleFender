package com.dododial.phone.data.dodo_database.background_tasks

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.dododial.phone.data.dodo_database.entities.CallLog
import com.dododial.phone.data.dodo_database.ClientDBConstants
import com.dododial.phone.data.dodo_database.ClientDatabase
import com.dododial.phone.data.dodo_database.MiscHelpers
import com.dododial.phone.data.dodo_database.MiscHelpers.cleanNumber
import com.dododial.phone.data.dodo_database.entities.ContactNumbers
import com.dododial.phone.data.default_database.ContactHelper
import timber.log.Timber
import java.time.Instant
import java.util.*

object TableSynchronizer {

    /**
    * Syncs our CallLog database with Android's CallLog database
    */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun syncCallLogs(database : ClientDatabase, contentResolver : ContentResolver) {

        val mostRecentCallLogDate = database.callLogDao().getMostRecentCallLogDate() ?: "0"
        val projection = arrayOf(
            android.provider.CallLog.Calls.NUMBER,
            android.provider.CallLog.Calls.TYPE,
            android.provider.CallLog.Calls.DATE,
            android.provider.CallLog.Calls.DURATION,
            android.provider.CallLog.Calls.GEOCODED_LOCATION
            )
        val selection = "DATE > ?"
        val curs : Cursor? = contentResolver.query(android.provider.CallLog.Calls.CONTENT_URI, projection, selection,
            arrayOf(mostRecentCallLogDate.toString()), null)

        if (curs != null) {
            while (curs.moveToNext()) {
                val number = cleanNumber(curs.getString(0))!!
                val type = curs.getInt(1).toString()
                val date = curs.getString(2).toLong()
                val duration = curs.getString(3)
                val location = curs.getString(4)

                val callLog = CallLog(number, type, date, duration, location, null)
                        Timber.i("DODODEBUG callLogSync added: %s", callLog.toString())
                database.callLogDao().insertLog(callLog)
            }
            curs?.close()
        }
    }

    /**
     * Syncs our Contact / ContactNumbers database with Android's default Contact / PhoneNumbers database
     *
     * Idea is that we iterate through our database and see if any changes to corresponding
     * rows in default database (checks for updates and deletes), and then we iterate through the default database and see
     * if the corresponding rows exist in our database (checks for inserts)
    */
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
        val tMgr = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val parentNumber = MiscHelpers.cleanNumber(tMgr.line1Number)

        val curs: Cursor? = ContactHelper.getContactNumberCursor(contentResolver)

        /**
         * We need to create a hash map of all the default database contact numbers (using the
         * CID as key) so that we can quickly find the correct rows / whether a row exists
         */
        var defaultContactHashMap = HashMap<String, MutableList<ContactNumbers>>()

        if (curs == null) {
            Timber.i("DODODEBUG: Contact Number cursor is null; BAD")
        } else {
            Timber.e("DODODEBUG: Inside table synchronizer")
            while (!curs.isAfterLast) {

                /**
                 * Need to turn default CID into UUID version of CID since default CID is not the
                 * same as the CIDs we store in our Contacts / ContactNumbers tables
                 */
                val defCID = UUID.nameUUIDFromBytes((curs.getString(0) + parentNumber).toByteArray()).toString()
                val defNumber = cleanNumber(curs.getString(1))!!
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
                if (defaultContactHashMap.get(defCID) == null) {
                    defaultContactHashMap.put(defCID, mutableListOf(contactNumber))
                } else {
                    defaultContactHashMap.get(defCID)!!.add(contactNumber)
                }

                curs.moveToNext()
            }
            curs.close()
        }
        Timber.e("DODODEBUG: AFTER SYNC INSERTS")
        // Now all CIDs and there lists of contact numbers from their database (in our format)
        return defaultContactHashMap
    }

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