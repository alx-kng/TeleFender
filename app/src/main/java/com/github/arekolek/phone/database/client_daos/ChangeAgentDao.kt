package com.github.arekolek.phone.database.client_daos

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.Dao
import androidx.room.Transaction
import com.github.arekolek.phone.database.ChangeLog
import com.github.arekolek.phone.database.ClientDBConstants.RESPONSE_OK
import com.github.arekolek.phone.database.client_daos.*
import com.github.arekolek.phone.database.QueueToExecute
import com.github.arekolek.phone.database.QueueToUpload

@Dao
abstract class ChangeAgentDao: ChangeLogDao, QueueToExecuteDao, QueueToUploadDao {

    @RequiresApi(Build.VERSION_CODES.R)
    @Transaction
    open suspend fun changeFromServer(
        changeID : String,
        instanceNumber : String,
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
        instanceNumber : String,
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
        val changeLog = ChangeLog(changeID, instanceNumber, changeTime, type,
            CID, name, oldNumber, number, parentNumber, trustability, counterValue)

        val execLog = QueueToExecute(changeID, changeTime)

        val upLog = QueueToUpload(changeID, changeTime)

        insertChangeLog(changeLog)
        insertQTE(execLog)
        insertQTU(upLog)
    }


}