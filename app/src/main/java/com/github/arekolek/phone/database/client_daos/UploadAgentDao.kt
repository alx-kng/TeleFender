package com.github.arekolek.phone.database.client_daos

import androidx.room.Dao
import androidx.room.Transaction

@Dao
interface UploadAgentDao: InstanceDao, ContactDao, ContactNumbersDao,
    ChangeLogDao, QueueToExecuteDao, QueueToUploadDao {

    suspend fun uploadFirst() {

        val firstJob = getFirstQTU()
        val firstID = firstJob.changeID
        updateQTUErrorCounter_Delta(firstID, 1)

        val changeLog = getChangeLogRow(firstID)

        uploadToServer(
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

    suspend fun uploadToServer(
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
        // TODO convert to json of data and call some function to upload data to server
        println("uploaded to server")
    }

    @Transaction
    open suspend fun deleteQTU(changeID : String) {
        deleteQTU_ChangeID(changeID)
    }
}

