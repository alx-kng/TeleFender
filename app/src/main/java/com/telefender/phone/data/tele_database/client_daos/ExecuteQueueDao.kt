package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.ExecuteQueue

@Dao
interface ExecuteQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQTE(vararg qte: ExecuteQueue)

    @Query("UPDATE execute_queue SET errorCounter = errorCounter + :counterDelta WHERE rowID = :rowID")
    suspend fun incrementQTEErrors(rowID: Int, counterDelta: Int)

    @Query("SELECT EXISTS (SELECT * FROM execute_queue LIMIT 1)")
    suspend fun hasQTE() : Boolean

    @Query("SELECT COUNT(rowID) FROM execute_queue")
    suspend fun getNumQTE() : Int?

    @Query("SELECT * FROM execute_queue ORDER BY rowID ASC LIMIT 1")
    suspend fun getFirstQTE() : ExecuteQueue?

    @Query("SELECT * FROM execute_queue WHERE rowID = :rowID")
    suspend fun getQTE(rowID: Int) : ExecuteQueue
    
    @Query("SELECT * FROM execute_queue ORDER BY rowID ASC")
    suspend fun getAllQTE() : List<ExecuteQueue>

    @Query("SELECT errorCounter FROM execute_queue WHERE rowID = :rowID ")
    suspend fun getQTEErrorCounter(rowID: Int) : Int?

    @Query("SELECT rowID FROM execute_queue WHERE errorCounter > 0")
    suspend fun getQTEErrorLogs() : List<Int>

    @Query("DELETE FROM execute_queue WHERE rowID = :rowID")
    suspend fun deleteQTE(rowID: Int)

    @Query("DELETE FROM execute_queue")
    suspend fun deleteAllQTE()
}