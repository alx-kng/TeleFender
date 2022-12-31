package com.telefender.phone.data.tele_database.client_daos

import android.provider.CallLog
import androidx.room.Dao
import androidx.room.Transaction
import com.telefender.phone.data.tele_database.ClientDBConstants
import com.telefender.phone.data.tele_database.MutexType
import com.telefender.phone.data.tele_database.TeleLocks.mutexLocks
import com.telefender.phone.data.tele_database.entities.*
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.lang.Long.max
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.roundToInt

@Dao
interface ExecuteAgentDao: InstanceDao, ContactDao, ContactNumberDao, CallDetailDao,
    AnalyzedNumberDao, ChangeLogDao, ExecuteQueueDao, UploadQueueDao, StoredMapDao {
    
    suspend fun executeAll(){
        while (hasQTEs()) {
            executeFirst()
        }
    }

    /**
     * Finds first task to execute and passes it's corresponding ChangeLog to
     * helper function executeFirstTransaction.
     */
    suspend fun executeFirst() {
        val firstJob = getFirstQTE()
        val firstID = firstJob.changeID

        mutexLocks[MutexType.EXECUTE]!!.withLock {
            updateQTEErrorCounter_Delta(firstID, 1)
        }

        val changeLog = getChangeLogRow(firstID)
        executeChange(changeLog, true)
    }

    /**
     * TODO: Maybe put extra data in a JSON to prevent unnecessary database migrations.
     *
     * Takes ChangeLog arguments and executes the corresponding database function in a single
     * transaction based on the type of change (e.g., instance insert), then deletes the task from
     * the ExecuteQueue. We have also already confirmed that deadlock is not possible with our
     * mutex lock usage.
     */
    suspend fun executeChange(changeLog: ChangeLog, fromServer: Boolean = false) {
        if (changeLog.type == ClientDBConstants.CHANGELOG_TYPE_INSTANCE_DELETE) {
            executeChangeINSD(changeLog)
        } else {
            executeChangeNORM(changeLog, fromServer)
        }
    }

    /**
     * We separated out instance deletes so they won't fall under one huge Transaction. Additionally,
     * we will handle mutex locking more fine grained in insDelete because one instance
     * delete might take a while (still probably less than a few seconds), and we
     * wouldn't want the data to be inaccessible during that time.
     */
    suspend fun executeChangeINSD(changeLog: ChangeLog) {
        with(changeLog) {
            if (changeLog.type ==  ClientDBConstants.CHANGELOG_TYPE_INSTANCE_DELETE) {
                insDelete(this, instanceNumber)
            } else {
                Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: Not instance delete type!!!")
            }
        }
    }

    /**
     * TODO: HANDLE TRANSACTIONS FOR RETRY.
     *
     * Other part of executeChange(). Handles all non-instance-delete actions.
     */
    @Transaction
    suspend fun executeChangeNORM(changeLog: ChangeLog, fromServer: Boolean) {
        val mutexInstance = mutexLocks[MutexType.INSTANCE]!!
        val mutexContact = mutexLocks[MutexType.CONTACT]!!
        val mutexContactNumber = mutexLocks[MutexType.CONTACT_NUMBER]!!
        val mutexAnalyzed = mutexLocks[MutexType.ANALYZED]!!

        with(changeLog) {
            when (type) {
                ClientDBConstants.CHANGELOG_TYPE_CONTACT_INSERT -> mutexContact.withLock {
                    cInsert(CID, instanceNumber)
                }
                ClientDBConstants.CHANGELOG_TYPE_CONTACT_UPDATE -> mutexAnalyzed.withLock {
                    mutexContact.withLock {
                        mutexContactNumber.withLock {
                            cUpdate(CID, instanceNumber, blocked)
                        }
                    }
                }
                ClientDBConstants.CHANGELOG_TYPE_CONTACT_DELETE -> mutexAnalyzed.withLock {
                    mutexContact.withLock {
                        mutexContactNumber.withLock {
                            cDelete(CID)
                        }
                    }
                }
                ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_INSERT -> mutexAnalyzed.withLock {
                    mutexContactNumber.withLock {
                        cnInsert(CID, normalizedNumber, defaultCID, rawNumber, instanceNumber, counterValue, degree)
                    }
                }
                ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_UPDATE -> mutexAnalyzed.withLock {
                    mutexContactNumber.withLock {
                        cnUpdate(CID, oldNumber, rawNumber, counterValue)
                    }
                }
                ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_DELETE -> mutexAnalyzed.withLock {
                    mutexContactNumber.withLock {
                        cnDelete(CID, normalizedNumber, instanceNumber, degree)
                    }
                }
                ClientDBConstants.CHANGELOG_TYPE_INSTANCE_INSERT -> mutexInstance.withLock {
                    insInsert(instanceNumber)
                }
            }

            // If from server, delete associated QTE.
            if (fromServer) {
                val mutexExecute = mutexLocks[MutexType.EXECUTE]!!
                mutexExecute.withLock {
                    deleteQTE_ChangeID(changeID)
                }
            }
        }
    }

    /**
     * Inserts call log into CallDetail table and updates AnalyzedNumber. Only updates
     * AnalyzedNumber if log is inserted.
     */
    @Transaction
    suspend fun logInsert(callDetail: CallDetail) : Boolean {
        val inserted = insertDetailSync(callDetail)

        if (inserted) {
            with(callDetail) {
                val oldAnalyzed = getAnalyzed(normalizedNumber)
                val isIncoming = callDirection == CallLog.Calls.INCOMING_TYPE
                val isOutgoing = callDirection == CallLog.Calls.OUTGOING_TYPE
                val numValidCalls = oldAnalyzed.numIncoming!! + oldAnalyzed.numOutgoing!!

                val newAvg = if (isIncoming || isOutgoing) {
                    getNewAvg(oldAnalyzed.avgDuration!!, numValidCalls, callDuration!!)
                } else {
                    oldAnalyzed.avgDuration!!
                }

                updateAnalyzed(
                    AnalyzedNumber(
                        normalizedNumber = normalizedNumber,
                        lastCallTime = callDetail.callEpochDate,
                        numIncoming = oldAnalyzed.numIncoming + if (isIncoming) 1 else 0,
                        numOutgoing = oldAnalyzed.numOutgoing + if (isOutgoing) 1 else 0,
                        maxDuration = max(oldAnalyzed.maxDuration!!, callDuration!!),
                        avgDuration = newAvg
                    )
                )
            }
        }

        return inserted
    }

    /**
     * TODO: Maybe redundant transaction annotation with higher level functions.
     *
     * Inserts contact given CID, instance. Higher level function than the corresponding
     * DAO function as it automatically modifies AnalyzedNumbers and provides a more
     * detailed NullPointerException.
     */
    @Transaction
    suspend fun cInsert(CID: String?, instanceNumber: String?) {
        if (null in listOf(CID, instanceNumber)) {
            throw NullPointerException("CID || instanceNumber was null for cInsert")
        } else {
            val contact = Contact(CID!!, instanceNumber!!)
            insertContact(contact)
        }
    }

    /**
     * Updates contact given CID, instanceNumber, and blocked status. Primarily used to update
     * blocked status.
     */
    @Transaction
    suspend fun cUpdate(CID: String?, instanceNumber: String?, blocked: Boolean?) {
        if (null in listOf(CID, instanceNumber, blocked)) {
            throw NullPointerException("CID || instanceNumber || blocked was null for cUpdate")
        } else {
            /*
            Ensures current blocked status is different from the passed in blocked value
            (to prevent duplicate update of AnalyzedNumber and unnecessary Contact update).
             */
            val currBlockedStatus = contactBlocked(CID!!)
            if (currBlockedStatus == blocked) {
                return
            }

            updateContactBlocked(CID, blocked!!)

            /*
            Only update AnalyzedNumbers for child ContactNumbers if the Contact's instance number
            is the same as user number (contact is direct contact).
             */
            if (instanceNumber == getUserNumber()) {
                val contactNumberChildren : List<ContactNumber> = getContactNumbers_CID(CID)
                for (contactNumber in contactNumberChildren) {
                    val normalizedNumber = contactNumber.normalizedNumber
                    val numBlockedDelta = if (blocked) 1 else -1
                    val oldAnalyzed = getAnalyzed(normalizedNumber)

                    updateAnalyzed(
                        AnalyzedNumber(
                            normalizedNumber = normalizedNumber,
                            isBlocked = blocked,
                            numMarkedBlocked = oldAnalyzed.numMarkedBlocked?.plus(numBlockedDelta)
                        )
                    )
                }
            }
        }
    }

    /**
     * Deletes Contact given CID. Higher level function than the corresponding
     * DAO function as it automatically modifies AnalyzedNumbers and provides a more
     * detailed NullPointerException.
     */
    @Transaction
    suspend fun cDelete(CID: String?) {
        if (CID == null) {
            throw NullPointerException("CID was null for cDelete")
        } else {
            /*
            Gets all contact numbers associated with that contact and deletes them as well.
             */
            val contactNumberChildren : List<ContactNumber> = getContactNumbers_CID(CID)
            for (contactNumber in contactNumberChildren) {
                with(contactNumber) {
                    cnDelete(CID, normalizedNumber, instanceNumber, degree)
                }
            }

            deleteContact_CID(CID)
        }
    }

    /**
     * Inserts ContactNumber given CID, number, and versionNumber. Higher level function than the corresponding
     * DAO function as it automatically modifies AnalyzedNumbers and provides a more
     * detailed NullPointerException.
     */
    @Transaction
    suspend fun cnInsert(
        CID: String?,
        normalizedNumber: String?,
        defaultCID: String?,
        rawNumber: String?,
        instanceNumber: String,
        versionNumber: Int?,
        degree: Int?
    ){
        if (null in listOf(CID, normalizedNumber, defaultCID, rawNumber, versionNumber, degree)) {
            throw NullPointerException("CID || number || defaultCID || versionNumber || degree was null for cnInsert")
        } else {
            /*
            Prevents double insertion by checking if number exists first. Also necessary to prevent
            AnalyzedNumber from being updated multiple times for same action.
             */
            val numberExists = getContactNumbersRow(CID!!, normalizedNumber!!) != null
            if (numberExists) {
                return
            }

            val contactNumber = ContactNumber(
                CID,
                normalizedNumber,
                defaultCID!!,
                rawNumber!!,
                instanceNumber,
                versionNumber!!,
                degree!!
            )
            insertContactNumbers(contactNumber)

            /*
            If number is added as a direct contact (instanceNumber = userNumber), set isBlocked to
            false, increment numSharedContacts.
            If number is not added as a direct contact, increment numTreeContacts.
            ADDITIONALLY, for both direct / tree contacts, reset notifyGate, and recalculate
            minDegree and degreeString
             */
            val oldAnalyzed = getAnalyzed(normalizedNumber)
            with(oldAnalyzed) {
                val newDegreeString = changeDegreeString(degreeString, degree, false)
                if (instanceNumber == getUserNumber()) {
                    updateAnalyzed(
                        AnalyzedNumber(
                            normalizedNumber = normalizedNumber,
                            notifyGate = 2,
                            isBlocked = false,
                            numSharedContacts = numSharedContacts?.plus(1),
                            minDegree = 0,
                            degreeString = newDegreeString
                        )
                    )
                } else {
                    updateAnalyzed(
                        AnalyzedNumber(
                            normalizedNumber = normalizedNumber,
                            notifyGate = 2,
                            numTreeContacts = numTreeContacts?.plus(1),
                            minDegree = getMinDegree(newDegreeString),
                            degreeString = newDegreeString
                        )
                    )
                }
            }

        }
    }

    /**
     * TODO: Make sure nothing is fishy with versionNumber, specifically, with the default value.
     * TODO: Fix cnUpdate to include normalizedNumber.
     * TODO: Need to include default blocked update.
     * TODO: Maybe get rid of oldNumber term (misleading). Should probably just be normalized
     *  number. OR update normalizedNumber as well.
     *
     * Updates ContactNumber given CID, oldNumber, and new rawNumber. Note that versionNumber is
     * actually still used. So far, it seems like ContactNumber updates don't affect its
     * AnalyzedNumber, as updating the rawNumber shouldn't really change the algorithm.
     */
    @Transaction
    suspend fun cnUpdate(CID: String?, oldNumber: String?, rawNumber: String?, versionNumber: Int?) {
        if (null in listOf(CID, oldNumber, rawNumber, versionNumber)) {
            throw NullPointerException("CID || oldNumber || rawNumber || versionNumber was null for cnUpdate")
        } else {
            updateContactNumbers(CID!!, oldNumber!!, rawNumber!!, versionNumber)
        }
    }

    /**
     * Deletes ContactNumber given CID and number. Higher level function than the corresponding
     * DAO function as it automatically modifies AnalyzedNumbers and provides a more
     * detailed NullPointerException.
     */
    @Transaction
    suspend fun cnDelete(CID: String?, normalizedNumber: String?, instanceNumber: String?, degree: Int?) {
        if (null in listOf(CID, normalizedNumber, instanceNumber, degree)) {
            throw NullPointerException("CID || number || instanceNumber || degree was null for cnDelete")
        } else {
            /*
            Prevents double deletion by checking if number is already deleted. Also necessary to
            prevent AnalyzedNumber from being updated multiple times for same action.
             */
            val numberExists = getContactNumbersRow(CID!!, normalizedNumber!!) != null
            if (!numberExists) {
                return
            }

            /*
            If instanceNumber is same as user contact (number is a direct contact of user), then
            decrement numSharedContacts. Moreover, if the contact associated with the number is
            blocked, then decrement numBlockedContacts. Remember, deleting contacts doesn't change
            its isBlocked value.
            If not direct contact, then just decrement numTreeContacts. Additionally, no matter if
            the contact number is a direct contact or not, recalculate minDegree and degreeString.
             */
            val oldAnalyzed = getAnalyzed(normalizedNumber)
            with(oldAnalyzed) {
                val newDegreeString = changeDegreeString(degreeString, degree, true)
                if (instanceNumber == getUserNumber()) {
                    val numBlockedDelta = if (contactBlocked(CID) == true) 1 else 0

                    updateAnalyzed(
                        AnalyzedNumber(
                            normalizedNumber = normalizedNumber,
                            numMarkedBlocked = numMarkedBlocked?.minus(numBlockedDelta),
                            numSharedContacts = numSharedContacts?.minus(1),
                            minDegree = getMinDegree(newDegreeString),
                            degreeString = newDegreeString
                        )
                    )
                } else {
                    updateAnalyzed(
                        AnalyzedNumber(
                            normalizedNumber = normalizedNumber,
                            numTreeContacts = numTreeContacts?.minus(1),
                            minDegree = getMinDegree(newDegreeString),
                            degreeString = newDegreeString
                        )
                    )
                }
            }

            deleteContactNumbers_PK(CID, normalizedNumber)
        }
    }

    /**
     * Inserts Instance given instanceNumber. Higher level function than the corresponding
     * DAO function as it automatically modifies AnalyzedNumbers and provides a more
     * detailed NullPointerException.
     */
    suspend fun insInsert(instanceNumber: String?) {
        if (instanceNumber == null) {
            throw NullPointerException("instanceNumber was null for insInsert")
        } else {
            val instance = Instance(instanceNumber)
            insertInstanceNumbers(instance)
        }
    }

    /**
     * Deletes Instance given instanceNumber. Higher level function than the corresponding
     * DAO function as it automatically modifies AnalyzedNumbers and provides a more
     * detailed NullPointerException.
     */
    suspend fun insDelete(changeLog: ChangeLog, instanceNumber: String?) {
        if (instanceNumber == null) {
            throw NullPointerException("instanceNumber was null for insDelete")
        } else {
            val mutexInstance = mutexLocks[MutexType.INSTANCE]!!
            val mutexExecute = mutexLocks[MutexType.EXECUTE]!!
            val mutexContact = mutexLocks[MutexType.CONTACT]!!
            val mutexContactNumber = mutexLocks[MutexType.CONTACT_NUMBER]!!
            val mutexAnalyzed = mutexLocks[MutexType.ANALYZED]!!

            /*
            Gets all contacts associated with instance number and calls cDelete() on each one,
            which takes care of corresponding contact numbers and trusted numbers
             */
            val contactChildren : List<Contact> = getContactsByInstance(instanceNumber)
            for (contact in contactChildren) {
                mutexAnalyzed.withLock {
                    mutexContact.withLock {
                        mutexContactNumber.withLock {
                            cDelete(contact.CID)
                        }
                    }
                }

            }

            mutexInstance.withLock {
                mutexExecute.withLock {
                    finalDelete(changeLog, Instance(instanceNumber))
                }
            }
        }
    }

    /**
     * Deleting instance number and QTE is wrapped under one transaction to force either both
     * to succeed or both to fail.
     */
    @Transaction
    suspend fun finalDelete(changeLog: ChangeLog, instance: Instance) {
        deleteInstanceNumbers(instance)

        // Instance delete must be from server, so delete associated QTE.
        deleteQTE_ChangeID(changeLog.changeID)
    }

    suspend fun changeDegreeString(degreeString: String?, degree: Int?, remove: Boolean) : String? {
        if (degreeString == null || degree == null) {
            return degreeString
        }

        return if (remove) {
            degreeString.replaceFirst(degree.toString(), "")
        } else {
            degreeString + degree
        }
    }

    suspend fun getMinDegree(degreeString: String?) : Int? {
        return degreeString?.minOrNull()?.digitToIntOrNull()
    }

    suspend fun getNewAvg(oldAvg: Double, numValidCalls: Int, newCallDuration: Long): Double {
        val newAvg = (oldAvg * numValidCalls + newCallDuration) / (numValidCalls + 1.0)
        return (newAvg * 100.0).roundToInt() / 100.0
    }

}