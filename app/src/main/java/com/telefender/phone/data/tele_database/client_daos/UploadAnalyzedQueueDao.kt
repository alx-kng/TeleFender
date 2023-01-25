package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.entities.UploadAnalyzedQueue


@Dao
interface UploadAnalyzedQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnalyzedQTU(vararg uploadQueue: UploadAnalyzedQueue)

    @Query("SELECT * FROM upload_analyzed_queue WHERE linkedRowID = :linkedRowID")
    suspend fun getAnalyzedQTU(linkedRowID: Long) : UploadAnalyzedQueue?

    @Query("SELECT * FROM upload_analyzed_queue ORDER BY linkedRowID ASC LIMIT 1")
    suspend fun getFirstAnalyzedQTU() : UploadAnalyzedQueue?

    // Transaction to prevent data corruption on large returns.
    @Transaction
    @Query("SELECT * FROM upload_analyzed_queue ORDER BY linkedRowID ASC LIMIT :amount")
    suspend fun getChunkAnalyzedQTU(amount: Int) : List<UploadAnalyzedQueue>

    // Transaction to prevent data corruption on large returns.
    @Transaction
    @Query("SELECT * FROM upload_analyzed_queue")
    suspend fun getAllAnalyzedQTU() : List<UploadAnalyzedQueue>

    // Transaction to prevent data corruption on large returns.
    @Transaction
    @Query("SELECT * FROM upload_analyzed_queue ORDER BY linkedRowID ASC")
    suspend fun getAllAnalyzedQTUOrdered() : List<UploadAnalyzedQueue>

    // Transaction to prevent data corruption on large returns.
    @Transaction
    @Query("SELECT errorCounter FROM upload_analyzed_queue WHERE linkedRowID = :linkedRowID ")
    suspend fun getAnalyzedQTUErrorCounter(linkedRowID: Long) : Int

    // Transaction to prevent data corruption on large returns.
    @Transaction
    @Query("SELECT linkedRowID FROM upload_analyzed_queue WHERE errorCounter > 0")
    suspend fun getAnalyzedQTUErrorLogs() : List<Int>

    @Query("SELECT EXISTS (SELECT * FROM upload_analyzed_queue LIMIT 1)")
    suspend fun hasAnalyzedQTU() : Boolean

    /**
     * Returns a nullable Int that indicates whether the delete was successful. If 1 is returned,
     * then the delete was successful, otherwise the delete failed.
     */
    @Query("DELETE FROM upload_analyzed_queue WHERE linkedRowID = :linkedRowID")
    suspend fun deleteAnalyzedQTU(linkedRowID: Long) : Int?

    /**********************************************************************************************
     * Queries that can delete multiple rows.
     *
     * Returns a nullable Int that indicates whether the delete was successful (number of rows
     * delete). If a value >0 is returned, then the delete was at least partially successful,
     * otherwise the delete completely failed (if there were existing rows).
     **********************************************************************************************/

    @Query("DELETE FROM upload_analyzed_queue WHERE linkedRowID <= :linkedRowID")
    suspend fun deleteAnalyzedQTUInclusive(linkedRowID: Long) : Int?

    @Query("DELETE FROM upload_analyzed_queue WHERE linkedRowID < :linkedRowID")
    suspend fun deleteAnalyzedQTUExclusive(linkedRowID: Long) : Int?

    @Query("DELETE FROM upload_analyzed_queue")
    suspend fun deleteAllAnalyzedQTU() : Int?
}