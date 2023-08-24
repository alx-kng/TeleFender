package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.call_related.HandleMode
import com.telefender.phone.data.tele_database.entities.StoredMap
import com.telefender.phone.misc_helpers.TeleHelpers

@Dao
interface StoredMapDao {

    /**
     * DANGEROUS! Should not be used outside of Dao. Inserts StoredMap directly into database.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStoredMapQuery(vararg storedMap : StoredMap)

    /**
     * Initializes StoredMap with user's number. After first initialization, this function is
     * basically locked to make sure there is ONLY ONE StoredMap (which contains the user's number).
     * Make sure that you are passing in the right number!!!
     *
     * NOTE: Currently no need to return whether the StoredMap insert was successful or not, as we
     * check for initialization using a separate SELECT query in ClientDatabase / TeleHelpers.
     */
    suspend fun initStoredMap(userNumber: String) {
        if (getStoredMap() == null && userNumber != TeleHelpers.UNKNOWN_NUMBER) {
            // Initialize StoredMap with just userNumber.
            insertStoredMapQuery(
                StoredMap(
                    userNumber = userNumber,
                    currentHandleMode = HandleMode.ALLOW_MODE
                )
            )
        }
    }

    /**
     * Retrieves user's StoredMap if it exists.
     */
    @Query("SELECT * FROM stored_map LIMIT 1")
    suspend fun getStoredMap() : StoredMap?

    suspend fun getUserNumber() : String? {
        return getStoredMap()?.userNumber
    }

    /**
     * Returns whether or not the update was successful.
     */
    suspend fun updateStoredMap(
        sessionID: String? = null,
        clientKey: String? = null,
        firebaseToken: String? = null,
        lastLogSyncTime: Long? = null,
        lastLogFullSyncTime: Long? = null,
        lastContactFullSyncTime: Long? = null,
        lastServerRowID: Long? = null,
        handleMode: HandleMode? = null
    ) : Boolean {
        // Retrieves user number if possible and returns false if not.
        val userNumber = getUserNumber() ?: return false

        // Can only update if the row already exists.
        if (getStoredMap() == null) return false

        val result =  updateStoredMapQuery(
            userNumber = userNumber,
            sessionID = sessionID,
            clientKey = clientKey,
            firebaseToken = firebaseToken,
            lastLogSyncTime = lastLogSyncTime,
            lastLogFullSyncTime = lastLogFullSyncTime,
            lastContactFullSyncTime = lastContactFullSyncTime,
            lastServerRowID = lastServerRowID,
            handleMode = handleMode
        )

        return result == 1
    }

    /**
     * Returns a nullable Int that indicates whether the update was successful. If 1 is returned,
     * then the update was successful, otherwise the update failed.
     */
    @Query(
        """UPDATE stored_map SET 
        sessionID =
            CASE
                WHEN :sessionID IS NOT NULL
                    THEN :sessionID
                ELSE sessionID
            END,
        clientKey =
            CASE
                WHEN :clientKey IS NOT NULL
                    THEN :clientKey
                ELSE clientKey
            END,
        firebaseToken =
            CASE
                WHEN :firebaseToken IS NOT NULL
                    THEN :firebaseToken
                ELSE firebaseToken
            END, 
        lastLogSyncTime = 
            CASE
                WHEN :lastLogSyncTime IS NOT NULL
                    THEN :lastLogSyncTime
                ELSE lastLogSyncTime
            END,
        lastLogFullSyncTime = 
            CASE
                WHEN :lastLogFullSyncTime IS NOT NULL
                    THEN :lastLogFullSyncTime
                ELSE lastLogFullSyncTime
            END,
        lastContactFullSyncTime = 
            CASE
                WHEN :lastContactFullSyncTime IS NOT NULL
                    THEN :lastContactFullSyncTime
                ELSE lastContactFullSyncTime
            END,
        lastServerRowID = 
            CASE
                WHEN :lastServerRowID IS NOT NULL
                    THEN :lastServerRowID
                ELSE lastServerRowID
            END,
        currentHandleMode = 
            CASE
                WHEN :handleMode IS NOT NULL
                    THEN :handleMode
                ELSE currentHandleMode
            END
        WHERE userNumber = :userNumber"""
    )
    suspend fun updateStoredMapQuery(
        userNumber: String,
        sessionID: String?,
        clientKey: String?,
        firebaseToken: String?,
        lastLogSyncTime: Long?,
        lastLogFullSyncTime: Long?,
        lastContactFullSyncTime: Long?,
        lastServerRowID: Long?,
        handleMode: HandleMode?
    ) : Int?

    /**
     * Returns a nullable Int that indicates whether the delete was successful (number of rows
     * delete). If a value >0 is returned, then the delete was at least partially successful,
     * otherwise the delete completely failed (if there were existing rows).
     *
     * NOTE: We assume there is only one StoredMap row, the query should return 1 if successful.
     */
    @Query("DELETE FROM stored_map")
    suspend fun deleteStoredMap() : Int?
}