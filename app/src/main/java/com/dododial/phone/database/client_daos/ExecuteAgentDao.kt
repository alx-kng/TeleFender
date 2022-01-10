package com.dododial.phone.database.client_daos

import androidx.room.Dao
import androidx.room.Transaction
import com.dododial.phone.database.*
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_DELETE
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_INSERT
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_DELETE
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_INSERT
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_UPDATE
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_UPDATE
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_INSTANCE_DELETE
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_INSTANCE_INSERT

@Dao
interface ExecuteAgentDao: InstanceDao, ContactDao, ContactNumbersDao,
    ChangeLogDao, QueueToExecuteDao, QueueToUploadDao, TrustedNumbersDao {

    suspend fun executeFirst() {

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
            changeLog.name,
            changeLog.oldNumber,
            changeLog.number,
            changeLog.parentNumber,
            changeLog.trustability,
            changeLog.counterValue
        )
    }

    @Transaction
    open suspend fun executeFirstTransaction(
        changeID: String,
        instanceNumber: String?,
        changeTime: String,
        type: String,
        CID : String?,
        name : String?,
        oldNumber : String?,
        number : String?,
        parentNumber : String?,
        trustability : Int?,
        counterValue : Int?
    ) {

        when (type) {
            CHANGELOG_TYPE_CONTACT_INSERT -> cInsert(CID, parentNumber, name)
            CHANGELOG_TYPE_CONTACT_UPDATE -> cUpdate(CID, name)
            CHANGELOG_TYPE_CONTACT_DELETE -> cDelete(CID)
            CHANGELOG_TYPE_CONTACT_NUMBER_INSERT -> cnInsert(CID, number, counterValue)
            CHANGELOG_TYPE_CONTACT_NUMBER_UPDATE -> cnUpdate(CID, oldNumber, number)
            CHANGELOG_TYPE_CONTACT_NUMBER_DELETE -> cnDelete(CID, number)
            CHANGELOG_TYPE_INSTANCE_INSERT -> insInsert(instanceNumber)
            CHANGELOG_TYPE_INSTANCE_DELETE -> insDelete(number)
            //"insUpdate" -> insUpdate(oldNumber, number)
        }

        deleteQTE_ChangeID(changeID)
    }

    // TODO check if TrustedNumbers changes are correct
    
    suspend fun cInsert(CID: String?, parentNumber: String?, name: String?) {
        try {
            if (CID == null || parentNumber == null) {
                throw NullPointerException("CID or parentNumber was null for cInsert")
            } else {
                val contact = Contact(CID, parentNumber, name)
                insertContact(contact)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    suspend fun cUpdate(CID: String?, name: String?) {
        try {
            if (CID == null || name == null) {
                throw NullPointerException("CID or name was Null for cUpdate ")
            } else {
                updateContactName(CID, name)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

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
                val contactNumberChildren = getContactNumbers_CID(CID)

                val cnIterator = contactNumberChildren.iterator()
                while (cnIterator.hasNext()) {
                    cnDelete(CID, cnIterator.next().number)
                }

                deleteContact_CID(CID)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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

    suspend fun cnUpdate(CID: String?, oldNumber: String?, number: String?) {
        try {
            if (CID == null || oldNumber == null || number == null) {
                throw NullPointerException("oldNumber or number was null for cnUpdate")
            } else {
                updateContactNumbers(CID, oldNumber, number)
                updateTrustedNumbers(oldNumber, number)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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

    suspend fun insDelete(instanceNumber: String?) {
        try {
            if (instanceNumber == null) {
                throw NullPointerException("number was null for insDelete")
            } else {
                val instance = Instance(instanceNumber)

                deleteInstanceNumbers(instance)
                deleteTrustedNumbers(instanceNumber)
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