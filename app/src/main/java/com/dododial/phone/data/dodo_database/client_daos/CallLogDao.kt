package com.dododial.phone.data.dodo_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dododial.phone.data.dodo_database.entities.CallLog
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(vararg callLog: CallLog)

    @Query("SELECT * FROM call_log ORDER BY callEpochDate ASC")
    fun getFlowCallLogs(): Flow<List<CallLog>>

    @Query("SELECT * FROM call_log ORDER BY callEpochDate ASC")
    suspend fun getCallLogs(): List<CallLog>

    @Query("SELECT callEpochDate FROM call_log ORDER BY callEpochDate DESC LIMIT 1")
    suspend fun getMostRecentCallLogDate() : Long?

    @Query("DELETE FROM call_log")
    suspend fun deleteAllLog()
}