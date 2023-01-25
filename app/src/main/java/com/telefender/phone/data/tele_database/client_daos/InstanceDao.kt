package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.entities.Instance

@Dao
interface InstanceDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInstanceNumber(vararg instances: Instance)

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

    /**
     * Returns a nullable Int that indicates whether the delete was successful. If 1 is returned,
     * then the delete was successful, otherwise the delete failed.
     */
    @Delete
    suspend fun deleteInstanceNumber(instances: Instance) : Int?

    /**
     * Returns a nullable Int that indicates whether the delete was successful (number of rows
     * delete). If a value >0 is returned, then the delete was at least partially successful,
     * otherwise the delete completely failed (if there were existing rows).
     */
    @Query("DELETE FROM instance")
    suspend fun deleteAllInstanceNumbers() : Int?
}