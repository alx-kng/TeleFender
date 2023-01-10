package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.Parameters
import com.telefender.phone.data.tele_database.entities.StoredMap
import com.telefender.phone.helpers.MiscHelpers


@Dao
interface ParametersDao : StoredMapDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParametersQuery(vararg parameters: Parameters)

    suspend fun initParameters(userNumber: String) : Boolean {
        return if (getParametersQuery(userNumber) == null && userNumber != MiscHelpers.UNKNOWN_NUMBER) {
            // Initial parameter values.
            insertParametersQuery(
                Parameters(
                    userNumber = userNumber,
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
     * Retrieves parameters. If no Parameters available, then initialize values.
     *
     * NOTE: We assume here that StoredMap is initialized.
     */
    suspend fun getParameters() : Parameters {
        val userNumber = getUserNumber()!!
        initParameters(userNumber)
        return getParametersQuery(userNumber)!!
    }

    @Query("SELECT * FROM parameters WHERE userNumber = :userNumber")
    suspend fun getParametersQuery(userNumber: String) : Parameters?

    suspend fun updateParameters(
        initialNotifyGate: Int? = null,
        verifiedSpamNotifyGate: Int? = null,
        superSpamNotifyGate: Int? = null,
        incomingGate: Int? = null,
        outgoingGate: Int? = null
    ) {
        val userNumber = getUserNumber()!!
        initParameters(userNumber)

        updateParametersQuery(
            userNumber = userNumber,
            initialNotifyGate = initialNotifyGate,
            verifiedSpamNotifyGate = verifiedSpamNotifyGate,
            superSpamNotifyGate = superSpamNotifyGate,
            incomingGate = incomingGate,
            outgoingGate = outgoingGate
        )
    }

    @Query(
        """UPDATE parameters SET 
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
        initialNotifyGate: Int?,
        verifiedSpamNotifyGate: Int?,
        superSpamNotifyGate: Int?,
        incomingGate: Int?,
        outgoingGate: Int?
    )

    @Query("DELETE FROM parameters WHERE userNumber = :userNumber")
    suspend fun deleteParameters(userNumber: String)
}