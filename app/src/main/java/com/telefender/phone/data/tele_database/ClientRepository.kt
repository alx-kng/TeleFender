package com.telefender.phone.data.tele_database

import androidx.annotation.WorkerThread
import com.telefender.phone.data.tele_database.client_daos.*
import com.telefender.phone.data.tele_database.entities.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ClientRepository(

    /**
     * TODO: Maybe we shouldn't require some queries to run on a worker thread. For example,
     *  retrieving the tree entities or safe logs used to let a new call through may need to be
     *  more immediate.
     */

    private val callDetailDao : CallDetailDao,
    private val changeAgentDao : ChangeAgentDao,
    private val uploadAgentDao: UploadAgentDao,
    private val changeLogDao : ChangeLogDao,
    private val executeAgentDao : ExecuteAgentDao,
    private val storedMapDao : StoredMapDao,
    private val queueToExecuteDao : QueueToExecuteDao,
    private val queueToUploadDao : QueueToUploadDao,
    private val instanceDao : InstanceDao,
    private val contactDao : ContactDao,
    private val contactNumbersDao : ContactNumbersDao,
    private val safeLogDao : SafeLogDao,
    ) {

    /**
     * Mutex locks to prevent conflicting access to same table
     * Makes our database thread safe (even if Room supposedly thread safe)
     */
    private val mutexExecute = Mutex()
    private val mutexUpload = Mutex()
    private val mutexChange = Mutex()

    private val mutexStoredMap = Mutex()
    private val mutexInstance = Mutex()
    private val mutexContact = Mutex()
    private val mutexContactNumbers = Mutex()
    private val mutexTrustedNumbers = Mutex()
    private val mutexOrganizations = Mutex()
    private val mutexMiscellaneous = Mutex()

    private val mutexCallDetails = Mutex()
    private val mutexSafeLogs = Mutex()

    /**
     * This Query does nothing but is called in order to initialize database
     */
    @WorkerThread
    suspend fun dummyQuery(): String? {
        return changeLogDao.dummyQuery()
    }

    /***********************************************************************************************
     * StoredMap Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun hasCredKey(instanceNumber : String) : Boolean {
        return storedMapDao.hasCredKey(instanceNumber)
    }

    @WorkerThread
    suspend fun getSessionID(number: String) : String? {
        var sessionID : String?
        mutexStoredMap.withLock {
            sessionID = storedMapDao.getSessionID(number)
        }
        return sessionID
    }

    /**
     * getClientKey() for retrieve UUID key to push and pull changes to / from server
     */
    @WorkerThread
    suspend fun getClientKey(userNumber: String): String? {
        val result : String?
        mutexStoredMap.withLock {
            result = storedMapDao.getCredKey(userNumber)
        }
        return result
    }

    @WorkerThread
    suspend fun getFireBaseToken(number : String) : String? {
        val result : String?
        mutexStoredMap.withLock {
            result = storedMapDao.getFireBaseToken(number)
        }
        return result
    }

    @WorkerThread
    suspend fun getLastSyncTime(number : String) : Long {
        val result : Long
        mutexStoredMap.withLock {
            result = storedMapDao.getLastSyncTime(number)
        }
        return result
    }

    @WorkerThread
    suspend fun insertStoredMap(storedMap : StoredMap) {
        mutexStoredMap.withLock {
            storedMapDao.insertStoredMap(storedMap)
        }
    }

    @WorkerThread
    suspend fun updateStoredMap(
        number : String,
        sessionID: String? = null,
        clientKey : String? = null,
        token : String? = null,
        lastSyncTime: Long? = null
    ) {
        mutexStoredMap.withLock {
            storedMapDao.updateStoredMap(number, sessionID, clientKey, token, lastSyncTime)
        }
    }

    /***********************************************************************************************
     * TODO: perhaps add the data analysis queries to the CallDetailDao and create an
     *  updateMiscellaneous() function (and perhaps an update call logs function), which will be
     *  called when the observer detects a change to the ACTUAL call logs.
     *
     * CallDetail Queries
     **********************************************************************************************/

    /**
     * getCallLogs() as an extra function just in case
     */
    @WorkerThread
    suspend fun getCallDetails(): List<CallDetail> {
        val result : List<CallDetail>
        mutexCallDetails.withLock {
            result = callDetailDao.getCallDetails()
        }
        return result
    }

    @WorkerThread
    suspend fun getCallDetailsPartial(amount: Int): List<CallDetail> {
        val result : List<CallDetail>
        mutexCallDetails.withLock {
            result = callDetailDao.getCallDetailsPartial(amount)
        }
        return result
    }

    @WorkerThread
    suspend fun getMostRecentCallDetailDate(): Long? {
        val result : Long?
        mutexCallDetails.withLock {
            result = callDetailDao.getMostRecentCallDetailDate()
        }
        return result
    }

    @WorkerThread
    suspend fun insertDetailSkeleton(callDetail: CallDetail) {
        mutexCallDetails.withLock {
            callDetailDao.insertDetailSkeleton(callDetail)
        }
    }

    @WorkerThread
    suspend fun insertDetailSync(instanceNumber: String, callDetail: CallDetail) : Boolean {
        var inserted = false
        mutexCallDetails.withLock {
            inserted = callDetailDao.insertDetailSync(instanceNumber, callDetail)
        }
        return inserted
    }

    /***********************************************************************************************
     * SafeLog Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getSafeLogs(): List<SafeLog> {
        val result : List<SafeLog>
        mutexSafeLogs.withLock {
            result = safeLogDao.getSafeLogs()
        }
        return result
    }

    /***********************************************************************************************
     * Instance Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun hasInstance() : Boolean {
        val result : Boolean
        mutexInstance.withLock {
            result = instanceDao.hasInstance()
        }
        return result
    }

    /***********************************************************************************************
     * Contact Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getAllContacts() : List<Contact> {
        val result : List<Contact>
        mutexContact.withLock {
            result = contactDao.getAllContacts()
        }
        return result
    }

    /***********************************************************************************************
     * ContactNumbers Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getAllContactNumbers() : List<ContactNumbers> {
        val result : List<ContactNumbers>
        mutexContactNumbers.withLock {
            result = contactNumbersDao.getAllContactNumbers()
        }
        return result
    }

    /***********************************************************************************************
     * ChangeLog Queries
     **********************************************************************************************/
    
    @WorkerThread
    suspend fun getLastChangeID() : Int? {
        val result : Int?
        mutexChange.withLock {
            result = changeLogDao.getLastChangeID()
        }
        return result
    }

    @WorkerThread
    suspend fun getAllChangeLogs(): List<ChangeLog> {
        val result : List<ChangeLog>
        mutexChange.withLock {
            result = changeLogDao.getAllChangeLogs()
        }
        return result
    }

    @WorkerThread
    suspend fun getChangeLogRow(changeID : String) : ChangeLog {
        val result : ChangeLog
        mutexChange.withLock {
            result = changeLogDao.getChangeLogRow(changeID)
        }
        return result
    }


    /***********************************************************************************************
     * QueueToUpload Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getAllQTUByRowID() : List<QueueToUpload> {
        val result : List<QueueToUpload>
        mutexUpload.withLock {
            result = queueToUploadDao.getAllQTU_rowID()
        }
        return result
    }

    @WorkerThread
    suspend fun getChunkQTUByRowID() : List<QueueToUpload> {
        val result : List<QueueToUpload>
        mutexUpload.withLock {
            result = queueToUploadDao.getChunkQTU_rowID()
        }
        return result
    }

    @WorkerThread
    suspend fun getAllQTU() : List<QueueToUpload> {
        val result : List<QueueToUpload>
        mutexUpload.withLock {
            result = queueToUploadDao.getAllQTU()
        }
        return result
    }

    @WorkerThread
    suspend fun hasQTUs() : Boolean {
        val result : Boolean
        mutexUpload.withLock {
            result = queueToUploadDao.hasQTUs()
        }
        return result
    }

    /**
     * Returns all QueueToUploads with error counter > 0
     */
    @WorkerThread
    suspend fun getQTUErrorLogs() : List<String> {
        val result : List<String>
        mutexUpload.withLock {
            result = queueToUploadDao.getQTUErrorLogs()
        }
        return result
    }

    @WorkerThread
    suspend fun deleteUploadInclusive(rowID : Int) {
        mutexUpload.withLock {
            queueToUploadDao.deleteUploadInclusive(rowID)
        }
    }

    @WorkerThread
    suspend fun deleteUploadExclusive(rowID : Int) {
        mutexUpload.withLock {
            queueToUploadDao.deleteUploadInclusive(rowID)
        }
    }

    /***********************************************************************************************
     * QueueToExecute Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getAllQTE() : List<QueueToExecute> {
        val result : List<QueueToExecute>
        mutexExecute.withLock {
            result = queueToExecuteDao.getAllQTEs()
        }
        return result
    }

    @WorkerThread
    suspend fun hasQTEs() : Boolean {
        val result : Boolean
        mutexExecute.withLock {
            result = executeAgentDao.hasQTEs()
        }
        return result
    }

    /**
     * Returns all QueueToExecutes with error counter > 0
     */
    @WorkerThread
    suspend fun getQTEErrorLogs() : List<String> {
        val result : List<String>
        mutexExecute.withLock {
            result = queueToExecuteDao.getQTEErrorLogs()
        }
        return result
    }

    /***********************************************************************************************
     * Agent Queries
     **********************************************************************************************/

    /**
     * executeAll() is called to execute the logs within QueueToExecute
     * Should be scheduled async and done when possible / there are logs left
     */
    @WorkerThread
    suspend fun executeAll() {
        executeAgentDao.executeAll(
            mutexExecute,
            mutexChange,
            mutexStoredMap,
            mutexInstance,
            mutexContact,
            mutexContactNumbers,
            mutexTrustedNumbers,
            mutexOrganizations,
            mutexMiscellaneous,
        )
    }

    /**
     * changeFromServer() should be called to handle changes that come from the server
     *
     * See documentation for changeAgentDao.changeFromServer()
     */
    @WorkerThread
    suspend fun changeFromServer(
        changeID : String,
        instanceNumber : String?,
        changeTime: Long,
        type: String,
        CID: String?,
        oldNumber : String?,
        number: String?,
        parentNumber: String?,
        trustability: Int?,
        counterValue: Int?,
        serverChangeID: Int
    ) {
        changeAgentDao.changeFromServer(
            changeID,
            instanceNumber,
            changeTime,
            type,
            CID,
            oldNumber,
            number,
            parentNumber,
            trustability,
            counterValue,
            serverChangeID
        )
    }

    /**
     * changeFromClient() should be called to handle changes that come from the client
     *
     * See documentation for changeAgentDao.changeFromClient()
     */
    @WorkerThread
    suspend fun changeFromClient(
        changeID : String,
        instanceNumber : String?,
        changeTime: Long,
        type: String,
        CID: String?,
        oldNumber : String?,
        number: String?,
        parentNumber: String?,
        trustability: Int?,
        counterValue: Int?
    ) {
        changeAgentDao.changeFromClient(
            changeID,
            instanceNumber,
            changeTime,
            type,
            CID,
            oldNumber,
            number,
            parentNumber,
            trustability,
            counterValue)
    }

    @WorkerThread
    suspend fun deleteQTU(changeID: String) {
        mutexUpload.withLock {
            uploadAgentDao.deleteQTU(changeID)
        }
    }
}