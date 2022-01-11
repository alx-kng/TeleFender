package com.dododial.phone.database.client_daos

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.room.Dao
import androidx.room.Transaction
import com.dododial.phone.database.entities.ChangeLog
import com.dododial.phone.database.ClientDBConstants.RESPONSE_OK
import com.dododial.phone.database.entities.QueueToExecute
import com.dododial.phone.database.entities.QueueToUpload

@Dao
abstract class ChangeAgentDao: ChangeLogDao, QueueToExecuteDao, QueueToUploadDao {

    /**
     * Function to handle a change (in the form of a ChangeLog's argument) from Server.
     * Adds change to the ChangeLog and QueueToExecute.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    @Transaction
    open suspend fun changeFromServer(
        changeID : String,
        instanceNumber : String?,
        changeTime: String,
        type: String,
        CID: String?,
        name: String?,
        oldNumber: String?,
        number: String?,
        parentNumber: String?,
        trustability: Int?,
        counterValue: Int?
    ) : Int {
        val changeLog = ChangeLog(changeID, instanceNumber, changeTime, type,
            CID, name, oldNumber, number, parentNumber, trustability, counterValue)

        val execLog = QueueToExecute(changeID, changeTime)

        insertChangeLog(changeLog)
        insertQTE(execLog)

        return RESPONSE_OK
    }

    /**
     * Function to handle a change (in the form of a ChangeLog's arguments) from Client.
     * Adds change to the ChangeLog and QueueToUpload.
     */
    @Transaction
    open suspend fun changeFromClient(
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
        counterValue: Int?,
    ) {
        //Log.i("DODODEBUG ChangeAgentDAO", "recieved changeFromClient() call")
        val changeLog = ChangeLog(changeID, instanceNumber, changeTime, type,
            CID, name, oldNumber, number, parentNumber, trustability, counterValue)

        val execLog = QueueToExecute(changeID, changeTime)

        val upLog = QueueToUpload(changeID, changeTime)

        insertChangeLog(changeLog)
        insertQTE(execLog)
        insertQTU(upLog)
    }


}