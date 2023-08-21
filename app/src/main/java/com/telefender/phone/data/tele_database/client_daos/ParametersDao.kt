package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.Parameters
import com.telefender.phone.data.tele_database.entities.ParametersWrapper
import com.telefender.phone.misc_helpers.TeleHelpers


@Dao
interface ParametersDao : StoredMapDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParametersWrapper(vararg parameters: ParametersWrapper)

    /**
     * Initializes the only ParametersWrapper row with user's number. After first initialization,
     * this function is basically locked to make sure there is ONLY ONE ParametersWrapper row.
     * Make sure that you are passing in the right number!!!
     *
     * NOTE: Currently no need to return whether the ParametersWrapper insert was successful or not,
     * as we check for initialization using a separate SELECT query in ClientDatabase / TeleHelpers.
     */
    suspend fun initParameters(userNumber: String) {
        if (getParametersWrapper() == null && userNumber != TeleHelpers.UNKNOWN_NUMBER) {

            // Initial parameter values.
            val initialParameters =
                Parameters(
                    shouldUploadAnalyzed = true,
                    shouldUploadLogs = false,

                    initialNotifyGate = 2,
                    verifiedSpamNotifyGate = 7,
                    superSpamNotifyGate = 14,
                    seenGateIncrease = 1,

                    notifyWindowSize = 7,
                    initialLastCallDropWindow = 15,
                    seenWindowDecrease = 5,
                    qualifiedDropWindow = 7,
                    seenDropWindow = 7,

                    incomingGate = 40,
                    outgoingGate = 12,
                    freshOutgoingGate = 1,
                    freshOutgoingRequiredPeriod = 12,
                    freshOutgoingExpirePeriod = 2,

                    smsImmediateWaitTime = 2000,
                    smsDeferredWaitTime = 60,
                    serverSentWindowSize = 24,
                    maxServerSent = 2,
                    smsLinkExpirePeriod = 30
                )

            insertParametersWrapper(
                ParametersWrapper(
                    userNumber = userNumber,
                    parametersJson = initialParameters.toJson()
                )
            )
        }
    }

    /**
     * Directly retrieves parameters if it exists. Underlying code uses getParametersWrapper().
     */
    suspend fun getParameters(): Parameters? {
        return getParametersWrapper()?.getParameters()
    }

    /**
     * Retrieves ParametersWrapper if it exists. Instead of using userNumber to query, we can just
     * select one row from the ParametersWrapper table since there can only be 1 or 0 rows.
     */
    @Query("SELECT * FROM parameters LIMIT 1")
    suspend fun getParametersWrapper() : ParametersWrapper?

    /**
     * Returns whether or not the update was successful.
     */
    suspend fun updateParameters(
        parameters: Parameters
    ) : Boolean {
        // Retrieves user number if possible and returns false if not.
        val userNumber = getUserNumber() ?: return false

        // Can only update if the row already exists.
        if (getParametersWrapper() == null) return false

        val result = updateParametersQuery(
            userNumber = userNumber,
            parametersJson = parameters.toJson()
        )

        return result == 1
    }

    /**
     * Returns a nullable Int that indicates whether the update was successful. If 1 is returned,
     * then the update was successful, otherwise the update failed.
     */
    @Query("UPDATE parameters SET parametersJson = :parametersJson WHERE userNumber = :userNumber")
    suspend fun updateParametersQuery(userNumber: String, parametersJson: String) : Int?

    /**
     * Returns a nullable Int that indicates whether the delete was successful. If 1 is returned,
     * then the delete was successful, otherwise the delete failed.
     *
     * NOTE: We assume there is only one ParametersWrapper row, so the query should return 1 if successful.
     */
    @Query("DELETE FROM parameters WHERE userNumber = :userNumber")
    suspend fun deleteParameters(userNumber: String) : Int?
}