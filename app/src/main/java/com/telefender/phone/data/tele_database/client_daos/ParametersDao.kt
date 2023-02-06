package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.Parameters
import com.telefender.phone.data.tele_database.entities.StoredMap
import com.telefender.phone.helpers.TeleHelpers


@Dao
interface ParametersDao : StoredMapDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParametersQuery(vararg parameters: Parameters)

    /**
     * Initializes the only Parameters row with user's number. After first initialization,
     * this function is basically locked to make sure there is ONLY ONE Parameters row.
     * Make sure that you are passing in the right number!!!
     *
     * NOTE: Currently no need to return whether the Parameters insert was successful or not, as we
     * check for initialization using a separate SELECT query in ClientDatabase / TeleHelpers.
     */
    suspend fun initParameters(userNumber: String) {
        if (getParameters() == null && userNumber != TeleHelpers.UNKNOWN_NUMBER) {
            // Initial parameter values.
            insertParametersQuery(
                Parameters(
                    userNumber = userNumber,
                    shouldUploadAnalyzed = true,
                    shouldUploadLogs = false,
                    initialNotifyGate = 2,
                    verifiedSpamNotifyGate = 7,
                    superSpamNotifyGate = 14,
                    incomingGate = 40,
                    outgoingGate = 12,
                    smsImmediateWaitTime = 2000,
                    smsDeferredWaitTime = 60
                )
            )
        }
    }

    /**
     * Retrieves parameters if it exists. Instead of using userNumber to query, we can just
     * select one row from the Parameters table since there can only be 1 or 0 rows.
     */
    @Query("SELECT * FROM parameters LIMIT 1")
    suspend fun getParameters() : Parameters?

    /**
     * Returns whether or not the update was successful.
     */
    suspend fun updateParameters(
        shouldUploadAnalyzed: Boolean? = null,
        shouldUploadLogs: Boolean? = null,
        initialNotifyGate: Int? = null,
        verifiedSpamNotifyGate: Int? = null,
        superSpamNotifyGate: Int? = null,
        incomingGate: Int? = null,
        outgoingGate: Int? = null,
        smsImmediateWaitTime: Long? = null,
        smsDeferredWaitTime: Int? = null
    ) : Boolean {
        // Retrieves user number if possible and returns false if not.
        val userNumber = getUserNumber() ?: return false

        // Can only update if the row already exists.
        if (getParameters() == null) return false

        val result = updateParametersQuery(
            userNumber = userNumber,
            shouldUploadAnalyzed = shouldUploadAnalyzed,
            shouldUploadLogs = shouldUploadLogs,
            initialNotifyGate = initialNotifyGate,
            verifiedSpamNotifyGate = verifiedSpamNotifyGate,
            superSpamNotifyGate = superSpamNotifyGate,
            incomingGate = incomingGate,
            outgoingGate = outgoingGate,
            smsImmediateWaitTime = smsImmediateWaitTime,
            smsDeferredWaitTime = smsDeferredWaitTime
        )

        return result == 1
    }

    /**
     * Returns a nullable Int that indicates whether the update was successful. If 1 is returned,
     * then the update was successful, otherwise the update failed.
     */
    @Query(
        """UPDATE parameters SET
        shouldUploadAnalyzed =
            CASE
                WHEN :shouldUploadAnalyzed IS NOT NULL
                    THEN :shouldUploadAnalyzed
                ELSE shouldUploadAnalyzed
            END,
        shouldUploadLogs =
            CASE
                WHEN :shouldUploadLogs IS NOT NULL
                    THEN :shouldUploadLogs
                ELSE shouldUploadLogs
            END,
        initialNotifyGate =
        CASE
            WHEN :initialNotifyGate IS NOT NULL
                THEN :initialNotifyGate
            ELSE initialNotifyGate
        END,
        verifiedSpamNotifyGate =
            CASE
                WHEN :verifiedSpamNotifyGate IS NOT NULL
                    THEN :verifiedSpamNotifyGate
                ELSE verifiedSpamNotifyGate
            END,
        superSpamNotifyGate =
            CASE
                WHEN :superSpamNotifyGate IS NOT NULL
                    THEN :superSpamNotifyGate
                ELSE superSpamNotifyGate
            END,
        incomingGate =
            CASE
                WHEN :incomingGate IS NOT NULL
                    THEN :incomingGate
                ELSE incomingGate
            END,
        outgoingGate =
            CASE
                WHEN :outgoingGate IS NOT NULL
                    THEN :outgoingGate
                ELSE outgoingGate
            END,
        smsImmediateWaitTime =
            CASE
                WHEN :smsImmediateWaitTime IS NOT NULL
                    THEN :smsImmediateWaitTime
                ELSE smsImmediateWaitTime
            END,
        smsDeferredWaitTime =
            CASE
                WHEN :smsDeferredWaitTime IS NOT NULL
                    THEN :smsDeferredWaitTime
                ELSE smsDeferredWaitTime
            END
        WHERE userNumber = :userNumber"""
    )
    suspend fun updateParametersQuery(
        userNumber: String,
        shouldUploadAnalyzed: Boolean?,
        shouldUploadLogs: Boolean?,
        initialNotifyGate: Int?,
        verifiedSpamNotifyGate: Int?,
        superSpamNotifyGate: Int?,
        incomingGate: Int?,
        outgoingGate: Int?,
        smsImmediateWaitTime: Long?,
        smsDeferredWaitTime: Int?
    ) : Int?

    /**
     * Returns a nullable Int that indicates whether the delete was successful. If 1 is returned,
     * then the delete was successful, otherwise the delete failed.
     *
     * NOTE: We assume there is only one Parameters row, so the query should return 1 if successful.
     */
    @Query("DELETE FROM parameters WHERE userNumber = :userNumber")
    suspend fun deleteParameters(userNumber: String) : Int?
}