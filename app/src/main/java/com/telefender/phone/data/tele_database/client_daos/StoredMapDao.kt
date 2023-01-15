package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.entities.StoredMap
import com.telefender.phone.helpers.TeleHelpers

@Dao
interface StoredMapDao {

    /**
     * DANGEROUS! Should not be used outside of Dao. Inserts StoredMap directly into database.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStoredMapQuery(vararg storedMap : StoredMap)

    /**
     * TODO: Maybe make StoredMap retrieval safer by following the method in ParametersDao. That
     *  way, if the user's only StoredMap gets deleted SOMEHOW, then we can just re-create an
     *  empty StoredMap and possibly redo setup process. Still iffy though.
     *
     * Initializes StoredMap with user's number. After first initialization, this function is
     * basically locked to make sure there is ONLY ONE StoredMap (which contains the user's number).
     * Make sure that you are passing in the right number!!!
     */
    suspend fun initStoredMap(userNumber: String) : Boolean {
        return if (getStoredMap() == null && userNumber != TeleHelpers.UNKNOWN_NUMBER) {
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
        firebaseToken: String? = null,
        databaseInitialized: Boolean? = null,
        lastLogSyncTime: Long? = null,
        lastServerRowID: Long? = null
    ) : Boolean {
        // Retrieves user number if possible and returns if not.
        val userNumber = getUserNumber() ?: return false

        updateStoredMapQuery(
            userNumber = userNumber,
            sessionID = sessionID,
            clientKey = clientKey,
            firebaseToken = firebaseToken,
            databaseInitialized = databaseInitialized,
            lastLogSyncTime = lastLogSyncTime,
            lastServerRowID = lastServerRowID
        )

        return true
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
        firebaseToken =
            CASE
                WHEN :firebaseToken IS NOT NULL
                    THEN :firebaseToken
                ELSE firebaseToken
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
        firebaseToken: String?,
        databaseInitialized: Boolean?,
        lastLogSyncTime: Long?,
        lastServerRowID: Long?
    )

    @Query("DELETE FROM stored_map")
    suspend fun deleteStoredMap()
}