package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.entities.CallDetail
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDetailDao: StoredMapDao {

    @Transaction
    suspend fun insertDetailSync(instanceNumber: String, callDetail: CallDetail) : Boolean {
        with(callDetail) {
            if (callDetailExists(callEpochDate)) {
                /*
                Makes sure call is not synced before setting values. Preserves memory lifetime
                by decreasing unnecessary writes.
                 */
                if (callSynced(callEpochDate) == null) {
                    syncDetail(
                        callEpochDate,
                        callType,
                        callLocation,
                        callDuration,
                        callDirection
                    )

                    updateStoredMap(
                        instanceNumber,
                        null,
                        null,
                        null,
                        callEpochDate
                    )

                    return true
                }
            } else {
                insertDetailIgnore(this)

                updateStoredMap(
                    instanceNumber,
                    null,
                    null,
                    null,
                    callEpochDate
                )

                return true
            }

            return false
        }
    }

    /**
     * Purposely don't set callType so that sync knows that this CallDetail is not synced
     * (only if inserted first <-> else statement).
     */
    @Transaction
    suspend fun insertDetailSkeleton(callDetail: CallDetail) {
        if (callDetailExists(callDetail.callEpochDate)) {
            with(callDetail) {
                updateUnallowed(callEpochDate, unallowed)
            }
        } else {
            val unSyncedCallDetail = with(callDetail) {
                CallDetail(
                    number,
                    null,
                    callEpochDate,
                    callDuration,
                    callLocation,
                    callDirection,
                    unallowed
                )
            }

            insertDetailIgnore(unSyncedCallDetail)
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
        callDirection: Int?,
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

    /**
     * Check synced by whether or not callType was set or not.
     */
    @Query("SELECT callType FROM call_detail WHERE callEpochDate = :callEpochDate")
    suspend fun callSynced(callEpochDate: Long) : String?

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