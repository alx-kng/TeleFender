package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.entities.CallDetail
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDetailDao {

    @Transaction
    suspend fun insertDetailSync(callDetail: CallDetail) {
        if (callDetailExists(callDetail.callEpochDate)) {
            syncDetail(
                callDetail.callEpochDate,
                callDetail.callType,
                callDetail.callLocation,
                callDetail.callDuration,
                callDetail.callDirection
            )
        } else {
            insertDetailIgnore(callDetail)
        }
    }

    @Transaction
    suspend fun insertDetailClient(callDetail: CallDetail) {
        if (callDetailExists(callDetail.callEpochDate)) {
            updateUnallowed(callDetail.callEpochDate, callDetail.unallowed)
        } else {
            insertDetailIgnore(callDetail)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDetailIgnore(vararg callDetail: CallDetail)

    @Query("""
        UPDATE call_detail 
        SET callType = :callType,
            callDuration = :callDuration,
            callLocation = :callLocation,
            callDirection = :callDirection
        WHERE callEpochDate = :callEpochDate
        """)
    suspend fun syncDetail(
        callEpochDate: Long,
        callType: String?,
        callLocation: String?,
        callDuration: Long?,
        callDirection: String?
    )

    @Query("""
        UPDATE call_detail 
        SET unallowed = :unallowed
        WHERE callEpochDate = :callEpochDate
        """)
    suspend fun updateUnallowed(
        callEpochDate: Long,
        unallowed: Boolean
    )

    @Query("SELECT EXISTS (SELECT * FROM call_detail WHERE callEpochDate = :callEpochDate)")
    suspend fun callDetailExists(callEpochDate: Long) : Boolean

    @Query("SELECT * FROM call_detail ORDER BY callEpochDate DESC")
    fun getFlowCallDetails(): Flow<List<CallDetail>>

    @Query("SELECT * FROM call_detail ORDER BY callEpochDate DESC")
    suspend fun getCallDetails(): List<CallDetail>

    @Query("SELECT * FROM call_detail ORDER BY callEpochDate DESC LIMIT :amount")
    suspend fun getCallDetailsPartial(amount: Int): List<CallDetail>

    @Query("SELECT callEpochDate FROM call_detail ORDER BY callEpochDate DESC LIMIT 1")
    suspend fun getMostRecentCallDetailDate() : Long?

    @Query("DELETE FROM call_detail")
    suspend fun deleteAllCallDetails()
}