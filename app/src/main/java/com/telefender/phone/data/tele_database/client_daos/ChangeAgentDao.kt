package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Transaction
import com.telefender.phone.data.server_related.GenericData
import com.telefender.phone.data.server_related.GenericDataType
import com.telefender.phone.data.tele_database.MutexType
import com.telefender.phone.data.tele_database.TeleLocks.mutexLocks
import com.telefender.phone.data.tele_database.entities.*
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import timber.log.Timber


/**
 * Contains most high level functions related to changes to our Tele database. Note that if you
 * want to expose a function marked as a Transaction, you must indicate the function as "open"?
 */
@Dao
abstract class ChangeAgentDao: ExecuteAgentDao, ExecuteQueueDao, UploadChangeQueueDao,
    UploadAnalyzedQueueDao, ChangeLogDao, AnalyzedNumberDao, CallDetailDao {

    private val retryAmount = 5

    /**
     * Function to handle a data downloaded from server. Adds data to the ExecuteQueue. We don't
     * need mutexSync here because downloaded CallDetails / AnalyzedNumbers are separate from
     * user's CallDetails and AnalyzedNumbers and won't affect their sync.
     * Also, although we technically don't need to retry here (as the DownloadPostRequest keeps
     * retrying with the lastServerRowID), we still retry here to decrease load on server (by not
     * re-requesting unnecessarily).
     */
    suspend fun changeFromServer(genericData: GenericData) {
        for (i in 1..retryAmount) {
            try {
                changeFromServerHelper(genericData)
                break
            } catch (e: Exception) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: " +
                    "changeFromServer() RETRYING... ${e.message}")
                delay(2000)
            }
        }
    }

    @Transaction
    open suspend fun changeFromServerHelper(genericData: GenericData) {
        val dataType = genericData.getGenericDataType()
            ?: throw Exception("genericDataType = ${genericData.type} is invalid!")

        // rowID of data in respective table. Used in ExecuteQueue for referencing a row.
        val linkedRowID: Long

        when(dataType) {
            GenericDataType.CHANGE_DATA -> mutexLocks[MutexType.CHANGE]!!.withLock {
                with(genericData.changeLog!!) {
                    linkedRowID = insertChangeLog(this)
                }
            }
            GenericDataType.ANALYZED_DATA -> mutexLocks[MutexType.ANALYZED]!!.withLock {
                with(genericData.analyzedNumber!!) {
                    linkedRowID = insertAnalyzedNum(this)
                }
            }
            GenericDataType.LOG_DATA -> mutexLocks[MutexType.CALL_DETAIL]!!.withLock {
                with(genericData.callDetail!!) {
                    linkedRowID = insertCallDetailIgnore(this)
                }
            }
        }

        mutexLocks[MutexType.EXECUTE]!!.withLock {
            val execLog = ExecuteQueue.create(
                genericDataType = dataType,
                linkedRowID = linkedRowID
            )
            insertQTE(execLog)
        }
    }

    /**
     * TODO: Modify or create new UploadLog to upload CallDetails. If not, then Transaction may
     *  be unnecessary.
     *
     * Handles new calls from client. Should only be called for syncing call logs. Returns whether
     * or not the CallDetail was inserted or not (may not be inserted if already synced).
     *
     * NOTE: if you would like to retry the transaction, you must do so yourself in the caller
     * function.
     */
    @Transaction
    open suspend fun callFromClient(callDetail: CallDetail) : Boolean {
        val inserted: Boolean
        mutexLocks[MutexType.ANALYZED]!!.withLock {
            mutexLocks[MutexType.CALL_DETAIL]!!.withLock {
                inserted = localLogInsert(callDetail)
            }
        }

        return inserted
    }

    /**
     * TODO: Debating whether or not we should handle default database changes here to. But on
     *  second thought, since this a DAO, we shouldn't mix between our database and the default
     *  database. Perhaps we should make an enveloping function in the repository or something
     *  to call our changeFromClient() and handle the default database changes.
     *
     * Function to handle a change (as a ChangeLog) from Client.
     * Inserts changes into actual tables (e.g., Instance, Contact, etc...) and adds change
     * to the ChangeLog and UploadChangeQueue. We can put Transaction over changeFromClient() because
     * we know there won't be any instance delete changes from client. Moreover, we should pass
     * in instanceNumber no matter what, so that we know whether the change was associated with
     * users direct contacts or with tree contacts.
     *
     * NOTE: When a direct contact change occurs, the
     * change is locked with mutexSync to prevent any parallelism problems with the sync process.
     * Since tree contact changes don't affect sync, we have no need to wrap with mutexSync (which
     * also practically restricts to one client side change at a time), which also makes the rare
     * case of a tree instance delete not super blocking to the database.
     *
     * NOTE: If changeFromClient() fails [retryAmount], then it also throws an error if
     * [bubbleError] is true so that the enclosing function can do a larger retry (for the
     * larger process) if it wants to.
     */
    suspend fun changeFromClient(changeLog: ChangeLog, fromSync: Boolean, bubbleError: Boolean = false) {
        for (i in 1..retryAmount) {
            try {
                changeFromClientHelper(changeLog, fromSync)
                break
            } catch (e: Exception) {
                /*
                We need to throw an Exception here so that the enclosing syncContacts() can retry
                if the lower level changeFromClient() fails too many times.
                 */
                if (i == retryAmount && bubbleError) throw Exception("changeFromClient() FAILED")

                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: " +
                    "changeFromClient() RETRYING... ${e.message}")
                delay(2000)
            }
        }
    }

    @Transaction
    open suspend fun changeFromClientHelper(changeLog: ChangeLog, fromSync: Boolean) {
        with(changeLog) {
            /*
            fromSync indicates whether or not the change was initiated from a sync. As mentioned
            in TableSynchronizer, the final sync queries (e.g., inserting into our database if we
            don't have it but the default database does) need to be uninterrupted by other insert
            / delete contact number queries. We can fix this by using the mutexSync lock. However,
            since we will be directly using the mutexSync inside TableSynchronizer, we need to
            make sure the lock isn't called again when it reaches changeFromClient().
             */
            if (fromSync || instanceNumber != getUserNumber()) {
                executeChange(changeLog)
            } else {
                mutexLocks[MutexType.SYNC]!!.withLock {
                    executeChange(changeLog)
                }
            }

            // rowID of data in respective table. Used in upload queues for referencing a row.
            val linkedRowID: Long

            // Need to insert ChangeLog before inserting QTU so that rowID gets set.
            mutexLocks[MutexType.CHANGE]!!.withLock {
                linkedRowID = insertChangeLog(changeLog)
            }

            // No need to upload non-contact updates since they don't affect other users.
            if (this.getChangeType() != ChangeType.NON_CONTACT_UPDATE) {
                mutexLocks[MutexType.UPLOAD_CHANGE]!!.withLock {
                    val upLog = UploadChangeQueue(linkedRowID = linkedRowID)
                    insertChangeQTU(upLog)
                }
            }
        }
    }
}