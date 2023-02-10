package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.MutexType
import com.telefender.phone.data.tele_database.TeleLocks.mutexLocks
import com.telefender.phone.data.tele_database.entities.*
import kotlinx.coroutines.sync.withLock


@Dao
interface AnalyzedNumberDao : ParametersDao, StoredMapDao, UploadAnalyzedQueueDao {

    /**
     * Inserts AnalyzedNumber and checks that the analyzedJson is valid. Returns inserted
     * rowID.
     *
     * NOTE: throws an Exception if the analyzedJson is invalid, so make sure that you handle the
     * possible Exception higher up.
     */
    suspend fun insertAnalyzedNum(analyzedNumber: AnalyzedNumber) : Long {
        if (analyzedNumber.analyzedJson.isValidAnalyzed()) {
            return insertAnalyzedNumQuery(analyzedNumber)
        } else {
            throw Exception("analyzedJson is invalid - $analyzedNumber")
        }
    }

    /**
     * Unvalidated insert for AnalyzedNumber. Returns inserted rowID. Don't use outside of Dao.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnalyzedNumQuery(analyzedNumber: AnalyzedNumber) : Long

    /**
     * Retrieves all AnalyzedNumbers. Annotated with Transaction for large query safety.
     */
    @Transaction
    @Query("SELECT * FROM analyzed_number")
    suspend fun getAllAnalyzedNum() : List<AnalyzedNumber>

    /**
     * Same as getAnalyzedNum() but doesn't auto initialize. Should be used for RuleChecker.
     */
    suspend fun getAnalyzedNumForCheck(
        normalizedNumber: String,
        instanceParam: String? = null,
    ) : AnalyzedNumber? {
        val userNumber = getUserNumber()
        val instanceNumber = instanceParam ?: userNumber ?: return null

        return getAnalyzedNumQuery(normalizedNumber, instanceNumber)
    }

    @Query("SELECT * FROM analyzed_number WHERE rowID = :rowID")
    suspend fun getAnalyzedNum(rowID: Long) : AnalyzedNumber?

    /**
     * TODO: Maybe we need to put a valid check for analyzedJson here?
     *
     * Gets AnalyzedNumber row given number and also initializes row if it doesn't already exist
     * and the associated instanceNumber is same as user's number (to prevent unnecessary
     * initializations other users AnalyzedNumbers). You can assume that the analyzedJson property
     * is valid.
     *
     * NOTE: if not specified, instanceNumber is assumed to be user's number.
     *
     * NOTE: Requires use of lock since it may initialize the AnalyzedNumber for the number if the
     * row didn't previously exist.
     */
    suspend fun getAnalyzedNum(
        normalizedNumber: String,
        instanceParam: String? = null,
    ) : AnalyzedNumber? {
        val userNumber = getUserNumber()
        val instanceNumber = instanceParam ?: userNumber ?: return null

        if (instanceNumber == userNumber){
            initAnalyzedNum(normalizedNumber, instanceNumber)
        }
        return getAnalyzedNumQuery(normalizedNumber, instanceNumber)
    }

    @Query("SELECT * FROM analyzed_number WHERE instanceNumber = :instanceNumber AND normalizedNumber = :normalizedNumber")
    suspend fun getAnalyzedNumQuery(normalizedNumber: String, instanceNumber: String) : AnalyzedNumber?

    /**
     * TODO: Confirm / find better control follow for adding to UploadAnalyzedQueue.
     * TODO: More error handling for AnalyzedQTU adding.
     *
     * TODO: Check for insert success?
     *
     * Initializes AnalyzedNumber row for number (under the user's number) if it doesn't already
     * exist. Returns whether row was initialized. Additionally, if initialized, we also add an
     * AnalyzedQTU here.
     *
     * NOTE: Assumes that StoredMap is already initialized by now.
     */
    suspend fun initAnalyzedNum(normalizedNumber: String, instanceNumber: String) : Boolean {
        if (getAnalyzedNumQuery(normalizedNumber, instanceNumber) == null) {
            // We assume that StoredMap is initialized by now due to being core.
            val parameters = getParameters() ?: return false

            val baseAnalyzed =
                Analyzed(
                    notifyGate = parameters.initialNotifyGate,
                    notifyCounter = 0,

                    // Important actions
                    smsVerified = false,
                    markedSafe = false,
                    isBlocked = false,

                    // General type
                    lastCallTime = null,
                    lastCallDirection = null,
                    lastCallDuration = null,
                    maxDuration = 0,
                    avgDuration = 0.0,

                    // Incoming subtype
                    numIncoming = 0,
                    lastIncomingTime = null,
                    lastIncomingDuration = null,
                    maxIncomingDuration = 0,
                    avgIncomingDuration = 0.0,

                    // Outgoing subtype
                    numOutgoing = 0,
                    lastOutgoingTime = null,
                    lastOutgoingDuration = null,
                    maxOutgoingDuration = 0,
                    avgOutgoingDuration = 0.0,

                    // Voicemail subtype
                    numVoicemail = 0,
                    lastVoicemailTime = null,
                    lastVoicemailDuration = null,
                    maxVoicemailDuration = 0,
                    avgVoicemailDuration = 0.0,

                    // Missed subtype
                    numMissed = 0,
                    lastMissedTime = null,

                    // Rejected subtype
                    numRejected = 0,
                    lastRejectedTime = null,

                    // Blocked subtype
                    numBlocked = 0,
                    lastBlockedTime = null,

                    numMarkedBlocked = 0,
                    numSharedContacts = 0,
                    numTreeContacts = 0,
                    degreeString = "",
                    minDegree = null,
                    isOrganization = false
                )

            val linkedRowID = insertAnalyzedNumQuery(
                AnalyzedNumber(
                    normalizedNumber = normalizedNumber,
                    instanceNumber = instanceNumber,
                    numTotalCalls = 0,
                    analyzedJson = baseAnalyzed.toJson()
                )
            )

            // If linkedRowID < 0, then the insert query failed.
            if (linkedRowID < 0) throw Exception("initAnalyzedNum() - insertAnalyzedNumQuery() failed!")

            mutexLocks[MutexType.UPLOAD_ANALYZED]!!.withLock {
                val upLog = UploadAnalyzedQueue(linkedRowID = linkedRowID)
                insertAnalyzedQTU(upLog)
            }

            return true
        } else {
            return false
        }
    }

    /**
     * Updates AnalyzedNumber. If trying to update user's AnalyzedNumber and no row exists,
     * we initialize it. Returns whether row was successfully inserted.
     *
     * NOTE: if not specified, instanceNumber is assumed to be user's number.
     */
    suspend fun updateAnalyzedNum(
        normalizedNumber: String,
        instanceParam: String? = null,
        numTotalCalls: Int? = null,
        analyzed: Analyzed? = null
    ) : Boolean {
        val userNumber = getUserNumber()
        val instanceNumber = instanceParam ?: userNumber ?: return false

        if (instanceNumber == userNumber) {
            initAnalyzedNum(normalizedNumber, instanceNumber)

            val result = updateAnalyzedNumQuery(
                normalizedNumber = normalizedNumber,
                instanceNumber  = instanceNumber,
                numTotalCalls = numTotalCalls,
                analyzedJson = analyzed?.toJson()
            )

            return result == 1
        } else {
            // Check if exists before updating.
            if (getAnalyzedNumQuery(normalizedNumber, instanceNumber) == null) {
                return false
            }

            val result = updateAnalyzedNumQuery(
                normalizedNumber = normalizedNumber,
                instanceNumber  = instanceNumber,
                numTotalCalls = numTotalCalls,
                analyzedJson = analyzed?.toJson()
            )

            return result == 1
        }
    }

    /**
     * Updates AnalyzedNumber. If trying to update user's AnalyzedNumber and no row exists,
     * we initialize it. Returns whether row was successfully inserted and updated.
     */
    suspend fun updateAnalyzedNum(analyzedNumber: AnalyzedNumber) : Boolean {
        with(analyzedNumber) {
            val userNumber = getUserNumber() ?: return false

            if (instanceNumber == userNumber) {
                initAnalyzedNum(normalizedNumber, instanceNumber)

                val result = updateAnalyzedNumQuery(
                    normalizedNumber = normalizedNumber,
                    instanceNumber  = instanceNumber,
                    numTotalCalls = numTotalCalls,
                    analyzedJson = if (analyzedJson.isValidAnalyzed()) analyzedJson else null
                )

                return result == 1
            } else {
                // Check if exists before updating.
                if (getAnalyzedNumQuery(normalizedNumber, instanceNumber) == null) {
                    return false
                }

                val result = updateAnalyzedNumQuery(
                    normalizedNumber = normalizedNumber,
                    instanceNumber  = instanceNumber,
                    numTotalCalls = numTotalCalls,
                    analyzedJson = if (analyzedJson.isValidAnalyzed()) analyzedJson else null
                )

                return result == 1
            }
        }
    }

    /**
     * Returns a nullable Int that indicates whether the update was successful. If 1 is returned,
     * then the update was successful, otherwise the update failed.
     */
    @Query(
        """UPDATE analyzed_number SET 
        numTotalCalls =
        CASE
            WHEN :numTotalCalls IS NOT NULL
                THEN :numTotalCalls
            ELSE numTotalCalls
        END,
        analyzedJson =
        CASE
            WHEN :analyzedJson IS NOT NULL
                THEN :analyzedJson
            ELSE analyzedJson
        END
        WHERE instanceNumber = :instanceNumber AND normalizedNumber = :normalizedNumber"""
    )
    suspend fun updateAnalyzedNumQuery(
        normalizedNumber: String,
        instanceNumber: String,
        numTotalCalls: Int?,
        analyzedJson: String?
    ) : Int?

    /**
     * Returns a nullable Int that indicates whether the delete was successful. If 1 is returned,
     * then the delete was successful, otherwise the delete failed.
     */
    @Query("DELETE FROM analyzed_number WHERE rowID = :rowID")
    suspend fun deleteAnalyzedNumber(rowID: Long) : Int?
}