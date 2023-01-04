package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.entities.AnalyzedNumber
import com.telefender.phone.data.tele_database.entities.Parameters
import com.telefender.phone.data.tele_database.entities.StoredMap

@Dao
interface StoredMapDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStoredMapQuery(vararg storedMap : StoredMap)

    suspend fun initStoredMap(userNumber: String) : Boolean{
        return if (getStoredMapQuery(userNumber) == null) {
            // Initialize StoredMap with just userNumber values.
            insertStoredMapQuery(StoredMap(userNumber = userNumber))

            true
        } else {
            false
        }
    }

    @Query("SELECT * FROM stored_map WHERE userNumber = :userNumber")
    suspend fun getStoredMapQuery(userNumber: String) : StoredMap?

    @Query("SELECT clientKey FROM stored_map WHERE userNumber = :number")
    suspend fun getCredKey(number: String): String?

    suspend fun hasCredKey(instanceNumber: String) : Boolean {
        return getCredKey(instanceNumber) != null
    }

    suspend fun getUserNumber() : String? {
        val storedMap = getStoredMapAsList().firstOrNull()
        return storedMap?.userNumber
    }

    /**
     * Specifically used for retrieving user's number. Since we're retrieving without PK, we need
     * to return a list of StoredMap.
     */
    @Query("SELECT * FROM stored_map")
    suspend fun getStoredMapAsList(): List<StoredMap>
    
    @Query("SELECT sessionID FROM stored_map WHERE userNumber = :number")
    suspend fun getSessionID(number: String?): String?

    @Query("SELECT fireBaseToken FROM stored_map WHERE userNumber = :number")
    suspend fun getFireBaseToken(number : String) : String?

    @Query("SELECT lastSyncTime FROM stored_map WHERE userNumber = :number")
    suspend fun getLastSyncTime(number : String) : Long

    @Query("SELECT databaseInitialized FROM stored_map WHERE userNumber = :number")
    suspend fun databaseInitialized(number: String) : Boolean?

    @Query("SELECT EXISTS (SELECT * FROM stored_map LIMIT 1)")
    suspend fun storedMapInitialized() : Boolean

    @Query("UPDATE stored_map SET databaseInitialized = :initialized WHERE userNumber = :number")
    suspend fun updateDatabaseInitialized(number: String, initialized: Boolean)

    @Query(
        """UPDATE stored_map SET 
        sessionID =
        CASE
            WHEN :sessionId IS NOT NULL
                THEN :sessionId
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
        lastSyncTime = 
            CASE
                WHEN :lastSyncTime IS NOT NULL
                    THEN :lastSyncTime
                ELSE lastSyncTime
            END
        WHERE userNumber = :number"""
    )
    suspend fun updateStoredMap(
        number: String,
        sessionId: String? = null,
        clientKey: String? = null,
        fireBaseToken : String? = null,
        lastSyncTime : Long? = null
    )

    @Delete
    suspend fun deleteStoredMap(vararg storedMap: StoredMap)

    @Query("DELETE FROM stored_map")
    suspend fun deleteAllStoredMaps()
}