package com.telefender.phone.data.tele_database.client_daos

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.Dao
import androidx.room.Transaction
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.helpers.MiscHelpers

// TODO: Can we still use UploadAgent? It seems like it could be a good structure.
@Deprecated("UploadAgentDao shouldn't be used. Use ServerInteractions")
@Dao
interface UploadAgentDao: InstanceDao, ContactDao, ContactNumberDao,
    ChangeLogDao, ExecuteQueueDao, UploadQueueDao {

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
            changeLog,
            context, 
            repository
        )
    }
    /**
     * Takes ChangeLog arguments and uploads to server, deletes QTU if result code is success
     */
    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun uploadToServer(
        changeLog: ChangeLog,
        context : Context,
        repository: ClientRepository
    ) {

        with(changeLog) {
            val cleanedChangeLog = ChangeLog(
                changeID = changeID,
                instanceNumber = instanceNumber,
                changeTime = changeTime,
                type = type,
                CID = CID,
                oldNumber = oldNumber,
                normalizedNumber = normalizedNumber,
                degree = degree,
                counterValue = counterValue,
                errorCounter = errorCounter,
                serverChangeID = serverChangeID,
            )

            //val changeLogAsJson = changeLogToJson(changeLog)
            val url = " " // TODO url?
            //uploadPostRequest(changeLogAsJson, url, context, repository)

            // TODO get result code from post to server so we can delete corresponding QTU
        }
    }

    @Transaction
    open suspend fun deleteQTU(changeID : String) {
        deleteQTU_ChangeID(changeID)

    }
}

