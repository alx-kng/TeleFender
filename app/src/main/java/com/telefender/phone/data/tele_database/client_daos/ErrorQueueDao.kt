package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.ErrorQueue


@Dao
interface ErrorQueueDao {
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertErrorLog(errorQueue: ErrorQueue)

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

    @Query("DELETE FROM error_queue WHERE rowID = :rowID")
    suspend fun deleteErrorLog(rowID: Long)

    @Query("DELETE FROM error_queue")
    suspend fun deleteAllErrorLog()

    @Query("DELETE FROM error_queue WHERE rowID <= :rowID")
    suspend fun deleteErrorLogInclusive(rowID: Long)

    @Query("DELETE FROM error_queue WHERE rowID < :rowID")
    suspend fun deleteErrorLogExclusive(rowID: Long)
}