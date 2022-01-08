package com.dododial.phone.database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy
import com.dododial.phone.database.ChangeLog

@Dao
interface ChangeLogDao {

    @Query("SELECT changeID FROM change_log LIMIT 0")
    suspend fun dummyQuery(): String?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChangeLog(vararg changeLog: ChangeLog)
    
    @Query("DELETE FROM change_log WHERE changeID = :changeID")
    suspend fun deleteChangeLog_ChangeID(changeID: String)

    @Query("DELETE FROM change_log WHERE instanceNumber = :instanceNumber")
    suspend fun deleteChangeLog_Instance(instanceNumber: String)

    @Query("DELETE FROM change_log WHERE changeTime = :changeTime")
    suspend fun deleteChangeLog_Date(changeTime: String)
    
    @Query("SELECT * FROM change_log WHERE changeID = :changeID")
    suspend fun getChangeLogRow(changeID: String) : ChangeLog
    
    @Query("SELECT * FROM change_log")
    suspend fun getAllChangeLogs() : List<ChangeLog>

    @Query("SELECT errorCounter FROM change_log WHERE changeID = :changeID ")
    suspend fun getChgLogErrorCounter(changeID: String) : Int

    @Query("UPDATE change_log SET errorCounter = errorCounter + :counterDelta WHERE changeID = :changeID")
    suspend fun updateChgLogErrorCounter_Delta(changeID: String, counterDelta: Int)

    @Query("UPDATE change_log SET errorCounter = :errorCounter WHERE changeID = :changeID")
    suspend fun updateChgLogErrorCounter_Absolute(changeID: String, errorCounter : Int)

    @Query("SELECT changeID FROM change_log WHERE errorCounter > 0")
    suspend fun getChgLogErrorLogs() : List<String>

    @Query("DELETE FROM change_log")
    suspend fun deleteAllChangeLogs()
}