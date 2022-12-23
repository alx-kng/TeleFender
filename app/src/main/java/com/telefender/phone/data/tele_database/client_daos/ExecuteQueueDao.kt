package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.ExecuteQueue

@Dao
interface ExecuteQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQTE(vararg queuetoexecute: ExecuteQueue)

    @Query("UPDATE execute_queue SET errorCounter = errorCounter + :counterDelta WHERE changeID = :changeID")
    suspend fun updateQTEErrorCounter_Delta(changeID: String, counterDelta: Int)

    @Query("UPDATE execute_queue SET errorCounter = :errorCounter WHERE changeID = :changeID")
    suspend fun updateQTEErrorCounter_Absolute(changeID: String, errorCounter : Int)

    @Query("SELECT * FROM execute_queue ORDER BY createTime ASC LIMIT 1")
    suspend fun getFirstQTE() : ExecuteQueue

    @Query("SELECT * FROM execute_queue WHERE changeID = :changeID")
    suspend fun getQTERow(changeID: String) : ExecuteQueue
    
    @Query("SELECT * FROM execute_queue ORDER BY createTime ASC")
    suspend fun getAllQTEs() : List<ExecuteQueue>

    @Query("SELECT errorCounter FROM execute_queue WHERE changeID = :changeID ")
    suspend fun getQTEErrorCounter(changeID: String) : Int

    @Query("SELECT changeID FROM execute_queue WHERE errorCounter > 0")
    suspend fun getQTEErrorLogs() : List<String>

    @Query("SELECT EXISTS (SELECT * FROM execute_queue LIMIT 1)")
    suspend fun hasQTEs() : Boolean
    
    @Query("SELECT COUNT(changeID) FROM execute_queue")
    suspend fun getQueueToExecuteSize() : Int?

    @Query("DELETE FROM execute_queue WHERE changeID = :changeID")
    suspend fun deleteQTE_ChangeID(changeID: String)

    @Query("DELETE FROM execute_queue WHERE createTime = :createTime")
    suspend fun deleteQTE_Date(createTime: Long)

    @Query("DELETE FROM execute_queue")
    suspend fun deleteAllQTEs()
}