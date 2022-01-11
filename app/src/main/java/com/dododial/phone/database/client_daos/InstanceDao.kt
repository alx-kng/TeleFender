package com.dododial.phone.database.client_daos

import androidx.room.*
import com.dododial.phone.database.entities.Instance

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

    @Delete
    suspend fun deleteInstanceNumbers(vararg instances: Instance)

    @Query("DELETE FROM instance")
    suspend fun deleteAllInstanceNumbers()
}