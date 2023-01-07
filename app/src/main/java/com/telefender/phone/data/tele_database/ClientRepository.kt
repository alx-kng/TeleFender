package com.telefender.phone.data.tele_database

import androidx.annotation.WorkerThread
import com.telefender.phone.data.tele_database.TeleLocks.mutexLocks
import com.telefender.phone.data.tele_database.client_daos.*
import com.telefender.phone.data.tele_database.entities.*
import kotlinx.coroutines.sync.withLock


class ClientRepository(

    /**
     * TODO: Maybe we shouldn't require some queries to run on a worker thread. For example,
     *  retrieving the tree entities or safe logs used to let a new call through may need to be
     *  more immediate.
     */
    private val executeAgentDao : ExecuteAgentDao,
    private val changeAgentDao : ChangeAgentDao,
    private val uploadAgentDao: UploadAgentDao,

    private val uploadChangeQueueDao : UploadChangeQueueDao,
    private val uploadAnalyzedQueueDao: UploadAnalyzedQueueDao,

    private val executeQueueDao : ExecuteQueueDao,
    private val changeLogDao : ChangeLogDao,
    private val storedMapDao : StoredMapDao,
    private val parametersDao : ParametersDao,

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
        mutexLocks[MutexType.STORED_MAP]!!.withLock {
            storedMapDao.updateStoredMap(
                number = number,
                sessionId = sessionID,
                clientKey = clientKey,
                fireBaseToken = token,
                lastSyncTime = lastSyncTime
            )
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
        mutexLocks[MutexType.CALL_DETAIL]!!.withLock {
            callDetailDao.insertDetailSkeleton(callDetail)
        }
    }

    /***********************************************************************************************
     * AnalyzedNumber Queries
     **********************************************************************************************/

    /**
     * Gets AnalyzedNumber given a number. Requires use of lock since it may initialize the
     * AnalyzedNumber for the number if the row didn't previously exist.
     *
     * NOTE: if not specified, instanceNumber is assumed to be user's number.
     */
    @WorkerThread
    suspend fun getAnalyzedNum(number: String, instanceNumber: String? = null) : AnalyzedNumber? {
        return mutexLocks[MutexType.ANALYZED]!!.withLock {
            analyzedNumberDao.getAnalyzedNum(number, instanceNumber)
        }
    }

    /**
     * Gets AnalyzedNumber given the rowID. Requires use of lock since it may initialize the
     * AnalyzedNumber for the number if the row didn't previously exist.
     */
    @WorkerThread
    suspend fun getAnalyzedNum(rowID: Int) : AnalyzedNumber? {
        return mutexLocks[MutexType.ANALYZED]!!.withLock {
            analyzedNumberDao.getAnalyzedNum(rowID)
        }
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
    suspend fun lastServerChangeID() : Int? {
        return changeLogDao.lastServerChangeID()
    }

    @WorkerThread
    suspend fun getAllChangeLogs(): List<ChangeLog> {
        return changeLogDao.getAllChangeLogs()
    }

    @WorkerThread
    suspend fun getChangeLog(changeID : String) : ChangeLog? {
        return changeLogDao.getChangeLog(changeID)
    }

    @WorkerThread
    suspend fun getChangeLog(rowID: Int) : ChangeLog? {
        return changeLogDao.getChangeLog(rowID)
    }


    /***********************************************************************************************
     * UploadChangeQueue Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getAllChangeQTUOrdered() : List<UploadChangeQueue> {
        return uploadChangeQueueDao.getAllChangeQTUOrdered()
    }

    @WorkerThread
    suspend fun getChunkChangeQTU(amount: Int) : List<UploadChangeQueue> {
        return uploadChangeQueueDao.getChunkChangeQTU(amount)
    }

    @WorkerThread
    suspend fun getAllChangeQTU() : List<UploadChangeQueue> {
        return uploadChangeQueueDao.getAllChangeQTU()
    }

    @WorkerThread
    suspend fun hasChangeQTU() : Boolean {
        return uploadChangeQueueDao.hasChangeQTU()
    }

    @WorkerThread
    suspend fun deleteChangeQTUInclusive(linkedRowID : Int) {
        mutexLocks[MutexType.UPLOAD_ANALYZED]!!.withLock {
            uploadChangeQueueDao.deleteChangeQTUInclusive(linkedRowID)
        }
    }

    @WorkerThread
    suspend fun deleteChangeQTUExclusive(linkedRowID : Int) {
        mutexLocks[MutexType.UPLOAD_ANALYZED]!!.withLock {
            uploadChangeQueueDao.deleteChangeQTUExclusive(linkedRowID)
        }
    }

    /***********************************************************************************************
     * UploadAnalyzedQueue Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getAllAnalyzedQTUOrdered() : List<UploadAnalyzedQueue> {
        return uploadAnalyzedQueueDao.getAllAnalyzedQTUOrdered()
    }

    @WorkerThread
    suspend fun getChunkAnalyzedQTU(amount: Int) : List<UploadAnalyzedQueue> {
        return uploadAnalyzedQueueDao.getChunkAnalyzedQTU(amount)
    }

    @WorkerThread
    suspend fun getAllAnalyzedQTU() : List<UploadAnalyzedQueue> {
        return uploadAnalyzedQueueDao.getAllAnalyzedQTU()
    }

    @WorkerThread
    suspend fun hasAnalyzedQTU() : Boolean {
        return uploadAnalyzedQueueDao.hasAnalyzedQTU()
    }

    @WorkerThread
    suspend fun deleteAnalyzedQTUInclusive(linkedRowID : Int) {
        mutexLocks[MutexType.UPLOAD_ANALYZED]!!.withLock {
            uploadAnalyzedQueueDao.deleteAnalyzedQTUInclusive(linkedRowID)
        }
    }

    @WorkerThread
    suspend fun deleteAnalyzedQTUExclusive(linkedRowID : Int) {
        mutexLocks[MutexType.UPLOAD_ANALYZED]!!.withLock {
            uploadAnalyzedQueueDao.deleteAnalyzedQTUExclusive(linkedRowID)
        }
    }

    /***********************************************************************************************
     * ExecuteQueue Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getAllQTE() : List<ExecuteQueue> {
        return executeQueueDao.getAllQTE()
    }

    @WorkerThread
    suspend fun hasQTE() : Boolean {
        return executeAgentDao.hasQTE()
    }

    /**
     * Returns all QueueToExecutes with error counter > 0
     */
    @WorkerThread
    suspend fun getQTEErrorLogs() : List<Int> {
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
     *
     * NOTE: if you would like to retry the transaction, you must do so yourself in the caller
     * function.
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
    suspend fun changeFromClient(changeLog: ChangeLog, fromSync: Boolean, bubbleError: Boolean = false) {
        changeAgentDao.changeFromClient(changeLog, fromSync, bubbleError)
    }
}