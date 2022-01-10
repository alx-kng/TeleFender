package com.dododial.phone.database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dododial.phone.database.QueueToUpload

@Dao
interface QueueToUploadDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQTU(vararg queueToUpload: QueueToUpload)

    @Query("UPDATE queue_to_upload SET errorCounter = errorCounter + :counterDelta WHERE changeID = :changeID")
    suspend fun updateQTUErrorCounter_Delta(changeID: String, counterDelta: Int)

    @Query("UPDATE queue_to_upload SET errorCounter = :errorCounter WHERE changeID = :changeID")
    suspend fun updateQTUErrorCounter_Absolute(changeID: String, errorCounter : Int)
    
    @Query("SELECT * FROM queue_to_upload WHERE createTime = (SELECT min(createTime) from queue_to_upload) LIMIT 1")
    suspend fun getFirstQTU() : QueueToUpload
    
    @Query("SELECT * FROM queue_to_upload WHERE changeID = :changeID")
    suspend fun getQTURow(changeID: String) : QueueToUpload

    @Query("SELECT * FROM queue_to_upload")
    suspend fun getAllQTU() : List<QueueToUpload>

    @Query("SELECT errorCounter FROM queue_to_upload WHERE changeID = :changeID ")
    suspend fun getQTUErrorCounter(changeID: String) : Int

    @Query("SELECT changeID FROM queue_to_upload WHERE errorCounter > 0")
    suspend fun getQTUErrorLogs() : List<String>

    @Query("DELETE FROM queue_to_upload WHERE changeID = :changeID")
    suspend fun deleteQTU_ChangeID(changeID: String)

    @Query("DELETE FROM queue_to_upload WHERE createTime = :createTime")
    suspend fun deleteQTU_Date(createTime: String)

    @Query("DELETE FROM queue_to_upload")
    suspend fun deleteAllQTUs()
}