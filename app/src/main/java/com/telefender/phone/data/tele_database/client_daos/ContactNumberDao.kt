package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.ContactNumber

@Dao
interface ContactNumberDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContactNumbers(vararg contactNumbers: ContactNumber)

    @Query(
        """
        UPDATE contact_number SET cleanNumber = :number,
            versionNumber =
                CASE
                    WHEN :versionNumber IS NOT NULL
                        THEN :versionNumber
                    ELSE versionNumber
                END
            WHERE CID = :CID AND cleanNumber = :oldNumber
        """
    )
    suspend fun updateContactNumbers(CID: String, oldNumber: String, number: String, versionNumber: Int?)

    @Query("SELECT * FROM contact_number WHERE CID = :CID AND cleanNumber = :number")
    suspend fun getContactNumbersRow(CID: String, number: String): ContactNumber?

    @Query("SELECT * FROM contact_number WHERE CID = :CID")
    suspend fun getContactNumbers_CID(CID: String): List<ContactNumber>

    @Query("SELECT * FROM contact_number WHERE CID IN (SELECT CID FROM contact WHERE instanceNumber = :parentNumber)")
    suspend fun getContactNumbers_ParentNumber(parentNumber: String): List<ContactNumber>

    @Query("SELECT * FROM contact_number")
    suspend fun getAllContactNumbers(): List<ContactNumber>

    @Query("SELECT COUNT(CID) FROM contact_number")
    suspend fun getContactNumberSize() : Int?

    @Query("DELETE FROM contact_number WHERE CID = :CID AND cleanNumber = :number")
    suspend fun deleteContactNumbers_PK(CID: String, number: String)

    @Query("DELETE FROM contact_number WHERE cleanNumber = :number")
    suspend fun deleteContactNumbers_Number(number: String)

    @Query("DELETE FROM contact_number")
    suspend fun deleteAllContactNumbers()


}