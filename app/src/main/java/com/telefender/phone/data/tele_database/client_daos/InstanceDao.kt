package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.entities.Instance

@Dao
interface InstanceDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInstanceNumbers(vararg instances: Instance)

    /**
     * Returns a nullable Int that indicates whether the update was successful. If 1 is returned,
     * then the update was successful, otherwise the update failed.
     */
    @Query("UPDATE instance SET number = :newNumber WHERE number = :oldNumber")
    suspend fun updateInstanceNumbers(oldNumber: String, newNumber: String) : Int?
    
    @Query("SELECT * FROM instance")
    suspend fun getAllInstance() : List<Instance>

    @Query("SELECT * FROM instance WHERE number = :number")
    suspend fun getInstanceRow(number : String) : Instance
    
    @Query("SELECT EXISTS (SELECT number FROM instance WHERE number = :instanceNumber)")
    suspend fun hasInstance(instanceNumber: String) : Boolean
    
    @Delete
    suspend fun deleteInstanceNumbers(vararg instances: Instance)

    @Query("DELETE FROM instance")
    suspend fun deleteAllInstanceNumbers()
}