package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.AnalyzedNumber
import com.telefender.phone.data.tele_database.entities.Parameters


@Dao
interface ParametersDao : StoredMapDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParametersQuery(vararg parameters: Parameters)

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

    suspend fun initParameters(userNumber: String) : Boolean {
        return if (getParametersQuery(userNumber) == null) {
            // Initial parameter values.
            insertParametersQuery(
                Parameters(
                    userNumber = userNumber,
                    initialNotifyGate = 2,
                    verifiedSpamNotifyGate = 7,
                    superSpamNotifyGate = 14
                )
            )

            true
        } else {
            false
        }
    }

    @Query("SELECT * FROM parameters WHERE userNumber = :userNumber")
    suspend fun getParametersQuery(userNumber: String) : Parameters?

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
            END
        WHERE userNumber = :number"""
    )
    suspend fun updateParameters(
        number: String,
        initialNotifyGate: Int?,
        verifiedSpamNotifyGate: Int?,
        superSpamNotifyGate: Int?
    )

    @Query("DELETE FROM parameters WHERE userNumber = :userNumber")
    suspend fun deleteParameters(userNumber: String)
}