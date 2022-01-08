package com.dododial.phone.database

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import com.dododial.phone.database.client_daos.ChangeAgentDao
import com.dododial.phone.database.client_daos.*
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
    ) {

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
    @WorkerThread
    suspend fun uploadFirst() {
        uploadAgentDao.uploadFirst()
    }

    /**
     * executeFirst() is called to execute the first log within QueueToExecute
     * Should be scheduled async and done when possible / there are logs left
     */
    @WorkerThread
    suspend fun executeFirst() {
        executeAgentDao.executeFirst()
    }

    /**
     * changeFromServer() should be called to handle changes that come from the server
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
     * getQTEErrorLogs() and getQTUErrorLogs() get all logs with error counters > 0
     * in the QueueToExecute and QueueToUpload tables respectively
     */
    @WorkerThread
    suspend fun getQTEErrorLogs() : List<String> {
        return queueToExecuteDao.getQTEErrorLogs()
    }

    @WorkerThread
    suspend fun getQTUErrorLogs() : List<String> {
        return queueToUploadDao.getQTUErrorLogs()
    }        
}