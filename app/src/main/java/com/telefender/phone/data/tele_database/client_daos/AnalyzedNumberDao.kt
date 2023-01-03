package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.Analyzed
import com.telefender.phone.data.tele_database.entities.AnalyzedNumber
import com.telefender.phone.data.tele_database.entities.isValidAnalyzed


@Dao
interface AnalyzedNumberDao {

    /**
     * Unvalidated insert for AnalyzedNumber. Don't use outside of Dao.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnalyzedNumQuery(vararg analyzedNumber: AnalyzedNumber)

    @Query("SELECT * FROM analyzed_number")
    suspend fun getAllAnalyzedNum() : List<AnalyzedNumber>

    /**
     * TODO: Maybe we need to put a valid check for analyzedValues here?
     *
     * Gets AnalyzedNumber row given number and also initializes row if it doesn't already exist.
     * You can assume that the analyzedValues property is valid.
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
            val baseAnalyzed =
                Analyzed(
                    notifyGate = 2,
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
                    analyzedValues = baseAnalyzed.toJson()
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
                analyzedValues = analyzed?.toJson() ?: "{}"
            ),
            confirmValid = analyzed != null
        )
    }

    /**
     * Updates AnalyzedNumber. If you are certain that the analyzedValues property of the passed
     * in AnalyzedNumber is a valid Analyzed JSON, then set [confirmValid] to true (for speed).
     */
    suspend fun updateAnalyzedNum(analyzedNumber: AnalyzedNumber, confirmValid: Boolean = false) {
        with(analyzedNumber) {
            // If no AnalyzedNumber row for number, then create row and initialize values
            initAnalyzedNum(normalizedNumber)

            updateAnalyzedNumQuery(
                normalizedNumber = normalizedNumber,
                analyzedValues = if (confirmValid || analyzedValues.isValidAnalyzed()) analyzedValues else null
            )
        }
    }

    @Query(
        """UPDATE analyzed_number SET 
        analyzedValues =
        CASE
            WHEN :analyzedValues IS NOT NULL
                THEN :analyzedValues
            ELSE analyzedValues
        END
        WHERE normalizedNumber = :normalizedNumber"""
    )
    suspend fun updateAnalyzedNumQuery(
        normalizedNumber: String,
        analyzedValues: String?
    )
}