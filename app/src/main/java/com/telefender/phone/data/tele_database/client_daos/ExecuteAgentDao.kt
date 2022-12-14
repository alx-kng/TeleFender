package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Transaction
import com.telefender.phone.data.tele_database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_DELETE
import com.telefender.phone.data.tele_database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_INSERT
import com.telefender.phone.data.tele_database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_DELETE
import com.telefender.phone.data.tele_database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_INSERT
import com.telefender.phone.data.tele_database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_UPDATE
import com.telefender.phone.data.tele_database.ClientDBConstants.CHANGELOG_TYPE_INSTANCE_DELETE
import com.telefender.phone.data.tele_database.ClientDBConstants.CHANGELOG_TYPE_INSTANCE_INSERT
import com.telefender.phone.data.tele_database.entities.Contact
import com.telefender.phone.data.tele_database.entities.ContactNumbers
import com.telefender.phone.data.tele_database.entities.Instance
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Dao
interface ExecuteAgentDao: InstanceDao, ContactDao, ContactNumbersDao,
    ChangeLogDao, QueueToExecuteDao, QueueToUploadDao, TrustedNumbersDao {
    
    suspend fun executeAll(
        mutexExecute: Mutex,
        mutexChange: Mutex,
        mutexKey: Mutex,
        mutexInstance : Mutex,
        mutexContact : Mutex,
        mutexContactNumbers : Mutex,
        mutexTrustedNumbers : Mutex,
        mutexOrganizations : Mutex,
        mutexMiscellaneous : Mutex
    ){

        var hasQTEs = mutexExecute.withLock {
            return@withLock hasQTEs()
        }
        while (hasQTEs) {
            executeFirst(
                mutexExecute,
                mutexChange,
                mutexKey,
                mutexInstance,
                mutexContact,
                mutexContactNumbers,
                mutexTrustedNumbers,
                mutexOrganizations,
                mutexMiscellaneous,
            )

            hasQTEs = mutexExecute.withLock {
                return@withLock hasQTEs()
            }
        }
    }

    /**
     * Finds first task to execute and passes it's corresponding ChangeLog to
     * helper function executeFirstTransaction.
     */
    suspend fun executeFirst(
        mutexExecute: Mutex,
        mutexChange: Mutex,
        mutexKey: Mutex,
        mutexInstance : Mutex,
        mutexContact : Mutex,
        mutexContactNumbers : Mutex,
        mutexTrustedNumbers : Mutex,
        mutexOrganizations : Mutex,
        mutexMiscellaneous : Mutex
    ) {

        mutexExecute.withLock {
            val firstJob = getFirstQTE()
            val firstID = firstJob.changeID
            updateQTEErrorCounter_Delta(firstID, 1)

            val changeLog = getChangeLogRow(firstID)

            executeFirstTransaction(
                changeLog.changeID,
                changeLog.instanceNumber,
                changeLog.changeTime,
                changeLog.type,
                changeLog.CID,
                changeLog.oldNumber,
                changeLog.number,
                changeLog.parentNumber,
                changeLog.trustability,
                changeLog.counterValue,
                mutexExecute,
                mutexChange,
                mutexKey,
                mutexInstance,
                mutexContact,
                mutexContactNumbers,
                mutexTrustedNumbers,
                mutexOrganizations,
                mutexMiscellaneous,
            )
        }
    }

    /**
     * Takes ChangeLog arguments and executes the corresponding database function in a single
     * transaction based on the type of change (eg instance insert), then deletes the task from
     * the QueueToExecute.
     */
    @Transaction
    open suspend fun executeFirstTransaction(
        changeID: String,
        instanceNumber: String?,
        changeTime: Long,
        type: String,
        CID : String?,
        oldNumber : String?,
        number : String?,
        parentNumber : String?,
        trustability : Int?,
        counterValue : Int?,
        mutexExecute: Mutex,
        mutexChange: Mutex,
        mutexKey: Mutex,
        mutexInstance : Mutex,
        mutexContact : Mutex,
        mutexContactNumbers : Mutex,
        mutexTrustedNumbers : Mutex,
        mutexOrganizations : Mutex,
        mutexMiscellaneous : Mutex
    ) {

        when (type) {
            CHANGELOG_TYPE_CONTACT_INSERT -> mutexContact.withLock {
                cInsert(CID, parentNumber)
            }
            CHANGELOG_TYPE_CONTACT_DELETE -> mutexContact.withLock {
                mutexContactNumbers.withLock {
                    mutexTrustedNumbers.withLock {
                        cDelete(CID)
                    }
                }
            }
            CHANGELOG_TYPE_CONTACT_NUMBER_INSERT -> mutexContactNumbers.withLock {
                mutexTrustedNumbers.withLock {
                    cnInsert(CID, number, counterValue)
                }
            }
            CHANGELOG_TYPE_CONTACT_NUMBER_UPDATE -> mutexContactNumbers.withLock {
                mutexTrustedNumbers.withLock {
                    cnUpdate(CID, oldNumber, number, counterValue)
                }
            }
            CHANGELOG_TYPE_CONTACT_NUMBER_DELETE -> mutexContactNumbers.withLock {
                mutexTrustedNumbers.withLock {
                    cnDelete(CID, number)
                }
            }
            CHANGELOG_TYPE_INSTANCE_INSERT -> mutexInstance.withLock {
                mutexTrustedNumbers.withLock {
                    insInsert(instanceNumber)
                }
            }
            CHANGELOG_TYPE_INSTANCE_DELETE -> mutexInstance.withLock {
                mutexContact.withLock {
                    mutexContactNumbers.withLock {
                        insDelete(number)
                    }
                }
            }
        }
        deleteQTE_ChangeID(changeID)
    }

    // TODO check if TrustedNumbers changes are correct
    /**
     * Inserts contact given CID, parentNumber, and Name. Higher level function than the corresponding
     * DAO function as it also verifies arguments are not Null and catches exceptions.
     */
    suspend fun cInsert(CID: String?, parentNumber: String?) {
        try {
            if (CID == null || parentNumber == null) {
                throw NullPointerException("CID or parentNumber was null for cInsert")
            } else {
                val contact = Contact(CID, parentNumber)
                insertContact(contact)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Deletes Contact given CID. Higher level function than the corresponding
     * DAO function as it also iterates through contact's corresponding ContactNumbers and deletes those
     * as well, verifies arguments are not Null, and catches exceptions.
     */
    suspend fun cDelete(CID: String?) {
        try {
            if (CID == null) {
                throw NullPointerException("CID was null for cDelete")
            } else {
                /*
                Gets all contact numbers associated with that contact and deletes
                them as well (this includes removing from the Trusted Numbers table
                through cnDelete())
                 */
                val contactNumberChildren : List<ContactNumbers> = getContactNumbers_CID(CID)
                for (contactNumber in contactNumberChildren) {
                    cnDelete(CID, contactNumber.number)
                }

                deleteContact_CID(CID)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Inserts ContactNumber given CID, number, and versionNumber. Higher level function than the corresponding
     * DAO function as it also inserts number into TrustedNumbers, verifies arguments are not Null,
     * and catches exceptions.
     */
    suspend fun cnInsert(CID: String?, number: String?, versionNumber: Int?){
        try {
            if (CID == null || number == null || versionNumber == null) {
                throw NullPointerException("CID, number, or versionNumber was null for cnInsert")
            } else {
                val contactNumber = ContactNumbers(CID, number, versionNumber)

                insertContactNumbers(contactNumber)
                insertTrustedNumbers(number)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Updates ContactNumber given CID, oldNumber, and new number. Higher level function than the corresponding
     * DAO function as it also updates TrustedNumbers, verifies arguments are not Null, and catches exceptions.
     */
    suspend fun cnUpdate(CID: String?, oldNumber: String?, number: String?, versionNumber: Int?) {
        try {
            if (CID == null || oldNumber == null || number == null) {
                throw NullPointerException("oldNumber or number was null for cnUpdate")
            } else {
                updateContactNumbers(CID, oldNumber, number, versionNumber)
                updateTrustedNumbers(oldNumber, number)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Deletes ContactNumber given CID and number. Higher level function than the corresponding
     * DAO function as it also deletes corresponding TrustedNumber, verifies arguments are not Null,
     * and catches exceptions.
     */
    suspend fun cnDelete(CID: String?, number: String?) {
        try {
            if (CID == null || number == null) {
                throw NullPointerException("CID or number was null for cnDelete")
            } else {
                deleteContactNumbers_PK(CID, number)
                deleteTrustedNumbers(number)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Inserts Instance given instanceNumber. Higher level function than the corresponding
     * DAO function as it also inserts number into TrustedNumbers, verifies arguments are not Null and catches exceptions.
     */
    suspend fun insInsert(instanceNumber: String?) {
        try {
            if (instanceNumber == null) {
                throw NullPointerException("number was null for insInsert")
            } else {
                val instance = Instance(instanceNumber)

                insertInstanceNumbers(instance)
                insertTrustedNumbers(instanceNumber)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Deletes Instance given instanceNumber. Higher level function than the corresponding
     * DAO function as it also deletes number from TrustedNumbers, verifies arguments are not Null,
     * and catches exceptions.
     */
    suspend fun insDelete(instanceNumber: String?) {
        try {
            if (instanceNumber == null) {
                throw NullPointerException("number was null for insDelete")
            } else {
                val instance = Instance(instanceNumber)

                /*
                Gets all contacts associated with instance number and calls cDelete() on each one,
                which takes care of corresponding contact numbers and trusted numbers
                 */
                val contactChildren : List<Contact> = getContacts_ParentNumber(instanceNumber)
                for (contact in contactChildren) {
                    cDelete(contact.CID)
                }

                deleteInstanceNumbers(instance)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * No real need to update instances, potentially just delete and create new?
     */
//    suspend fun insUpdate(oldNumber: String?, number: String?) {
//        try {
//            if (oldNumber == null || number == null) {
//                throw NullPointerException("oldNumber or number was null for insUpdate")
//            } else {
//                updateInstanceNumbers(oldNumber, number)
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }

}