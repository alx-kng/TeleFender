package com.dododial.phone.data.dodo_database.client_daos

import androidx.room.Dao
import androidx.room.Query
import com.dododial.phone.data.dodo_database.entities.Organizations

@Dao
interface OrganizationsDao {
    
    
    @Query("INSERT INTO organizations (number) VALUES (:number)")
    suspend fun insertOrganizationsHelper(number: String)

    @Query("UPDATE organizations SET number = :newNum WHERE number = :oldNum")
    suspend fun updateOrganizationsHelper(oldNum: String, newNum : String)

    @Query("UPDATE organizations SET counter = counter + :counterDelta WHERE number = :number")
    suspend fun updateCounterOrganizations(number: String, counterDelta: Int)

    @Query("SELECT COUNT(number) from organizations WHERE number = :number")
    suspend fun getOrganizationsCount(number: String): Int

    @Query("SELECT * FROM organizations")
    suspend fun getAllOrganizations() : List<Organizations>

    @Query("SELECT * FROM organizations WHERE number = :number")
    suspend fun getOrganizationsRow(number : String) : Organizations

    @Query("SELECT counter FROM organizations where number = :number")
    suspend fun getCounterOrganizations(number: String): Int

    @Query("DELETE FROM organizations WHERE number = :number")
    suspend fun deleteOrganizations_Number(number: String)

    @Query("DELETE FROM organizations")
    suspend fun deleteAllOrganizations()

    suspend fun insertOrganizations(num: String) {
        if (getOrganizationsCount(num) != 0) {
            updateCounterOrganizations(num, 1)
        }
        else {
            insertOrganizationsHelper(num)
        }
    }
    suspend fun updateOrganizations(oldNum: String, newNum: String) {
        // if counter == 1,update number
        // else, counter -= 1 and insert new number newNum
        if (getCounterOrganizations(oldNum) == 1) {
            updateOrganizationsHelper(oldNum, newNum)
        }
        else {
            updateCounterOrganizations(oldNum, -1)
            insertOrganizations(newNum)
        }
    }

    suspend fun deleteOrganizations(num : String) {
        if (getCounterOrganizations(num) == 1) {
            deleteOrganizations_Number(num)
        }
        else {
            updateCounterOrganizations(num, -1)
        }
    }
}