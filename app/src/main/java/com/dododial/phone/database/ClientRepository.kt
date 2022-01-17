package com.dododial.phone.database

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.work.WorkInfo
import com.dododial.phone.database.client_daos.ChangeAgentDao
import com.dododial.phone.database.client_daos.*
import com.dododial.phone.database.entities.ChangeLog
import com.dododial.phone.database.entities.CallLog
import com.dododial.phone.database.entities.KeyStorage
import com.dododial.phone.database.entities.QueueToUpload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ClientRepository(
    private val callLogDao : CallLogDao,
    private val changeAgentDao : ChangeAgentDao,
    private val uploadAgentDao: UploadAgentDao,
    private val changeLogDao : ChangeLogDao,
    private val executeAgentDao : ExecuteAgentDao,
    private val keyStorageDao : KeyStorageDao,
    private val queueToExecuteDao : QueueToExecuteDao,
    private val queueToUploadDao : QueueToUploadDao,
    private val instanceDao : InstanceDao
    ) {

    /**
     * Mutex locks to prevent conflicting access to same table
     * Makes our database thread safe (even if Room supposedly thread safe)
     */
    private val mutexExecute = Mutex()
    private val mutexUpload = Mutex()
    private val mutexChange = Mutex()

    private val mutexKey = Mutex()
    private val mutexInstance = Mutex()
    private val mutexContact = Mutex()
    private val mutexContactNumbers = Mutex()
    private val mutexTrustedNumbers = Mutex()
    private val mutexOrganizations = Mutex()
    private val mutexMiscellaneous = Mutex()

    private val mutexCall = Mutex()

    /**
     * This Query does nothing but is called in order to initialize database
     */
    @WorkerThread
    suspend fun dummyQuery(): String? {
        return changeLogDao.dummyQuery()
    }

    /*
    TODO: perhaps add the data analysis queries to the CallLogDao and create an
     updateMiscellaneous() function (and perhaps an update call logs function), which will be called when the an observer
     detects a change to the call logs. NEEDS TO BE FROM ACTUAL CALL LOGS
     */
    /**
     * getCallLogs() as an extra function just in case
     */
    @WorkerThread
    suspend fun updateMiscNumbers() {
    }

    @WorkerThread
    suspend fun getCallLogs(): List<CallLog> {
        val result : List<CallLog>
        mutexCall.withLock {
            result = callLogDao.getCallLogs()
        }
        return result
    }

    /**
     * getClientKey() for retrieve UUID key to push and pull changes to / from server
     */
    @WorkerThread
    suspend fun getClientKey(userNumber: String): String? {
        val result : String?
        mutexKey.withLock {
            result = keyStorageDao.getCredKey(userNumber)
        }
        return result
    }
    
    @WorkerThread
    suspend fun getLastChangeID() : Int? {
        val result : Int?
        mutexChange.withLock {
            result = changeLogDao.getLastChangeID()
        }
        return result
    }
    /**
     * getChangeLog() and getAllChangeLogs() for monitoring changes
     */
    @WorkerThread
    suspend fun getChangeLog(changeID: String): ChangeLog {
        val result : ChangeLog
        mutexChange.withLock {
            result = changeLogDao.getChangeLogRow(changeID)
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
    suspend fun getAllQTU() : List<QueueToUpload> {
        val result : List<QueueToUpload>
        mutexUpload.withLock {
            result = queueToUploadDao.getAllQTU()
        }
        return result
    }

    /**
     * executeAll() is called to execute the logs within QueueToExecute
     * Should be scheduled async and done when possible / there are logs left
     */
    @WorkerThread
    suspend fun executeAll() {
        executeAgentDao.executeAll(
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
    
    @WorkerThread
    suspend fun hasQTEs() : Boolean {
        val result : Boolean
        mutexExecute.withLock {
            result = executeAgentDao.hasQTEs()
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

    @WorkerThread
    suspend fun hasInstance() : Boolean {
        val result : Boolean
        mutexInstance.withLock {
            result = instanceDao.hasInstance()
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

    @WorkerThread
    suspend fun deleteQTU(changeID: String) {
        mutexUpload.withLock {
            uploadAgentDao.deleteQTU(changeID)
        }
    }

    @WorkerThread
    suspend fun insertKey(keyStorage : KeyStorage) {
        mutexKey.withLock {
            keyStorageDao.insertKey(keyStorage)
        }
    }

    @WorkerThread
    suspend fun updateKey(number : String, clientKey : String?) {
        mutexKey.withLock {
            keyStorageDao.updateKey(number, clientKey)
        }
    }

    @WorkerThread
    suspend fun getSessionID(number: String) : String {
        var sessionID : String
        mutexKey.withLock {
            sessionID = keyStorageDao.getSessionID(number)
        }
        return sessionID
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
        name: String?,
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
            name,
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
        name: String?,
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
            name,
            oldNumber,
            number,
            parentNumber,
            trustability,
            counterValue)
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

    @WorkerThread
    suspend fun getAllQTUByRowID() : List<QueueToUpload> {
        val result : List<QueueToUpload>
        mutexUpload.withLock {
            result = queueToUploadDao.getAllQTU_rowID()
        }
        return result
    }
}