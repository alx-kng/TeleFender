package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.Parameters
import com.telefender.phone.helpers.TeleHelpers


@Dao
interface ParametersDao : StoredMapDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParametersQuery(vararg parameters: Parameters)

    suspend fun initParameters(userNumber: String) : Boolean {
        return if (getParametersQuery(userNumber) == null && userNumber != TeleHelpers.UNKNOWN_NUMBER) {
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
                    outgoingGate = 12
                )
            )

            true
        } else {
            false
        }
    }

    /**
     * TODO: Finish including Parameters initialization as part of initialization process!
     */

    /**
     * Retrieves parameters. If no Parameters available, then initialize values.
     *
     * NOTE: It's STRONGLY advised that you put a try-catch around any use cases of this,
     * especially if you plan on non-null asserting the return, as there is a real possibility of
     * an error (especially if the database isn't yet initialized).
     */
    suspend fun getParameters() : Parameters? {
        val userNumber = getUserNumber() ?: return null
        initParameters(userNumber)
        return getParametersQuery(userNumber)
    }

    @Query("SELECT * FROM parameters WHERE userNumber = :userNumber")
    suspend fun getParametersQuery(userNumber: String) : Parameters?

    /**
     * TODO: init shouldn't be done here.
     *
     * Returns whether or not the update was successful.
     */
    suspend fun updateParameters(
        shouldUploadAnalyzed: Boolean? = null,
        shouldUploadLogs: Boolean? = null,
        initialNotifyGate: Int? = null,
        verifiedSpamNotifyGate: Int? = null,
        superSpamNotifyGate: Int? = null,
        incomingGate: Int? = null,
        outgoingGate: Int? = null
    ) : Boolean {
        val userNumber = getUserNumber() ?: return false
        initParameters(userNumber)

        val result = updateParametersQuery(
            userNumber = userNumber,
            shouldUploadAnalyzed = shouldUploadAnalyzed,
            shouldUploadLogs = shouldUploadLogs,
            initialNotifyGate = initialNotifyGate,
            verifiedSpamNotifyGate = verifiedSpamNotifyGate,
            superSpamNotifyGate = superSpamNotifyGate,
            incomingGate = incomingGate,
            outgoingGate = outgoingGate
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
        outgoingGate: Int?
    ) : Int?

    @Query("DELETE FROM parameters WHERE userNumber = :userNumber")
    suspend fun deleteParameters(userNumber: String)
}