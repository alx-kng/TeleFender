package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Transaction
import com.telefender.phone.data.tele_database.ClientDBConstants.RESPONSE_OK
import com.telefender.phone.data.tele_database.MutexType
import com.telefender.phone.data.tele_database.TeleLocks.mutexLocks
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.data.tele_database.entities.ExecuteQueue
import com.telefender.phone.data.tele_database.entities.UploadQueue
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.sync.withLock

@Dao
abstract class ChangeAgentDao: ChangeLogDao, ExecuteAgentDao, ExecuteQueueDao, UploadQueueDao {


    /**
     * TODO: HANDLE TRANSACTIONS FOR RETRY.
     * TODO: Do we still need to clean here??
     *
     * Function to handle a change (as a ChangeLog) from Server.
     * Adds change to the ChangeLog and ExecuteQueue.
     */
    @Transaction
    open suspend fun changeFromServer(changeLog: ChangeLog) : Int{
        with(changeLog) {
            val cleanInstanceNumber = MiscHelpers.cleanNumber(instanceNumber)
            val cleanOldNumber = MiscHelpers.cleanNumber(oldNumber)
            val cleanNumber = MiscHelpers.cleanNumber(number)

            val cleanedChangeLog = this.copy(
                instanceNumber = cleanInstanceNumber,
                oldNumber = cleanOldNumber,
                number = cleanNumber
            )

            mutexLocks[MutexType.CHANGE]!!.withLock {
                insertChangeLog(cleanedChangeLog)
            }

            mutexLocks[MutexType.EXECUTE]!!.withLock {
                val execLog = ExecuteQueue(changeID, changeTime)
                insertQTE(execLog)
            }
        }

        return RESPONSE_OK
    }

    /**
     * TODO: HANDLE TRANSACTIONS FOR RETRY.
     * TODO: Do we still need to clean here??
     *
     * Function to handle a change (as a ChangeLog) from Client.
     * Inserts changes into actual tables (e.g., Instance, Contact, etc...) and adds change
     * to the ChangeLog and UploadQueue. We can put Transaction over changeFromClient() because
     * we know there won't be any instance delete changes from client.
     */
    @Transaction
    open suspend fun changeFromClient(changeLog: ChangeLog) {
        with(changeLog) {
            val cleanInstanceNumber = MiscHelpers.cleanNumber(instanceNumber)
            val cleanOldNumber = MiscHelpers.cleanNumber(oldNumber)
            val cleanNumber = MiscHelpers.cleanNumber(number)

            val cleanedChangeLog = this.copy(
                instanceNumber = cleanInstanceNumber,
                oldNumber = cleanOldNumber,
                number = cleanNumber
            )

            executeChange(changeLog)

            mutexLocks[MutexType.CHANGE]!!.withLock {
                insertChangeLog(cleanedChangeLog)
            }

            mutexLocks[MutexType.UPLOAD]!!.withLock {
                val upLog = UploadQueue(changeID, changeTime, getRowID(changeID))
                insertQTU(upLog)
            }

        }
    }
}