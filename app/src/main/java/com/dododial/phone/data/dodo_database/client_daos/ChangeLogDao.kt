package com.dododial.phone.data.dodo_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy
import com.dododial.phone.data.dodo_database.entities.ChangeLog

@Dao
interface ChangeLogDao {

    /**
     * To initialize repository in DialerActivity (MainActivity)
     */
    @Query("SELECT changeID FROM change_log LIMIT 0")
    suspend fun dummyQuery(): String?

    @Query("SELECT serverChangeID FROM change_log WHERE serverChangeID NOT null ORDER BY serverChangeID DESC LIMIT 1")
    suspend fun getLastChangeID(): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChangeLog(vararg changeLog: ChangeLog)
    
    @Query("DELETE FROM change_log WHERE changeID = :changeID")
    suspend fun deleteChangeLog_ChangeID(changeID: String)

    @Query("DELETE FROM change_log WHERE instanceNumber = :instanceNumber")
    suspend fun deleteChangeLog_Instance(instanceNumber: String)

    @Query("DELETE FROM change_log WHERE changeTime = :changeTime")
    suspend fun deleteChangeLog_Date(changeTime: String)

    @Query("SELECT changeTime FROM change_log ORDER BY changeTime DESC LIMIT 1")
    suspend fun getLatestChangeLogTime() : Int

    @Query("SELECT * FROM change_log WHERE changeID = :changeID")
    suspend fun getChangeLogRow(changeID: String) : ChangeLog
    
    @Query("SELECT * FROM change_log ORDER BY changeTime ASC")
    suspend fun getAllChangeLogs() : List<ChangeLog>

    @Query("SELECT COUNT(changeID) FROM change_log")
    suspend fun getChangeLogSize() : Int?

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
    
    @Query("SELECT rowID FROM change_log WHERE changeID = :changeID")
    suspend fun getRowID(changeID : String) : Int

}