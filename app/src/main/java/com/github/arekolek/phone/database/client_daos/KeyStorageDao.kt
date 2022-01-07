package com.github.arekolek.phone.database.client_daos

import androidx.room.*
import com.github.arekolek.phone.database.KeyStorage

@Dao
interface KeyStorageDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertKey(vararg keyStorage : KeyStorage)

    @Query("UPDATE key_storage SET clientKey = :clientKey WHERE number = :number")
    suspend fun updateKey(clientKey: String, number: String)

    @Query("SELECT clientKey FROM key_storage WHERE number = :number")
    suspend fun getCredKey(number: String): String

    @Delete
    suspend fun deleteKey(vararg keyStorage: KeyStorage)

    @Query("DELETE FROM key_storage")
    suspend fun deleteAllKeys()
}