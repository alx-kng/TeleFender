package com.dododial.phone.database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dododial.phone.database.QueueToExecute

@Dao
interface QueueToExecuteDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQTE(vararg queuetoexecute: QueueToExecute)

    @Query("UPDATE queue_to_execute SET errorCounter = errorCounter + :counterDelta WHERE changeID = :changeID")
    suspend fun updateQTEErrorCounter_Delta(changeID: String, counterDelta: Int)

    @Query("UPDATE queue_to_execute SET errorCounter = :errorCounter WHERE changeID = :changeID")
    suspend fun updateQTEErrorCounter_Absolute(changeID: String, errorCounter : Int)

    @Query("SELECT * FROM queue_to_execute WHERE createTime = (SELECT min(createTime) from queue_to_execute) LIMIT 1")
    suspend fun getFirstQTE() : QueueToExecute

    @Query("SELECT * FROM queue_to_execute WHERE changeID = :changeID")
    suspend fun getQTERow(changeID: String) : QueueToExecute
    
    @Query("SELECT * FROM queue_to_execute")
    suspend fun getAllQTEs() : List<QueueToExecute>

    @Query("SELECT errorCounter FROM queue_to_execute WHERE changeID = :changeID ")
    suspend fun getQTEErrorCounter(changeID: String) : Int

    @Query("SELECT changeID FROM queue_to_execute WHERE errorCounter > 0")
    suspend fun getQTEErrorLogs() : List<String>

    @Query("DELETE FROM queue_to_execute WHERE changeID = :changeID")
    suspend fun deleteQTE_ChangeID(changeID: String)

    @Query("DELETE FROM queue_to_execute WHERE createTime = :createTime")
    suspend fun deleteQTE_Date(createTime: String)

    @Query("DELETE FROM queue_to_execute")
    suspend fun deleteAllQTEs()
}