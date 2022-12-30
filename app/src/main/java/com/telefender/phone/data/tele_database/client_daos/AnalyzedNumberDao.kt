package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.AnalyzedNumber


@Dao
interface AnalyzedNumberDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnalyzed(vararg analyzedNumber: AnalyzedNumber)

    /**
     * Gets AnalyzedNumber row given number and also initializes row if it doesn't already exist.
     */
    suspend fun getAnalyzed(normalizedNumber: String) : AnalyzedNumber {
        // If no AnalyzedNumber row for number, then create row and initialize values
        initAnalyzed(normalizedNumber)
        return getAnalyzedQuery(normalizedNumber)!!
    }

    /**
     * TODO: Check if we're initializing the right values.
     *
     * Initializes AnalyzedNumber row for number if it doesn't already exist.
     */
    suspend fun initAnalyzed(number: String) : Boolean {
        if (getAnalyzedQuery(number) == null) {
            insertAnalyzed(
                AnalyzedNumber(
                    normalizedNumber = number,
                    notifyGate = 2,
                    lastCallTime = null,
                    numIncoming = 0,
                    numOutgoing = 0,
                    maxDuration = 0,
                    avgDuration = 0,
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
            )

            return true
        } else {
            return false
        }
    }

    @Query("SELECT * FROM analyzed_number WHERE normalizedNumber = :normalizedNumber")
    suspend fun getAnalyzedQuery(normalizedNumber: String) : AnalyzedNumber?

    suspend fun updateAnalyzed(analyzedNumber: AnalyzedNumber) {
        with(analyzedNumber) {
            // If no AnalyzedNumber row for number, then create row and initialize values
            initAnalyzed(normalizedNumber)

            updateAnalyzedQuery(
                normalizedNumber = normalizedNumber,
                algoAllowed = algoAllowed,
                notifyGate = notifyGate,
                lastCallTime = lastCallTime,
                numIncoming = numIncoming,
                numOutgoing = numOutgoing,
                maxDuration = maxDuration,
                avgDuration = avgDuration,
                smsVerified = smsVerified,
                markedSafe = markedSafe,
                isBlocked = isBlocked,
                numMarkedBLocked = numMarkedBlocked,
                numSharedContacts = numSharedContacts,
                isOrganization = isOrganization,
                minDegree = minDegree,
                numTreeContacts = numTreeContacts,
                degreeString = degreeString
            )
        }
    }

    @Query(
        """UPDATE analyzed_number SET 
        algoAllowed =
            CASE
                WHEN :algoAllowed IS NOT NULL
                    THEN :algoAllowed
                ELSE algoAllowed
            END,
        notifyGate =
            CASE
                WHEN :notifyGate IS NOT NULL
                    THEN :notifyGate
                ELSE notifyGate
            END,
        lastCallTime =
            CASE
                WHEN :lastCallTime IS NOT NULL
                    THEN :lastCallTime
                ELSE lastCallTime
            END,
        numIncoming =
            CASE
                WHEN :numIncoming IS NOT NULL
                    THEN :numIncoming
                ELSE numIncoming
            END,
        numOutgoing =
            CASE
                WHEN :numOutgoing IS NOT NULL
                    THEN :numOutgoing
                ELSE numOutgoing
            END,
        maxDuration =
            CASE
                WHEN :maxDuration IS NOT NULL
                    THEN :maxDuration
                ELSE maxDuration
            END,
        avgDuration =
            CASE
                WHEN :avgDuration IS NOT NULL
                    THEN :avgDuration
                ELSE avgDuration
            END,
        smsVerified =
            CASE
                WHEN :smsVerified IS NOT NULL
                    THEN :smsVerified
                ELSE smsVerified
            END,
        markedSafe =
            CASE
                WHEN :markedSafe IS NOT NULL
                    THEN :markedSafe
                ELSE markedSafe
            END,
        isBlocked =
            CASE
                WHEN :isBlocked IS NOT NULL
                    THEN :isBlocked
                ELSE isBlocked
            END,
        numMarkedBlocked =
            CASE
                WHEN :numMarkedBLocked IS NOT NULL
                    THEN :numMarkedBLocked
                ELSE numMarkedBlocked
            END,
        numSharedContacts =
            CASE
                WHEN :numSharedContacts IS NOT NULL
                    THEN :numSharedContacts
                ELSE numSharedContacts
            END,
        isOrganization =
            CASE
                WHEN :isOrganization IS NOT NULL
                    THEN :isOrganization
                ELSE isOrganization
            END,
        minDegree =
            CASE
                WHEN :minDegree IS NOT NULL
                    THEN :minDegree
                ELSE minDegree
            END,
        numTreeContacts =
            CASE
                WHEN :numTreeContacts IS NOT NULL
                    THEN :numTreeContacts
                ELSE numTreeContacts
            END,
        degreeString =
            CASE
                WHEN :degreeString IS NOT NULL
                    THEN :degreeString
                ELSE degreeString
            END
        WHERE normalizedNumber = :normalizedNumber"""
    )
    suspend fun updateAnalyzedQuery(
        normalizedNumber: String,
        algoAllowed: Boolean?,
        notifyGate: Int?,
        lastCallTime: Long?,
        numIncoming: Int?,
        numOutgoing: Int?,
        maxDuration: Long?,
        avgDuration: Long?,
        smsVerified: Boolean?,
        markedSafe: Boolean?,
        isBlocked: Boolean?,
        numMarkedBLocked: Int?,
        numSharedContacts: Int?,
        isOrganization: Boolean?,
        minDegree: Int?,
        numTreeContacts: Int?,
        degreeString: String?
    )
}