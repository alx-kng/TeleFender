package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.ChangeLog

@Dao
interface ChangeLogDao {

    /**
     * Inserts ChangeLog and returns inserted rowID.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChangeLog(changeLog: ChangeLog) : Long

    /**
     * To initialize repository in DialerActivity (MainActivity)
     */
    @Query("SELECT changeID FROM change_log LIMIT 0")
    suspend fun dummyQuery(): String?

    @Query("SELECT serverChangeID FROM change_log WHERE serverChangeID NOT null ORDER BY serverChangeID DESC LIMIT 1")
    suspend fun lastServerChangeID(): Int?

    @Query("SELECT changeTime FROM change_log ORDER BY changeTime DESC LIMIT 1")
    suspend fun getLatestChangeLogTime() : Int?

    @Query("SELECT * FROM change_log WHERE changeID = :changeID")
    suspend fun getChangeLog(changeID: String) : ChangeLog?

    @Query("SELECT * FROM change_log WHERE rowID = :rowID")
    suspend fun getChangeLog(rowID: Long) : ChangeLog?
    
    @Query("SELECT * FROM change_log ORDER BY changeTime ASC")
    suspend fun getAllChangeLogs() : List<ChangeLog>

    @Query("SELECT COUNT(changeID) FROM change_log")
    suspend fun getChangeLogSize() : Int?

    @Query("SELECT errorCounter FROM change_log WHERE changeID = :changeID ")
    suspend fun getChgLogErrorCounter(changeID: String) : Int?

    @Query("UPDATE change_log SET errorCounter = errorCounter + :counterDelta WHERE changeID = :changeID")
    suspend fun updateChgLogErrorCounterDelta(changeID: String, counterDelta: Int)

    @Query("UPDATE change_log SET errorCounter = :errorCounter WHERE changeID = :changeID")
    suspend fun updateChgLogErrorCounterAbsolute(changeID: String, errorCounter : Int)

    @Query("DELETE FROM change_log WHERE changeID = :changeID")
    suspend fun deleteChangeLogByID(changeID: String)

    @Query("DELETE FROM change_log WHERE instanceNumber = :instanceNumber")
    suspend fun deleteChangeLogByINS(instanceNumber: String)

    @Query("DELETE FROM change_log WHERE changeTime = :changeTime")
    suspend fun deleteChangeLogByTime(changeTime: String)

    @Query("DELETE FROM change_log")
    suspend fun deleteAllChangeLogs()
}