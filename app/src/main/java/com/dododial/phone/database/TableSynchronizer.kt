package com.dododial.phone.database

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.dododial.phone.database.android_db.ContactDetailsHelper
import java.time.Instant
import java.util.*

object TableSynchronizer {

    /**
     * Idea is that we iterate through our database and see if any changes to corresponding
     * rows in default database (checks for updates and deletes), and then we iterate through the default database and see
     * if the corresponding rows exist in our database (checks for inserts)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    suspend fun syncContacts(context: Context, database: ClientDatabase, contentResolver: ContentResolver) {
        val tMgr = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val parentNumber = tMgr.line1Number

        val curs: Cursor? = ContactDetailsHelper.getContactNumberCursor(contentResolver)

        /**
         * We need to create a hash map of all the default database contact numbers (using the
         * CID as key) so that we can quickly find the correct rows / whether a row exists
         */
        var defaultContactHashMap = HashMap<String, ContactNumbers>()

        if (curs == null) {
            Log.i("DODODEBUG: ", "Contact Number cursor is null; BAD")
        } else {
            while (!curs.isAfterLast) {

                /**
                 * Need to turn default CID into UUID version of CID since default CID is not the
                 * same as the CIDs we store in our Contacts / ContactNumbers tables
                 */
                val defCID = UUID.nameUUIDFromBytes(curs.getString(0).toByteArray()).toString()
                val defNumber = curs.getString(1)
                val defName = curs.getString(2)
                val defVersionNumber = curs.getString(3).toInt()

                val matchCID: List<ContactNumbers> = database.contactNumbersDao().getContactNumbers_CID(defCID)
                val matchCN: ContactNumbers? = database.contactNumbersDao().getContactNumbersRow(defCID, defNumber)

                /**
                 * If no ContactNumbers have the same CID, that means the corresponding contact
                 * doesn't even exist and thus needs to be inserted into the Contacts table
                 */
                if (matchCID.isEmpty()) {
                    val changeID = UUID.randomUUID().toString()
                    val changeTime = Instant.now().toEpochMilli().toString()

                    database.changeAgentDao().changeFromClient(
                        changeID,
                        null,
                        changeTime,
                        ClientDBConstants.CHANGELOG_TYPE_CONTACT_INSERT,
                        defCID,
                        defName,
                        null,
                        null,
                        parentNumber,
                        null,
                        null
                    )
                }

                val contactNumber = ContactNumbers(
                    defCID,
                    defNumber,
                    defName,
                    defVersionNumber
                )

                defaultContactHashMap.put(curs.getString(0), contactNumber)

                curs.moveToNext()
            }
        }
    }
}