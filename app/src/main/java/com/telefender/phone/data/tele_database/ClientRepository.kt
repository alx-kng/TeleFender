package com.telefender.phone.data.tele_database

import androidx.annotation.WorkerThread
import com.telefender.phone.data.tele_database.TeleLocks.mutexCallDetails
import com.telefender.phone.data.tele_database.TeleLocks.mutexStoredMap
import com.telefender.phone.data.tele_database.TeleLocks.mutexUpload
import com.telefender.phone.data.tele_database.client_daos.*
import com.telefender.phone.data.tele_database.entities.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class MutexType {
    EXECUTE, UPLOAD, CHANGE, STORED_MAP, INSTANCE, CONTACT, CONTACT_NUMBER, CALL_DETAIL, ANALYZED,
    SYNC
}

/**
 * Mutex locks to prevent conflicting access to same table, which makes our database thread safe
 * (even if Room is already supposedly thread safe). Note that we only locks on writing queries,
 * that is, insert / update / delete queries.
 */
object TeleLocks {
    val mutexExecute = Mutex()
    val mutexUpload = Mutex()
    val mutexChange = Mutex()
    val mutexStoredMap = Mutex()

    val mutexCallDetails = Mutex()
    val mutexInstance = Mutex()
    val mutexContact = Mutex()
    val mutexContactNumber = Mutex()
    val mutexAnalyzed = Mutex()

    val mutexSync = Mutex()

    val mutexLocks = mapOf(
        MutexType.EXECUTE to mutexExecute,
        MutexType.UPLOAD to mutexUpload,
        MutexType.CHANGE to mutexChange,
        MutexType.STORED_MAP to mutexStoredMap,
        MutexType.CALL_DETAIL to mutexCallDetails,
        MutexType.INSTANCE to mutexInstance,
        MutexType.CONTACT to mutexContact,
        MutexType.CONTACT_NUMBER to mutexContactNumber,
        MutexType.ANALYZED to mutexAnalyzed,
        MutexType.SYNC to mutexSync
    )
}

class ClientRepository(

    /**
     * TODO: Maybe we shouldn't require some queries to run on a worker thread. For example,
     *  retrieving the tree entities or safe logs used to let a new call through may need to be
     *  more immediate.
     */
    private val executeAgentDao : ExecuteAgentDao,
    private val changeAgentDao : ChangeAgentDao,
    private val uploadAgentDao: UploadAgentDao,

    private val executeQueueDao : ExecuteQueueDao,
    private val uploadQueueDao : UploadQueueDao,
    private val changeLogDao : ChangeLogDao,
    private val storedMapDao : StoredMapDao,

    private val callDetailDao : CallDetailDao,
    private val instanceDao : InstanceDao,
    private val contactDao : ContactDao,
    private val contactNumberDao : ContactNumberDao,
    private val analyzedNumberDao : AnalyzedNumberDao
    ) {

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
        return storedMapDao.getSessionID(number)
    }

    @WorkerThread
    suspend fun getInstanceNumber() : String? {
        return storedMapDao.getUserNumber()
    }

    /**
     * getClientKey() for retrieve UUID key to push and pull changes to / from server
     */
    @WorkerThread
    suspend fun getClientKey(userNumber: String): String? {
        return storedMapDao.getCredKey(userNumber)
    }

    @WorkerThread
    suspend fun getFireBaseToken(number : String) : String? {
        return storedMapDao.getFireBaseToken(number)
    }

    @WorkerThread
    suspend fun getLastSyncTime(number : String) : Long {
        return storedMapDao.getLastSyncTime(number)
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
        return callDetailDao.getCallDetails()
    }

    @WorkerThread
    suspend fun getCallDetailsPartial(amount: Int): List<CallDetail> {
        return callDetailDao.getCallDetailsPartial(amount)
    }

    @WorkerThread
    suspend fun getMostRecentCallDetailDate(): Long? {
        return callDetailDao.getMostRecentCallDetailDate()
    }

    @WorkerThread
    suspend fun insertDetailSkeleton(callDetail: CallDetail) {
        mutexCallDetails.withLock {
            callDetailDao.insertDetailSkeleton(callDetail)
        }
    }

    /***********************************************************************************************
     * AnalyzedNumber Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getAnalyzed(number: String) : AnalyzedNumber {
        return analyzedNumberDao.getAnalyzed(number)
    }

    /***********************************************************************************************
     * Instance Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun hasInstance(instanceNumber: String) : Boolean {
        return instanceDao.hasInstance(instanceNumber)
    }

    /***********************************************************************************************
     * Contact Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getAllContacts() : List<Contact> {
        return contactDao.getAllContacts()
    }

    /***********************************************************************************************
     * ContactNumber Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getAllContactNumbers() : List<ContactNumber> {
        return contactNumberDao.getAllContactNumbers()
    }

    /***********************************************************************************************
     * ChangeLog Queries
     **********************************************************************************************/
    
    @WorkerThread
    suspend fun getLastChangeID() : Int? {
        return changeLogDao.getLastChangeID()
    }

    @WorkerThread
    suspend fun getAllChangeLogs(): List<ChangeLog> {
        return changeLogDao.getAllChangeLogs()
    }

    @WorkerThread
    suspend fun getChangeLogRow(changeID : String) : ChangeLog {
        return changeLogDao.getChangeLogRow(changeID)
    }


    /***********************************************************************************************
     * UploadQueue Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getAllQTUByRowID() : List<UploadQueue> {
        return uploadQueueDao.getAllQTU_rowID()
    }

    @WorkerThread
    suspend fun getChunkQTUByRowID() : List<UploadQueue> {
        return uploadQueueDao.getChunkQTU_rowID()
    }

    @WorkerThread
    suspend fun getAllQTU() : List<UploadQueue> {
        return uploadQueueDao.getAllQTU()
    }

    @WorkerThread
    suspend fun hasQTUs() : Boolean {
        return uploadQueueDao.hasQTUs()
    }

    /**
     * Returns all QueueToUploads with error counter > 0
     */
    @WorkerThread
    suspend fun getQTUErrorLogs() : List<String> {
        return uploadQueueDao.getQTUErrorLogs()
    }

    @WorkerThread
    suspend fun deleteUploadInclusive(rowID : Int) {
        mutexUpload.withLock {
            uploadQueueDao.deleteUploadInclusive(rowID)
        }
    }

    @WorkerThread
    suspend fun deleteUploadExclusive(rowID : Int) {
        mutexUpload.withLock {
            uploadQueueDao.deleteUploadInclusive(rowID)
        }
    }

    /***********************************************************************************************
     * ExecuteQueue Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getAllQTE() : List<ExecuteQueue> {
        return executeQueueDao.getAllQTEs()
    }

    @WorkerThread
    suspend fun hasQTEs() : Boolean {
        return executeAgentDao.hasQTEs()
    }

    /**
     * Returns all QueueToExecutes with error counter > 0
     */
    @WorkerThread
    suspend fun getQTEErrorLogs() : List<String> {
        return executeQueueDao.getQTEErrorLogs()
    }

    /***********************************************************************************************
     * Agent Queries
     **********************************************************************************************/

    /**
     * executeAll() is called to execute the logs within ExecuteQueue
     * Should be scheduled async and done when possible / there are logs left
     */
    @WorkerThread
    suspend fun executeAll() {
        executeAgentDao.executeAll()
    }

    /**
     * changeFromServer() should be called to handle changes that come from the server
     * See documentation for changeAgentDao.changeFromServer(). Locks handled at ExecuteAgent level.
     */
    @WorkerThread
    suspend fun changeFromServer(changeLog: ChangeLog) {
        changeAgentDao.changeFromServer(changeLog)
    }

    /**
     * Used to add CallDetail to database and update AnalyzedNumber. Returns whether the CallDetail
     * was inserted or not (may not be inserted if log already exists and is synced).
     */
    @WorkerThread
    suspend fun callFromClient(callDetail: CallDetail) : Boolean {
        return changeAgentDao.callFromClient(callDetail)
    }

    /**
     * TODO: Do we even need fromSync option in repository?
     *  Do we even need to have changeFromClient() in the repository for that matter?
     *
     * changeFromClient() should be called to handle changes that come from the client
     * See documentation for changeAgentDao.changeFromClient(). Locks handled at ExecuteAgent level.
     */
    @WorkerThread
    suspend fun changeFromClient(changeLog: ChangeLog, fromSync: Boolean) {
        changeAgentDao.changeFromClient(changeLog, fromSync)
    }

    @WorkerThread
    suspend fun deleteQTU(changeID: String) {
        mutexUpload.withLock {
            uploadAgentDao.deleteQTU(changeID)
        }
    }
}