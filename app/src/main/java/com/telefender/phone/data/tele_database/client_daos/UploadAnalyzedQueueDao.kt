package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.UploadAnalyzedQueue


@Dao
interface UploadAnalyzedQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnalyzedQTU(vararg uploadQueue: UploadAnalyzedQueue)

    @Query("SELECT * FROM upload_analyzed_queue WHERE linkedRowID = :linkedRowID")
    suspend fun getAnalyzedQTU(linkedRowID: Int) : UploadAnalyzedQueue?

    @Query("SELECT * FROM upload_analyzed_queue WHERE linkedRowID = (SELECT min(linkedRowID) from upload_analyzed_queue) LIMIT 1")
    suspend fun getFirstAnalyzedQTU() : UploadAnalyzedQueue

    @Query("SELECT * FROM upload_analyzed_queue ORDER BY linkedRowID ASC LIMIT :amount")
    suspend fun getChunkAnalyzedQTU(amount: Int) : List<UploadAnalyzedQueue>

    @Query("SELECT * FROM upload_analyzed_queue")
    suspend fun getAllAnalyzedQTU() : List<UploadAnalyzedQueue>

    @Query("SELECT * FROM upload_analyzed_queue ORDER BY linkedRowID ASC")
    suspend fun getAllAnalyzedQTUOrdered() : List<UploadAnalyzedQueue>

    @Query("SELECT errorCounter FROM upload_analyzed_queue WHERE linkedRowID = :linkedRowID ")
    suspend fun getAnalyzedQTUErrorCounter(linkedRowID: Int) : Int

    @Query("SELECT linkedRowID FROM upload_analyzed_queue WHERE errorCounter > 0")
    suspend fun getAnalyzedQTUErrorLogs() : List<Int>

    @Query("SELECT EXISTS (SELECT * FROM upload_analyzed_queue LIMIT 1)")
    suspend fun hasAnalyzedQTU() : Boolean

    @Query("DELETE FROM upload_analyzed_queue WHERE linkedRowID = :linkedRowID")
    suspend fun deleteAnalyzedQTU(linkedRowID: Int)

    @Query("DELETE FROM upload_analyzed_queue")
    suspend fun deleteAllAnalyzedQTU()

    @Query("DELETE FROM upload_analyzed_queue WHERE linkedRowID <= :linkedRowID")
    suspend fun deleteAnalyzedQTUInclusive(linkedRowID: Int)

    @Query("DELETE FROM upload_analyzed_queue WHERE linkedRowID < :linkedRowID")
    suspend fun deleteAnalyzedQTUExclusive(linkedRowID: Int)
}