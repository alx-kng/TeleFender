package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.entities.Instance

@Dao
interface InstanceDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInstanceNumbers(vararg instances: Instance)

    @Query("UPDATE instance SET number = :newNumber WHERE number = :oldNumber")
    suspend fun updateInstanceNumbers(oldNumber: String, newNumber: String)
    
    @Query("SELECT * FROM instance")
    suspend fun getAllInstance() : List<Instance>

    @Query("SELECT * FROM instance WHERE number = :number")
    suspend fun getInstanceRow(number : String) : Instance
    
    @Query("SELECT EXISTS (SELECT number FROM instance LIMIT 1)")
    suspend fun hasInstance() : Boolean
    
    @Delete
    suspend fun deleteInstanceNumbers(vararg instances: Instance)

    @Query("DELETE FROM instance")
    suspend fun deleteAllInstanceNumbers()
}