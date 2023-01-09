package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.entities.StoredMap
import com.telefender.phone.helpers.MiscHelpers

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
     */
    suspend fun initStoredMap(userNumber: String) : Boolean{
        return if (getStoredMap() == null && userNumber != MiscHelpers.UNKNOWN_NUMBER) {
            // Initialize StoredMap with just userNumber.
            insertStoredMapQuery(StoredMap(userNumber = userNumber))

            true
        } else {
            false
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

    suspend fun databaseInitialized() : Boolean {
        return getStoredMap()?.databaseInitialized == true
    }

    suspend fun updateStoredMap(
        sessionID: String? = null,
        clientKey: String? = null,
        fireBaseToken: String? = null,
        databaseInitialized: Boolean? = null,
        lastLogSyncTime: Long? = null,
        lastServerRowID: Long? = null
    ) {
        // Retrieves user number if possible and returns if not.
        val userNumber = getUserNumber() ?: return

        updateStoredMapQuery(
            userNumber = userNumber,
            sessionID = sessionID,
            clientKey = clientKey,
            fireBaseToken = fireBaseToken,
            databaseInitialized = databaseInitialized,
            lastLogSyncTime = lastLogSyncTime,
            lastServerRowID = lastServerRowID
        )
    }

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
        fireBaseToken =
            CASE
                WHEN :fireBaseToken IS NOT NULL
                    THEN :fireBaseToken
                ELSE fireBaseToken
            END,
        databaseInitialized =
            CASE
                WHEN :databaseInitialized IS NOT NULL
                    THEN :databaseInitialized
                ELSE databaseInitialized
            END,            
        lastLogSyncTime = 
            CASE
                WHEN :lastLogSyncTime IS NOT NULL
                    THEN :lastLogSyncTime
                ELSE lastLogSyncTime
            END,
        lastServerRowID = 
            CASE
                WHEN :lastServerRowID IS NOT NULL
                    THEN :lastServerRowID
                ELSE lastServerRowID
            END
        WHERE userNumber = :userNumber"""
    )
    suspend fun updateStoredMapQuery(
        userNumber: String,
        sessionID: String?,
        clientKey: String?,
        fireBaseToken: String?,
        databaseInitialized: Boolean?,
        lastLogSyncTime: Long?,
        lastServerRowID: Long?
    )

    @Query("DELETE FROM stored_map")
    suspend fun deleteStoredMap()
}