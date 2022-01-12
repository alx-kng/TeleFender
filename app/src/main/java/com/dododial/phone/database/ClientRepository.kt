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
import kotlinx.coroutines.flow.Flow

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
     * flowCallLogs and updateMiscNumbers() to monitor changes to call logs and update
     * the Miscellaneous table with new analysed numbers
     *
     * getCallLogs() as an extra function just in case
     */
    val flowCallLogs: Flow<List<CallLog>> = callLogDao.getFlowCallLogs()

    @WorkerThread
    suspend fun updateMiscNumbers() {
    }

    @WorkerThread
    suspend fun getCallLogs(): List<CallLog> {
        return callLogDao.getCallLogs()
    }

    /**
     * getClientKey() for retrieve UUID key to push and pull changes to / from server
     */
    @WorkerThread
    suspend fun getClientKey(userNumber: String): String {
        return keyStorageDao.getCredKey(userNumber)
    }

    /**
     * getChangeLog() and getAllChangeLogs() for monitoring changes
     */
    @WorkerThread
    suspend fun getChangeLog(changeID: String): ChangeLog {
        return changeLogDao.getChangeLogRow(changeID)
    }

    @WorkerThread
    suspend fun getAllChangeLogs(): List<ChangeLog> {
        return changeLogDao.getAllChangeLogs()
    }


    /**
     * uploadFirst() is called to upload the first log within QueueToUpload to the server
     * Should be scheduled async and done when possible / there are logs left
     */
    @RequiresApi(Build.VERSION_CODES.N)
    @WorkerThread
    suspend fun uploadFirst(context : Context) {
        uploadAgentDao.uploadFirst(context)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @WorkerThread
    suspend fun uploadAll(context : Context) {
        uploadAgentDao.uploadAll(context)
    }
    /**
     * executeFirst() is called to execute the first log within QueueToExecute
     * Should be scheduled async and done when possible / there are logs left
     */
    @WorkerThread
    suspend fun executeFirst() {
        executeAgentDao.executeFirst()
    }
    
    @WorkerThread
    suspend fun executeAll() {
        executeAgentDao.executeAll()
    }
    
    @WorkerThread
    suspend fun hasQTEs() : Boolean {
        return executeAgentDao.hasQTEs()
    }

    @WorkerThread
    suspend fun hasInstance() : Boolean {
        return instanceDao.hasInstance()
    }
    /**
     * changeFromServer() should be called to handle changes that come from the server
     *
     * See documentation for changeAgentDao.changeFromServer()
     */
    @RequiresApi(Build.VERSION_CODES.R)
    @WorkerThread
    suspend fun changeFromServer(
        changeID : String,
        instanceNumber : String?,
        changeTime: String,
        type: String,
        CID: String?,
        name: String?,
        oldNumber : String?,
        number: String?,
        parentNumber: String?,
        trustability: Int?,
        counterValue: Int?
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
            counterValue
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
        changeTime: String,
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
        return queueToExecuteDao.getQTEErrorLogs()
    }

    /**
     * Returns all QueueToUploads with error counter > 0
     */
    @WorkerThread
    suspend fun getQTUErrorLogs() : List<String> {
        return queueToUploadDao.getQTUErrorLogs()
    }        
}