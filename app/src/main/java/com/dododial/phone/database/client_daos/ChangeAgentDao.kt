package com.dododial.phone.database.client_daos

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.Dao
import androidx.room.Transaction
import com.dododial.phone.database.entities.ChangeLog
import com.dododial.phone.database.ClientDBConstants.RESPONSE_OK
import com.dododial.phone.database.MiscHelpers
import com.dododial.phone.database.entities.QueueToExecute
import com.dododial.phone.database.entities.QueueToUpload

@Dao
abstract class ChangeAgentDao: ChangeLogDao, QueueToExecuteDao, QueueToUploadDao {

    /**
     * Function to handle a change (in the form of a ChangeLog's argument) from Server.
     * Adds change to the ChangeLog and QueueToExecute.
     */
    @Transaction
    open suspend fun changeFromServer(
        changeID : String,
        instanceNumber : String?,
        changeTime: Long,
        type: String,
        CID: String?,
        name: String?,
        oldNumber: String?,
        number: String?,
        parentNumber: String?,
        trustability: Int?,
        counterValue: Int?,
        serverChangeID: Int
    ) : Int {

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
            null,
            cleanOldNumber,
            cleanNumber,
            cleanParentNumber,
            trustability,
            counterValue,
            serverChangeID
        )

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
        changeTime: Long,
        type: String,
        CID: String?,
        name: String?,
        oldNumber : String?,
        number: String?,
        parentNumber: String?,
        trustability: Int?,
        counterValue: Int?,
    ) {
        val cleanInstanceNumber = MiscHelpers.cleanNumber(instanceNumber)
        val cleanOldNumber = MiscHelpers.cleanNumber(oldNumber)
        val cleanNumber = MiscHelpers.cleanNumber(number)
        val cleanParentNumber = MiscHelpers.cleanNumber(parentNumber)
        
        //Log.i("DODODEBUG ChangeAgentDAO", "received changeFromClient() call")
        val changeLog = ChangeLog(
            changeID,
            cleanInstanceNumber,
            changeTime,
            type,
            CID,
            null,
            cleanOldNumber,
            cleanNumber,
            cleanParentNumber,
            trustability,
            counterValue)

        insertChangeLog(changeLog)

        val execLog = QueueToExecute(changeID, changeTime)

        val upLog = QueueToUpload(changeID, changeTime, getRowID(changeID))

        insertQTE(execLog)
        insertQTU(upLog)
    }


}