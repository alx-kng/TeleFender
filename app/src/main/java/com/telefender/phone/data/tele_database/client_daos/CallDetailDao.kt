package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.entities.CallDetail
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDetailDao: StoredMapDao {

    /**
     * Inserts CallDetail and returns inserted rowID.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCallDetailIgnore(callDetail: CallDetail) : Long

    /**
     * TODO: Double check that Transaction is unnecessary here since insertDetailSync() is
     *  already wrapped in a Transaction inside ExecuteAgent's logInsert().
     * 
     * Syncs CallDetail from default database with our CallDetails and returns inserted rowID.
     * 
     * NOTE: MUST ONLY BE USED FOR SYNCING CallDetails, since it also updates the lastLogSyncTime
     * in StoredMap.
     */
    suspend fun insertCallDetailSync(callDetail: CallDetail) : Boolean {
        with(callDetail) {
            if (callDetailExists(callEpochDate)) {
                /*
                Makes sure call is not synced before setting values. Preserves memory lifetime
                by decreasing unnecessary writes.
                 */
                if (!callSynced(callEpochDate)) {
                    syncDetail(
                        rawNumber = rawNumber,
                        normalizedNumber = normalizedNumber,
                        callType = callType,
                        callEpochDate = callEpochDate,
                        callLocation = callLocation,
                        callDuration = callDuration,
                        callDirection = callDirection,
                        instanceNumber = instanceNumber
                    )

                    updateStoredMap(lastLogSyncTime = callEpochDate)

                    return true
                }
            } else {
                insertCallDetailIgnore(this)

                updateStoredMap(lastLogSyncTime = callEpochDate)

                return true
            }

            return false
        }
    }

    /**
     * For inserting a CallDetail skeleton that contains info on the unallowed status of the call.
     * Purposely don't set callType so that sync knows that this CallDetail is not synced (only if
     * inserted first <-> else statement).
     *
     * NOTE: Should be wrapped in try-catch, as the underlying mechanism can throw exceptions.
     */
    @Transaction
    suspend fun insertCallDetailSkeleton(callDetail: CallDetail) {
        if (callDetailExists(callDetail.callEpochDate)) {
            with(callDetail) {
                updateUnallowed(callEpochDate, instanceNumber, unallowed)
            }
        } else {
            val unSyncedCallDetail = with(callDetail) {
                CallDetail(
                    rawNumber = rawNumber,
                    normalizedNumber = normalizedNumber,
                    callType = null,
                    callEpochDate = callEpochDate,
                    callDuration = callDuration,
                    callLocation = callLocation,
                    callDirection = callDirection,
                    instanceNumber = instanceNumber,
                    unallowed = unallowed
                )
            }

            insertCallDetailIgnore(unSyncedCallDetail)
        }
    }

    /**
     * TODO: Maybe check for null values to make sure that null values don't override non-null
     *  columns. Also, maybe check that the values are different before updating column.
     */
    @Query("""
        UPDATE call_detail 
        SET rawNumber = :rawNumber,
            normalizedNumber = :normalizedNumber,
            callType = :callType,
            callDuration = :callDuration,
            callLocation = :callLocation,
            callDirection = :callDirection
        WHERE callEpochDate = :callEpochDate AND instanceNumber = :instanceNumber
        """)
    suspend fun syncDetail(
        rawNumber: String,
        normalizedNumber: String,
        callType: String?,
        callEpochDate: Long,
        callLocation: String?,
        callDuration: Long,
        callDirection: Int,
        instanceNumber: String,
    )

    @Query("""
        UPDATE call_detail 
        SET unallowed = :unallowed
        WHERE callEpochDate = :callEpochDate AND instanceNumber = :instanceNumber
        """)
    suspend fun updateUnallowed(
        callEpochDate: Long,
        instanceNumber: String,
        unallowed: Boolean
    )

    @Query("SELECT * FROM call_detail ORDER BY callEpochDate DESC")
    fun getFlowCallDetails(): Flow<List<CallDetail>>

    @Query("SELECT * FROM call_detail WHERE rowID = :rowID")
    suspend fun getCallDetail(rowID: Long) : CallDetail?

    /**********************************************************************************************
     * Check if CallDetail under given instanceNumber exists. If no instanceNumber is passed in,
     * then we assume we are querying from the user's own CallDetails. For now these are private
     * because they have the possibility of throwing Exception when not used in the right place.
     *********************************************************************************************/

    private suspend fun callDetailExists(callEpochDate: Long, instanceParam: String? = null) : Boolean {
        val instanceNumber = instanceParam ?: getUserNumber()!!
        return callDetailExistsQuery(callEpochDate, instanceNumber)
    }

    @Query("SELECT EXISTS (SELECT * FROM call_detail WHERE callEpochDate = :callEpochDate AND instanceNumber = :instanceNumber)")
    suspend fun callDetailExistsQuery(callEpochDate: Long, instanceNumber: String) : Boolean

    /**********************************************************************************************
     * Check synced by whether or not callType was set or not. Only applicable to user's own
     * CallDetails. For now these are private because they have the possibility of throwing
     * Exception when not used in the right place.
     *********************************************************************************************/

    private suspend fun callSynced(callEpochDate: Long) : Boolean {
        val instanceNumber = getUserNumber()!!
        return callSyncedQuery(callEpochDate, instanceNumber)
    }

    @Query("SELECT EXISTS (SELECT callType FROM call_detail WHERE callEpochDate = :callEpochDate AND instanceNumber = :instanceNumber)")
    suspend fun callSyncedQuery(callEpochDate: Long, instanceNumber: String) : Boolean

    /**********************************************************************************************
     * Retrieves all CallDetails associated with the given instanceNumber. If nothing is passed in,
     * then we assume we are retrieving the user's own CallDetails. Returns null if can't retrieve
     * user's number.
     *********************************************************************************************/

    suspend fun getCallDetails(instanceParam: String? = null) : List<CallDetail>? {
        val instanceNumber = instanceParam ?: getUserNumber() ?: return null
        return getCallDetailsQuery(instanceNumber)
    }

    @Query("SELECT * FROM call_detail WHERE instanceNumber = :instanceNumber ORDER BY callEpochDate DESC")
    suspend fun getCallDetailsQuery(instanceNumber: String) : List<CallDetail>


    /**********************************************************************************************
     * Retrieves partial CallDetails associated with the given instanceNumber. If nothing is passed
     * in, then we assume we are retrieving the user's own CallDetails. Returns null if can't retrieve
     * user's number.
     *********************************************************************************************/

    suspend fun getCallDetailsPartial(instanceParam: String? = null, amount: Int) : List<CallDetail>? {
        val instanceNumber = instanceParam ?: getUserNumber() ?: return null
        return getCallDetailsPartialQuery(instanceNumber, amount)
    }

    @Query("SELECT * FROM call_detail WHERE instanceNumber = :instanceNumber ORDER BY callEpochDate DESC LIMIT :amount")
    suspend fun getCallDetailsPartialQuery(instanceNumber: String, amount: Int) : List<CallDetail>

    /**********************************************************************************************
     * Retrieves most recent CallDetail date associated with the given instanceNumber. If nothing
     * is passed in, then we assume we are querying from the user's own CallDetails.
     *********************************************************************************************/

    suspend fun getNewestCallDate(instanceParam: String? = null) : Long? {
        val instanceNumber = instanceParam ?: getUserNumber()!!
        return getNewestCallDateQuery(instanceNumber)
    }

    @Query("SELECT callEpochDate FROM call_detail WHERE instanceNumber = :instanceNumber ORDER BY callEpochDate DESC LIMIT 1")
    suspend fun getNewestCallDateQuery(instanceNumber: String) : Long?

    /**********************************************************************************************
     * Deletion functions
     *********************************************************************************************/

    @Query("DELETE FROM call_detail WHERE rowID = :rowID")
    suspend fun deleteCallDetail(rowID: Long)

    @Query("DELETE FROM call_detail")
    suspend fun deleteAllCallDetails()
}