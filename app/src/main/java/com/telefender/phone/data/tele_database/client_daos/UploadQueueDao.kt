package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.UploadQueue

@Dao
interface UploadQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQTU(vararg uploadQueue: UploadQueue)

    @Query("UPDATE upload_queue SET errorCounter = errorCounter + :counterDelta WHERE changeID = :changeID")
    suspend fun updateQTUErrorCounter_Delta(changeID: String, counterDelta: Int)

    @Query("UPDATE upload_queue SET errorCounter = :errorCounter WHERE changeID = :changeID")
    suspend fun updateQTUErrorCounter_Absolute(changeID: String, errorCounter : Int)
    
    @Query("SELECT * FROM upload_queue WHERE createTime = (SELECT min(createTime) from upload_queue) LIMIT 1")
    suspend fun getFirstQTU() : UploadQueue
    
    @Query("SELECT * FROM upload_queue WHERE changeID = :changeID")
    suspend fun getQTURow(changeID: String) : UploadQueue?

    @Query("SELECT * FROM upload_queue")
    suspend fun getAllQTU() : List<UploadQueue>

    @Query("SELECT * FROM upload_queue ORDER BY rowID ASC LIMIT 200")
    suspend fun getChunkQTU_rowID() : List<UploadQueue>

    @Query("SELECT * FROM upload_queue ORDER BY rowID ASC")
    suspend fun getAllQTU_rowID() : List<UploadQueue>

    @Query("SELECT errorCounter FROM upload_queue WHERE changeID = :changeID ")
    suspend fun getQTUErrorCounter(changeID: String) : Int

    @Query("SELECT changeID FROM upload_queue WHERE errorCounter > 0")
    suspend fun getQTUErrorLogs() : List<String>

    @Query("SELECT EXISTS (SELECT * FROM upload_queue LIMIT 1)")
    suspend fun hasQTUs() : Boolean

    @Query("DELETE FROM upload_queue WHERE changeID = :changeID")
    suspend fun deleteQTU_ChangeID(changeID: String)

    @Query("DELETE FROM upload_queue WHERE createTime = :createTime")
    suspend fun deleteQTU_Date(createTime: Long)

    @Query("DELETE FROM upload_queue")
    suspend fun deleteAllQTUs()
    
    @Query("DELETE FROM upload_queue WHERE rowID <= :rowID")
    suspend fun deleteUploadInclusive(rowID : Int)

    @Query("DELETE FROM upload_queue WHERE rowID < :rowID")
    suspend fun deleteUploadExclusive(rowID : Int)
}