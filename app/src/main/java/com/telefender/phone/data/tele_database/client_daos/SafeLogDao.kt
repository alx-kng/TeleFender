package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.entities.SafeLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SafeLogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSafeLog(vararg safeLog: SafeLog)

    @Transaction
    suspend fun insertSafeNumber(number: String, callEpochDate: Long) {
        insertSafeLog(SafeLog(number, callEpochDate))
    }

    @Query("SELECT * FROM safe_log ORDER BY callEpochDate ASC")
    fun getFlowSafeLogs(): Flow<List<SafeLog>>

    @Query("SELECT * FROM safe_log ORDER BY callEpochDate ASC")
    suspend fun getSafeLogs(): List<SafeLog>

    @Query("SELECT callEpochDate FROM safe_log ORDER BY callEpochDate DESC LIMIT 1")
    suspend fun getMostRecentSafeLogDate() : Long?

    @Query("DELETE FROM safe_log WHERE number = :number")
    suspend fun deleteSafeLog(number: String)

    @Query("DELETE FROM safe_log")
    suspend fun deleteAllSafeLogs()
}