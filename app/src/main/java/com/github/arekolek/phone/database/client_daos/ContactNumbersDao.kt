package com.github.arekolek.phone.database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.arekolek.phone.database.ContactNumbers

@Dao
interface ContactNumbersDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertContactNumbers(vararg contactNumbers: ContactNumbers)

    @Query("UPDATE contact_numbers SET number = :number WHERE CID = :CID AND number = :oldNumber")
    suspend fun updateContactNumbers(CID: String, oldNumber: String, number: String)

    @Query("SELECT * FROM contact_numbers WHERE CID = :CID AND number = :number")
    suspend fun getContactNumbersRow(CID: String, number: String): ContactNumbers

    @Query("SELECT * FROM contact_numbers WHERE CID = :CID")
    suspend fun getContactNumbers_CID(CID: String): List<ContactNumbers>

    @Query("SELECT * FROM contact_numbers")
    suspend fun getAllContactNumbers(): List<ContactNumbers>

    @Query("DELETE FROM contact_numbers WHERE CID = :CID AND number = :number")
    suspend fun deleteContactNumbers_PK(CID: String, number: String)

    @Query("DELETE FROM contact_numbers WHERE number = :number")
    suspend fun deleteContactNumbers_Number(number: String)

    @Query("DELETE FROM contact_numbers")
    suspend fun deleteAllContactNumbers()
}