package com.dododial.phone.data.dodo_database.client_daos

import androidx.room.*
import com.dododial.phone.data.dodo_database.entities.KeyStorage

@Dao
interface KeyStorageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKey(vararg keyStorage : KeyStorage)

    @Query("SELECT clientKey FROM key_storage WHERE number = :number")
    suspend fun getCredKey(number: String): String?
    
    @Query("SELECT sessionID FROM key_storage WHERE number = :number")
    suspend fun getSessionID(number: String): String

    @Query("SELECT fireBaseToken FROM key_storage WHERE number = :number")
    suspend fun getFireBaseToken(number : String) : String?

    @Query("""UPDATE key_storage SET clientKey =
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
            END
        WHERE number = :number""")
    suspend fun updateKey(number: String, clientKey: String?, fireBaseToken : String?)

    @Delete
    suspend fun deleteKey(vararg keyStorage: KeyStorage)

    @Query("DELETE FROM key_storage")
    suspend fun deleteAllKeys()

    suspend fun hasCredKey(instanceNumber: String) : Boolean {
        return getCredKey(instanceNumber) != null
    }
}