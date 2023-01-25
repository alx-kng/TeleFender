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

    /**
     * Returns a nullable Int that indicates whether the update was successful. If 1 is returned,
     * then the update was successful, otherwise the update failed.
     */
    @Query("UPDATE execute_queue SET errorCounter = errorCounter + :counterDelta WHERE rowID = :rowID")
    suspend fun incrementQTEErrors(rowID: Long, counterDelta: Int) : Int?

    @Query("SELECT EXISTS (SELECT * FROM execute_queue LIMIT 1)")
    suspend fun hasQTE() : Boolean

    @Query("SELECT COUNT(rowID) FROM execute_queue")
    suspend fun getNumQTE() : Int?

    @Query("SELECT * FROM execute_queue ORDER BY rowID ASC LIMIT 1")
    suspend fun getFirstQTE() : ExecuteQueue?

    @Query("SELECT * FROM execute_queue WHERE rowID = :rowID")
    suspend fun getQTE(rowID: Long) : ExecuteQueue?
    
    @Query("SELECT * FROM execute_queue ORDER BY rowID ASC")
    suspend fun getAllQTE() : List<ExecuteQueue>

    @Query("SELECT errorCounter FROM execute_queue WHERE rowID = :rowID ")
    suspend fun getQTEErrorCounter(rowID: Long) : Int?

    @Query("SELECT rowID FROM execute_queue WHERE errorCounter > 0")
    suspend fun getQTEErrorLogs() : List<Int>

    /**
     * Returns a nullable Int that indicates whether the delete was successful. If 1 is returned,
     * then the delete was successful, otherwise the delete failed.
     */
    @Query("DELETE FROM execute_queue WHERE rowID = :rowID")
    suspend fun deleteQTE(rowID: Long) : Int?

    /**
     * Returns a nullable Int that indicates whether the delete was successful (number of rows
     * delete). If a value >0 is returned, then the delete was at least partially successful,
     * otherwise the delete completely failed (if there were existing rows).
     */
    @Query("DELETE FROM execute_queue")
    suspend fun deleteAllQTE() : Int?
}