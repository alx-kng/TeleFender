package com.dododial.phone.database.client_daos

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.Dao
import androidx.room.Transaction
import com.dododial.phone.database.MiscHelpers
import com.dododial.phone.database.background_tasks.UploadHelpers.changeLogToJson
import com.dododial.phone.database.background_tasks.UploadHelpers.sendPostRequest
import com.dododial.phone.database.entities.ChangeLog

@Dao
interface UploadAgentDao: InstanceDao, ContactDao, ContactNumbersDao,
    ChangeLogDao, QueueToExecuteDao, QueueToUploadDao {

    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun uploadAll(context : Context) {
        while (hasQTUs()) {
            uploadFirst(context)
        }
    }
    /**
     * Finds first task to upload and passes it's corresponding ChangeLog to
     * helper function uploadToServer.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun uploadFirst(context: Context) {

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

            context
        )
    }
    /**
     * Takes ChangeLog arguments and uploads to server, then (TODO) deletes from QTU
     */
    @RequiresApi(Build.VERSION_CODES.N)
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
        counterValue : Int?,

        context : Context
    ) {

        val cleanInstanceNumber = MiscHelpers.cleanNumber(instanceNumber)
        val cleanOldNumber = MiscHelpers.cleanNumber(oldNumber)
        val cleanNumber = MiscHelpers.cleanNumber(number)
        val cleanParentNumber = MiscHelpers.cleanNumber(parentNumber)

        // TODO convert to json of data and call some function to upload data to server
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

        val changeLogAsJson = changeLogToJson(changeLog)
        val url = " " // TODO url?
        sendPostRequest(changeLogAsJson, url, context)

    }

    @Transaction
    open suspend fun deleteQTU(changeID : String) {
        deleteQTU_ChangeID(changeID)
    }
}

