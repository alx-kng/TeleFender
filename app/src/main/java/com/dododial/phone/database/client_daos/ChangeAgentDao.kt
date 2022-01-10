package com.dododial.phone.database.client_daos

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.room.Dao
import androidx.room.Transaction
import com.dododial.phone.database.ChangeLog
import com.dododial.phone.database.ClientDBConstants.RESPONSE_OK
import com.dododial.phone.database.QueueToExecute
import com.dododial.phone.database.QueueToUpload

@Dao
abstract class ChangeAgentDao: ChangeLogDao, QueueToExecuteDao, QueueToUploadDao {

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

        //Log.i("DODODEBUG ChangeAgentDAO WATCH", "inserting changelog: " + changeLog.toString())
        val execLog = QueueToExecute(changeID, changeTime)

        val upLog = QueueToUpload(changeID, changeTime)

        insertChangeLog(changeLog)
        insertQTE(execLog)
        insertQTU(upLog)
        //Log.i("DODODEBUG ChangeAgentDAO", "finished ChangeFromClient() Call (changeLog inserted into database hopefully)")
    }


}