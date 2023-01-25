package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.UploadChangeQueue

@Dao
interface UploadChangeQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChangeQTU(vararg uploadQueue: UploadChangeQueue)
    
    @Query("SELECT * FROM upload_change_queue WHERE linkedRowID = :linkedRowID")
    suspend fun getChangeQTU(linkedRowID: Long) : UploadChangeQueue?

    @Query("SELECT * FROM upload_change_queue ORDER BY linkedRowID ASC LIMIT 1")
    suspend fun getFirstChangeQTU() : UploadChangeQueue?

    @Query("SELECT * FROM upload_change_queue ORDER BY linkedRowID ASC LIMIT :amount")
    suspend fun getChunkChangeQTU(amount: Int) : List<UploadChangeQueue>

    @Query("SELECT * FROM upload_change_queue")
    suspend fun getAllChangeQTU() : List<UploadChangeQueue>

    @Query("SELECT * FROM upload_change_queue ORDER BY linkedRowID ASC")
    suspend fun getAllChangeQTUOrdered() : List<UploadChangeQueue>

    @Query("SELECT errorCounter FROM upload_change_queue WHERE linkedRowID = :linkedRowID ")
    suspend fun getChangeQTUErrorCounter(linkedRowID: Long) : Int

    @Query("SELECT linkedRowID FROM upload_change_queue WHERE errorCounter > 0")
    suspend fun getChangeQTUErrorLogs() : List<Int>

    @Query("SELECT EXISTS (SELECT * FROM upload_change_queue LIMIT 1)")
    suspend fun hasChangeQTU() : Boolean

    /**
     * Returns a nullable Int that indicates whether the delete was successful. If 1 is returned,
     * then the delete was successful, otherwise the delete failed.
     */
    @Query("DELETE FROM upload_change_queue WHERE linkedRowID = :linkedRowID")
    suspend fun deleteChangeQTU(linkedRowID: Long) : Int?

    /**********************************************************************************************
     * Queries that can delete multiple rows.
     *
     * Returns a nullable Int that indicates whether the delete was successful (number of rows
     * delete). If a value >0 is returned, then the delete was at least partially successful,
     * otherwise the delete completely failed (if there were existing rows).
     **********************************************************************************************/
    
    @Query("DELETE FROM upload_change_queue WHERE linkedRowID <= :linkedRowID")
    suspend fun deleteChangeQTUInclusive(linkedRowID: Long) : Int?

    @Query("DELETE FROM upload_change_queue WHERE linkedRowID < :linkedRowID")
    suspend fun deleteChangeQTUExclusive(linkedRowID: Long) : Int?

    @Query("DELETE FROM upload_change_queue")
    suspend fun deleteAllChangeQTU() : Int?
}