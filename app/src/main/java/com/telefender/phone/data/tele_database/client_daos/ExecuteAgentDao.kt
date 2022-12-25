package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Transaction
import com.telefender.phone.data.tele_database.ClientDBConstants
import com.telefender.phone.data.tele_database.MutexType
import com.telefender.phone.data.tele_database.TeleLocks.mutexLocks
import com.telefender.phone.data.tele_database.entities.*
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@Dao
interface ExecuteAgentDao: InstanceDao, ContactDao, ContactNumberDao, AnalyzedNumberDao,
    ChangeLogDao, ExecuteQueueDao, UploadQueueDao, StoredMapDao {
    
    suspend fun executeAll(){
        val mutexExecute = mutexLocks[MutexType.EXECUTE]!!
        var hasQTEs = mutexExecute.withLock { return@withLock hasQTEs() }

        while (hasQTEs) {
            executeFirst()
            hasQTEs = mutexExecute.withLock { return@withLock hasQTEs() }
        }
    }

    /**
     * Finds first task to execute and passes it's corresponding ChangeLog to
     * helper function executeFirstTransaction.
     */
    suspend fun executeFirst() {
        val mutexExecute = mutexLocks[MutexType.EXECUTE]!!
        mutexExecute.withLock {
            val firstJob = getFirstQTE()
            val firstID = firstJob.changeID
            updateQTEErrorCounter_Delta(firstID, 1)

            val changeLog = getChangeLogRow(firstID)
            executeChange(changeLog, true)
        }
    }

    /**
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
                        cnInsert(CID, cleanNumber, defaultCID, rawNumber, instanceNumber, counterValue, degree)
                    }
                }
                ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_UPDATE -> mutexAnalyzed.withLock {
                    mutexContactNumber.withLock {
                        cnUpdate(CID, oldNumber, cleanNumber, counterValue)
                    }
                }
                ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_DELETE -> mutexAnalyzed.withLock {
                    mutexContactNumber.withLock {
                        cnDelete(CID, cleanNumber, instanceNumber, degree)
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
            updateContactBlocked(CID!!, blocked!!)

            /*
            Only update AnalyzedNumbers for child ContactNumbers if the Contact's instance number
            is the same as user number. That is, the contact is the user's direct contact.
             */
            if (instanceNumber == getUserNumber()) {
                val contactNumberChildren : List<ContactNumber> = getContactNumbers_CID(CID)
                for (contactNumber in contactNumberChildren) {
                    val number = contactNumber.cleanNumber
                    val numBlockedDelta = if (blocked) 1 else -1
                    val oldAnalyzed = getAnalyzed(number)

                    updateAnalyzed(
                        AnalyzedNumber(
                            number = number,
                            isBlocked = blocked,
                            numMarkedBlocked = oldAnalyzed.numMarkedBlocked?.plus(numBlockedDelta)
                        )
                    )
                }
            }
        }
    }

    /**
     * TODO: Add in AnalyzedNumbers to this
     *
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
                    cnDelete(CID, cleanNumber, instanceNumber, degree)
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
        cleanNumber: String?,
        defaultCID: String?,
        rawNumber: String?,
        instanceNumber: String,
        versionNumber: Int?,
        degree: Int?
    ){
        if (null in listOf(CID, cleanNumber, defaultCID, rawNumber, versionNumber, degree)) {
            throw NullPointerException("CID || number || defaultCID || versionNumber || degree was null for cnInsert")
        } else {
            val contactNumber = ContactNumber(
                CID!!,
                cleanNumber!!,
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
            val oldAnalyzed = getAnalyzed(cleanNumber)
            with(oldAnalyzed) {
                val newDegreeString = changeDegreeString(degreeString, degree, false)
                if (instanceNumber == getUserNumber()) {
                    updateAnalyzed(
                        AnalyzedNumber(
                            number = cleanNumber,
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
                            number = cleanNumber,
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
     * TODO: Fix cnUpdate to include cleanNumber.
     *
     * Updates ContactNumber given CID, oldNumber, and new number. Higher level function than the corresponding
     * DAO function as it automatically modifies AnalyzedNumbers and provides a more
     * detailed NullPointerException.
     */
    @Transaction
    suspend fun cnUpdate(CID: String?, oldNumber: String?, rawNumber: String?, versionNumber: Int?) {
        if (null in listOf(CID, oldNumber, rawNumber)) {
            throw NullPointerException("CID || oldNumber || rawNumber was null for cnUpdate")
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
    suspend fun cnDelete(CID: String?, cleanNumber: String?, instanceNumber: String?, degree: Int?) {
        if (null in listOf(CID, cleanNumber, instanceNumber, degree)) {
            throw NullPointerException("CID || number || instanceNumber || degree was null for cnDelete")
        } else {

            /*
            If instanceNumber is same as user contact (number is a direct contact of user), then
            decrement numSharedContacts. Moreover, if the contact associated with the number is
            blocked, then decrement numBlockedContacts. Remember, deleting contacts doesn't change
            its isBlocked value.
            If not direct contact, then just decrement numTreeContacts. Additionally, no matter if
            the contact number is a direct contact or not, recalculate minDegree and degreeString.
             */
            val oldAnalyzed = getAnalyzed(cleanNumber!!)
            with(oldAnalyzed) {
                val newDegreeString = changeDegreeString(degreeString, degree, true)
                if (instanceNumber == getUserNumber()) {
                    val numBlockedDelta = if (contactBlocked(CID!!) == true) 1 else 0

                    updateAnalyzed(
                        AnalyzedNumber(
                            number = cleanNumber,
                            numMarkedBlocked = numMarkedBlocked?.minus(numBlockedDelta),
                            numSharedContacts = numSharedContacts?.minus(1),
                            minDegree = getMinDegree(newDegreeString),
                            degreeString = newDegreeString
                        )
                    )
                } else {
                    updateAnalyzed(
                        AnalyzedNumber(
                            number = cleanNumber,
                            numTreeContacts = numTreeContacts?.minus(1),
                            minDegree = getMinDegree(newDegreeString),
                            degreeString = newDegreeString
                        )
                    )
                }
            }

            deleteContactNumbers_PK(CID!!, cleanNumber)
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
     * TODO: MAKE SURE WE'RE NOT USING CASCADING DELETE FOR FOREIGN KEYS, SINCE WE NEED TO
     *  INDIVIDUALLY DELETE SO WE CAN UPDATE ANALYZED_NUMBERS!!!
     *
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
}