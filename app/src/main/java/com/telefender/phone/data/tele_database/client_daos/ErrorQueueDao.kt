package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.ErrorQueue


@Dao
interface ErrorQueueDao {

    /**
     * Inserts ErrorQueue and returns inserted rowID. Probably returns -1 if insert failure.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertErrorLog(errorQueue: ErrorQueue) : Long

    @Query("SELECT * FROM error_queue WHERE rowID = :rowID")
    suspend fun getErrorLogByRID(rowID: Long) : ErrorQueue?

    @Query("SELECT * FROM error_queue WHERE serverRowID = :serverRowID")
    suspend fun getErrorLogBySRID(serverRowID: Long) : ErrorQueue?

    @Query("SELECT * FROM error_queue ORDER BY rowID ASC LIMIT 1")
    suspend fun getFirstErrorLog() : ErrorQueue?

    @Query("SELECT * FROM error_queue ORDER BY rowID ASC LIMIT :amount")
    suspend fun getChunkErrorLog(amount: Int) : List<ErrorQueue>

    @Query("SELECT * FROM error_queue")
    suspend fun getAllErrorLog() : List<ErrorQueue>

    @Query("SELECT * FROM error_queue ORDER BY rowID ASC")
    suspend fun getAllErrorLogOrdered() : List<ErrorQueue>

    @Query("SELECT errorCounter FROM error_queue WHERE rowID = :rowID ")
    suspend fun getErrorLogErrorCounter(rowID: Long) : Int

    @Query("SELECT rowID FROM error_queue WHERE errorCounter > 0")
    suspend fun getErrorLogWithError() : List<Int>

    @Query("SELECT EXISTS (SELECT * FROM error_queue LIMIT 1)")
    suspend fun hasErrorLog() : Boolean

    /**
     * Returns a nullable Int that indicates whether the delete was successful. If 1 is returned,
     * then the delete was successful, otherwise the delete failed.
     */
    @Query("DELETE FROM error_queue WHERE rowID = :rowID")
    suspend fun deleteErrorLog(rowID: Long) : Int?

    /**********************************************************************************************
     * Queries that can delete multiple rows.
     *
     * Returns a nullable Int that indicates whether the delete was successful (number of rows
     * delete). If a value >0 is returned, then the delete was at least partially successful,
     * otherwise the delete completely failed (if there were existing rows).
     **********************************************************************************************/

    @Query("DELETE FROM error_queue WHERE rowID <= :rowID")
    suspend fun deleteErrorLogInclusive(rowID: Long) : Int?

    @Query("DELETE FROM error_queue WHERE rowID < :rowID")
    suspend fun deleteErrorLogExclusive(rowID: Long) : Int?

    @Query("DELETE FROM error_queue")
    suspend fun deleteAllErrorLog() : Int?
}