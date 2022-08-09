package com.dododial.phone.data.dodo_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dododial.phone.data.dodo_database.entities.QueueToExecute

@Dao
interface QueueToExecuteDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQTE(vararg queuetoexecute: QueueToExecute)

    @Query("UPDATE queue_to_execute SET errorCounter = errorCounter + :counterDelta WHERE changeID = :changeID")
    suspend fun updateQTEErrorCounter_Delta(changeID: String, counterDelta: Int)

    @Query("UPDATE queue_to_execute SET errorCounter = :errorCounter WHERE changeID = :changeID")
    suspend fun updateQTEErrorCounter_Absolute(changeID: String, errorCounter : Int)

    @Query("SELECT * FROM queue_to_execute ORDER BY createTime ASC LIMIT 1")
    suspend fun getFirstQTE() : QueueToExecute

    @Query("SELECT * FROM queue_to_execute WHERE changeID = :changeID")
    suspend fun getQTERow(changeID: String) : QueueToExecute
    
    @Query("SELECT * FROM queue_to_execute ORDER BY createTime ASC")
    suspend fun getAllQTEs() : List<QueueToExecute>

    @Query("SELECT errorCounter FROM queue_to_execute WHERE changeID = :changeID ")
    suspend fun getQTEErrorCounter(changeID: String) : Int

    @Query("SELECT changeID FROM queue_to_execute WHERE errorCounter > 0")
    suspend fun getQTEErrorLogs() : List<String>

    @Query("SELECT EXISTS (SELECT * FROM queue_to_execute LIMIT 1)")
    suspend fun hasQTEs() : Boolean
    
    @Query("SELECT COUNT(changeID) FROM queue_to_execute")
    suspend fun getQueueToExecuteSize() : Int?

    @Query("DELETE FROM queue_to_execute WHERE changeID = :changeID")
    suspend fun deleteQTE_ChangeID(changeID: String)

    @Query("DELETE FROM queue_to_execute WHERE createTime = :createTime")
    suspend fun deleteQTE_Date(createTime: Long)

    @Query("DELETE FROM queue_to_execute")
    suspend fun deleteAllQTEs()
}