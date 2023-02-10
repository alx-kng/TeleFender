package com.telefender.phone.data.tele_database

import androidx.annotation.WorkerThread
import com.telefender.phone.data.server_related.ServerData
import com.telefender.phone.data.tele_database.TeleLocks.mutexLocks
import com.telefender.phone.data.tele_database.client_daos.*
import com.telefender.phone.data.tele_database.entities.*
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.data.tele_database.entities.Contact
import com.telefender.phone.data.tele_database.entities.ContactNumber
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
    private val errorQueueDao: ErrorQueueDao,

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

    /**
     * Checks if database is initialized. Requires that the singleton StoredMap row exists (which
     * contains the user's number), an Instance row with the user's number exists, and the
     * singleton Parameters row exists.
     */
    @WorkerThread
    suspend fun databaseInitialized(): Boolean {
        val userNumber = storedMapDao.getUserNumber()
        return userNumber != null
            && instanceDao.hasInstance(userNumber)
            && parametersDao.getParameters() != null
    }

    /***********************************************************************************************
     * StoredMap Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getUserNumber() : String? {
        return storedMapDao.getUserNumber()
    }

    @WorkerThread
    suspend fun getSessionID() : String? {
        return storedMapDao.getStoredMap()?.sessionID
    }

    @WorkerThread
    suspend fun hasClientKey() : Boolean {
        return getClientKey() != null
    }

    @WorkerThread
    suspend fun getClientKey(): String? {
        return storedMapDao.getStoredMap()?.clientKey
    }

    @WorkerThread
    suspend fun getFireBaseToken() : String? {
        return storedMapDao.getStoredMap()?.firebaseToken
    }

    @WorkerThread
    suspend fun getLastLogSyncTime() : Long? {
        return storedMapDao.getStoredMap()?.lastLogSyncTime
    }

    @WorkerThread
    suspend fun getLastServerRowID() : Long? {
        return storedMapDao.getStoredMap()?.lastServerRowID
    }

    /**
     * Returns whether or not the update was successful.
     */
    @WorkerThread
    suspend fun updateStoredMap(
        sessionID: String? = null,
        clientKey: String? = null,
        firebaseToken: String? = null,
        lastLogSyncTime: Long? = null,
        lastServerRowID: Long? = null
    ) : Boolean {
        return mutexLocks[MutexType.STORED_MAP]!!.withLock {
            storedMapDao.updateStoredMap(
                sessionID = sessionID,
                clientKey = clientKey,
                firebaseToken = firebaseToken,
                lastLogSyncTime = lastLogSyncTime,
                lastServerRowID = lastServerRowID
            )
        }
    }

    /***********************************************************************************************
     * Parameters Queries
     **********************************************************************************************/

    /**
     * Gets Parameters associated with user.
     *
     * NOTE: It's STRONGLY advised that you put a try-catch around any use cases of this,
     * especially if you plan on non-null asserting the return, as there is a real possibility of
     * an error (especially if the database isn't yet initialized).
     */
    @WorkerThread
    suspend fun getParameters() : Parameters? {
        return parametersDao.getParameters()
    }

    @WorkerThread
    suspend fun updateParameters(
        shouldUploadAnalyzed: Boolean? = null,
        shouldUploadLogs: Boolean? = null,
        initialNotifyGate: Int? = null,
        verifiedSpamNotifyGate: Int? = null,
        superSpamNotifyGate: Int? = null,
        incomingGate: Int? = null,
        outgoingGate: Int? = null,
        smsImmediateWaitTime: Long? = null,
        smsDeferredWaitTime: Int? = null
    ) : Boolean {
        return mutexLocks[MutexType.PARAMETERS]!!.withLock {
            parametersDao.updateParameters(
                shouldUploadAnalyzed = shouldUploadAnalyzed,
                shouldUploadLogs = shouldUploadLogs,
                initialNotifyGate = initialNotifyGate,
                verifiedSpamNotifyGate = verifiedSpamNotifyGate,
                superSpamNotifyGate = superSpamNotifyGate,
                incomingGate = incomingGate,
                outgoingGate = outgoingGate,
                smsImmediateWaitTime = smsImmediateWaitTime,
                smsDeferredWaitTime = smsDeferredWaitTime
            )
        }
    }

    /***********************************************************************************************
     * CallDetail Queries
     **********************************************************************************************/

    /**
     * getCallLogs() as an extra function just in case
     */
    @WorkerThread
    suspend fun getCallDetails(instanceNumber: String? = null): List<CallDetail>? {
        return callDetailDao.getCallDetails(instanceNumber)
    }

    @WorkerThread
    suspend fun getCallDetailsPartial(instanceNumber: String? = null, amount: Int): List<CallDetail>? {
        return callDetailDao.getCallDetailsPartial(instanceNumber, amount)
    }

    @WorkerThread
    suspend fun getNewestCallDate(instanceNumber: String? = null): Long? {
        return callDetailDao.getNewestCallDate(instanceNumber)
    }

    /**
     * For inserting a CallDetail skeleton that contains info on the unallowed status of the call.
     *
     * NOTE: Should be wrapped in try-catch, as the underlying mechanism can throw exceptions.
     */
    @WorkerThread
    suspend fun insertCallDetailSkeleton(callDetail: CallDetail) {
        mutexLocks[MutexType.CALL_DETAIL]!!.withLock {
            callDetailDao.insertCallDetailSkeleton(callDetail)
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
            analyzedNumberDao.getAnalyzedNum(
                normalizedNumber = number,
                instanceParam = instanceNumber
            )
        }
    }

    /**
     * Gets AnalyzedNumber given the rowID. Doesn't auto-initialize.
     */
    @WorkerThread
    suspend fun getAnalyzedNum(rowID: Long) : AnalyzedNumber? {
        return analyzedNumberDao.getAnalyzedNum(rowID)

    }

    /**
     * Gets AnalyzedNumber given a number. Doesn't auto-initialize.
     *
     * NOTE: if not specified, instanceNumber is assumed to be user's number.
     */
    @WorkerThread
    suspend fun getAnalyzedNumForCheck(number: String, instanceNumber: String? = null) : AnalyzedNumber? {
        return analyzedNumberDao.getAnalyzedNumForCheck(
            normalizedNumber = number,
            instanceParam = instanceNumber
        )
    }

    @WorkerThread
    suspend fun getAllAnalyzedNum() : List<AnalyzedNumber> {
        return mutexLocks[MutexType.ANALYZED]!!.withLock {
            analyzedNumberDao.getAllAnalyzedNum()
        }
    }

    /***********************************************************************************************
     * Instance Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun hasInstance(instanceNumber: String) : Boolean {
        return instanceDao.hasInstance(instanceNumber)
    }

    @WorkerThread
    suspend fun getAllInstance() : List<Instance> {
        return instanceDao.getAllInstance()
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
    suspend fun getAllChangeLogs(): List<ChangeLog> {
        return changeLogDao.getAllChangeLogs()
    }

    @WorkerThread
    suspend fun getChangeLog(changeID : String) : ChangeLog? {
        return changeLogDao.getChangeLog(changeID)
    }

    @WorkerThread
    suspend fun getChangeLog(rowID: Long) : ChangeLog? {
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

    /**
     * Returns number of rows deleted or null.
     */
    @WorkerThread
    suspend fun deleteChangeQTUInclusive(linkedRowID: Long) : Int? {
        return mutexLocks[MutexType.UPLOAD_ANALYZED]!!.withLock {
            uploadChangeQueueDao.deleteChangeQTUInclusive(linkedRowID)
        }
    }

    /**
     * Returns number of rows deleted or null.
     */
    @WorkerThread
    suspend fun deleteChangeQTUExclusive(linkedRowID: Long) : Int? {
        return mutexLocks[MutexType.UPLOAD_ANALYZED]!!.withLock {
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

    /**
     * Returns number of rows deleted or null.
     */
    @WorkerThread
    suspend fun deleteAnalyzedQTUInclusive(linkedRowID: Long) : Int? {
        return mutexLocks[MutexType.UPLOAD_ANALYZED]!!.withLock {
            uploadAnalyzedQueueDao.deleteAnalyzedQTUInclusive(linkedRowID)
        }
    }

    /**
     * Returns number of rows deleted or null.
     */
    @WorkerThread
    suspend fun deleteAnalyzedQTUExclusive(linkedRowID: Long) : Int? {
        return mutexLocks[MutexType.UPLOAD_ANALYZED]!!.withLock {
            uploadAnalyzedQueueDao.deleteAnalyzedQTUExclusive(linkedRowID)
        }
    }

    /***********************************************************************************************
     * ErrorQueue Queries
     **********************************************************************************************/

    @WorkerThread
    suspend fun getAllErrorLogOrdered() : List<ErrorQueue> {
        return errorQueueDao.getAllErrorLogOrdered()
    }

    @WorkerThread
    suspend fun getChunkErrorLog(amount: Int) : List<ErrorQueue> {
        return errorQueueDao.getChunkErrorLog(amount)
    }

    @WorkerThread
    suspend fun getAllErrorLog() : List<ErrorQueue> {
        return errorQueueDao.getAllErrorLog()
    }

    @WorkerThread
    suspend fun hasErrorLog() : Boolean {
        return errorQueueDao.hasErrorLog()
    }

    /**
     * Returns number of rows deleted or null.
     */
    @WorkerThread
    suspend fun deleteErrorLogInclusive(linkedRowID: Long) : Int? {
        return mutexLocks[MutexType.ERROR_LOG]!!.withLock {
            errorQueueDao.deleteErrorLogInclusive(linkedRowID)
        }
    }

    /**
     * Returns number of rows deleted or null.
     */
    @WorkerThread
    suspend fun deleteErrorLogExclusive(linkedRowID: Long) : Int? {
        return mutexLocks[MutexType.ERROR_LOG]!!.withLock {
            errorQueueDao.deleteErrorLogExclusive(linkedRowID)
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
    suspend fun changeFromServer(serverData: ServerData) : Boolean {
        return changeAgentDao.changeFromServer(serverData)
    }


    /**
     * Used to add CallDetail to database and update AnalyzedNumber. Returns whether the CallDetail
     * was inserted or not (may not be inserted if log already exists and is synced).
     *
     * NOTE: Throws Exception if the Sync didn't go through, so higher level function must wrap
     * with try-catch.
     *
     * NOTE: if you would like to retry the transaction, you must do so yourself in the caller
     * function.
     */
    @WorkerThread
    suspend fun callFromClient(callDetail: CallDetail) : Boolean {
        return changeAgentDao.callFromClient(callDetail)
    }

    /**
     * changeFromClient() should be called to handle changes that come from the client
     * See documentation for changeAgentDao.changeFromClient(). Locks handled at ExecuteAgent level.
     */
    @WorkerThread
    suspend fun changeFromClient(
        changeLog: ChangeLog,
        fromSync: Boolean = false,
        bubbleError: Boolean = false
    ) {
        changeAgentDao.changeFromClient(changeLog, fromSync, bubbleError)
    }
}