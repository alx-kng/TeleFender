package com.dododial.phone.database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dododial.phone.database.entities.ChangeLog
import com.dododial.phone.database.entities.ContactNumbers

@Dao
interface ContactNumbersDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContactNumbers(vararg contactNumbers: ContactNumbers)

    @Query("""
        UPDATE contact_numbers SET number = :number,
            versionNumber =
                CASE
                    WHEN :versionNumber IS NOT NULL
                        THEN :versionNumber
                    ELSE versionNumber
                END
            WHERE CID = :CID AND number = :oldNumber
        """)
    suspend fun updateContactNumbers(CID: String, oldNumber: String, number: String, versionNumber: Int?)

    @Query("SELECT * FROM contact_numbers WHERE CID = :CID AND number = :number")
    suspend fun getContactNumbersRow(CID: String, number: String): ContactNumbers?

    @Query("SELECT * FROM contact_numbers WHERE CID = :CID")
    suspend fun getContactNumbers_CID(CID: String): List<ContactNumbers>

    @Query("SELECT * FROM contact_numbers WHERE CID IN (SELECT CID FROM contact WHERE parentNumber = :parentNumber)")
    suspend fun getContactNumbers_ParentNumber(parentNumber: String): List<ContactNumbers>

    @Query("SELECT * FROM contact_numbers")
    suspend fun getAllContactNumbers(): List<ContactNumbers>

    @Query("SELECT COUNT(CID) FROM contact_numbers")
    suspend fun getContactNumberSize() : Int?

    @Query("DELETE FROM contact_numbers WHERE CID = :CID AND number = :number")
    suspend fun deleteContactNumbers_PK(CID: String, number: String)

    @Query("DELETE FROM contact_numbers WHERE number = :number")
    suspend fun deleteContactNumbers_Number(number: String)

    @Query("DELETE FROM contact_numbers")
    suspend fun deleteAllContactNumbers()


}