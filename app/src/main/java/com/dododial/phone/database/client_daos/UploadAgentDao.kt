package com.dododial.phone.database.client_daos

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.Dao
import androidx.room.Transaction
import com.dododial.phone.database.ClientRepository
import com.dododial.phone.database.MiscHelpers
import com.dododial.phone.database.entities.ChangeLog

@Deprecated("UploadAgentDao shouldn't be used. Use ServerHelpers")
@Dao
interface UploadAgentDao: InstanceDao, ContactDao, ContactNumbersDao,
    ChangeLogDao, QueueToExecuteDao, QueueToUploadDao {

    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun uploadAll(context : Context, repository: ClientRepository) {
        while (hasQTUs()) {
            uploadFirst(context, repository)
        }
    }
    /**
     * Finds first task to upload and passes it's corresponding ChangeLog to
     * helper function uploadToServer.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun uploadFirst(context: Context, repository: ClientRepository) {

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
            changeLog.counterValue,
            context, 
            repository
        )
    }
    /**
     * Takes ChangeLog arguments and uploads to server, deletes QTU if result code is success
     */
    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun uploadToServer(
        changeID: String,
        instanceNumber: String?,
        changeTime: Long,
        type: String,
        CID : String?,
        name : String?,
        oldNumber : String?,
        number : String?,
        parentNumber : String?,
        trustability : Int?,
        counterValue : Int?,
        context : Context,
        repository: ClientRepository
    ) {

        val cleanInstanceNumber = MiscHelpers.cleanNumber(instanceNumber)
        val cleanOldNumber = MiscHelpers.cleanNumber(oldNumber)
        val cleanNumber = MiscHelpers.cleanNumber(number)
        val cleanParentNumber = MiscHelpers.cleanNumber(parentNumber)

        val changeLog = ChangeLog(
            changeID,
            cleanInstanceNumber,
            changeTime,
            type,
            CID,
            name,
            cleanOldNumber,
            cleanNumber,
            cleanParentNumber,
            trustability,
            counterValue
        )

        //val changeLogAsJson = changeLogToJson(changeLog)
        val url = " " // TODO url?
        //uploadPostRequest(changeLogAsJson, url, context, repository)
        
        // TODO get result code from post to server so we can delete corresponding QTU
    }

    @Transaction
    open suspend fun deleteQTU(changeID : String) {
        deleteQTU_ChangeID(changeID)

    }
}

