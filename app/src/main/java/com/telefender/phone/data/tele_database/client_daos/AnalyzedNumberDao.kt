package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.Analyzed
import com.telefender.phone.data.tele_database.entities.AnalyzedNumber
import com.telefender.phone.data.tele_database.entities.UploadQueue
import com.telefender.phone.data.tele_database.entities.isValidAnalyzed


@Dao
interface AnalyzedNumberDao : ParametersDao, StoredMapDao {

    /**
     * Unvalidated insert for AnalyzedNumber. Don't use outside of Dao.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnalyzedNumQuery(vararg analyzedNumber: AnalyzedNumber)

    @Query("SELECT * FROM analyzed_number")
    suspend fun getAllAnalyzedNum() : List<AnalyzedNumber>

    /**
     * TODO: Maybe we need to put a valid check for analyzedJson here?
     *
     * Gets AnalyzedNumber row given number and also initializes row if it doesn't already exist.
     * You can assume that the analyzedJson property is valid.
     *
     * NOTE: Requires use of lock since it may initialize the AnalyzedNumber for the number if the
     * row didn't previously exist.
     */
    suspend fun getAnalyzedNum(normalizedNumber: String) : AnalyzedNumber {
        // If no AnalyzedNumber row for number, then create row and initialize values
        initAnalyzedNum(normalizedNumber)
        return getAnalyzedNumQuery(normalizedNumber)!!
    }

    /**
     * TODO: Check if we're initializing the right values.
     *
     * Initializes AnalyzedNumber row for number if it doesn't already exist.
     */
    suspend fun initAnalyzedNum(number: String) : Boolean {
        if (getAnalyzedNumQuery(number) == null) {
            // We assume that StoredMap is initialized by now due to being core.
            val parameters = getParameters()

            val baseAnalyzed =
                Analyzed(
                    notifyGate = parameters.initialNotifyGate,
                    lastCallTime = null,
                    numIncoming = 0,
                    numOutgoing = 0,
                    maxDuration = 0,
                    avgDuration = 0.0,
                    smsVerified = false,
                    markedSafe = false,
                    isBlocked = false,
                    numMarkedBlocked = 0,
                    numSharedContacts = 0,
                    isOrganization = false,
                    minDegree = null,
                    numTreeContacts = 0,
                    degreeString = ""
                )

            insertAnalyzedNumQuery(
                AnalyzedNumber(
                    normalizedNumber = number,
                    analyzedJson = baseAnalyzed.toJson()
                )
            )

            return true
        } else {
            return false
        }
    }

    @Query("SELECT * FROM analyzed_number WHERE normalizedNumber = :normalizedNumber")
    suspend fun getAnalyzedNumQuery(normalizedNumber: String) : AnalyzedNumber?

    suspend fun updateAnalyzedNum(
        normalizedNumber: String,
        analyzed: Analyzed? = null
    ) {
        updateAnalyzedNum(
            AnalyzedNumber(
                normalizedNumber = normalizedNumber,
                analyzedJson = analyzed?.toJson() ?: "{}"
            ),
            confirmValid = analyzed != null
        )
    }

    /**
     * Updates AnalyzedNumber. If you are certain that the analyzedJson property of the passed
     * in AnalyzedNumber is a valid Analyzed JSON, then set [confirmValid] to true (for speed).
     */
    suspend fun updateAnalyzedNum(analyzedNumber: AnalyzedNumber, confirmValid: Boolean = false) {
        with(analyzedNumber) {
            // If no AnalyzedNumber row for number, then create row and initialize values
            initAnalyzedNum(normalizedNumber)

            updateAnalyzedNumQuery(
                normalizedNumber = normalizedNumber,
                analyzedJson = if (confirmValid || analyzedJson.isValidAnalyzed()) analyzedJson else null
            )
        }
    }

    @Query(
        """UPDATE analyzed_number SET 
        analyzedJson =
        CASE
            WHEN :analyzedJson IS NOT NULL
                THEN :analyzedJson
            ELSE analyzedJson
        END
        WHERE normalizedNumber = :normalizedNumber"""
    )
    suspend fun updateAnalyzedNumQuery(
        normalizedNumber: String,
        analyzedJson: String?
    )
}