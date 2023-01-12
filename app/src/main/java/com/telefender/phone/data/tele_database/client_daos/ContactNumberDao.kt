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

    /**
     * We clean oldNumber so that we can update with PK (CID and normalizedNumber).
     */
    suspend fun updateContactNumbers(CID: String, normalizedNumber: String, rawNumber: String, versionNumber: Int?) {
        updateContactNumbersHelper(CID, normalizedNumber, rawNumber, versionNumber)
    }

    @Query(
        """
        UPDATE contact_number SET rawNumber = :rawNumber,
            versionNumber =
                CASE
                    WHEN :versionNumber IS NOT NULL
                        THEN :versionNumber
                    ELSE versionNumber
                END
            WHERE CID = :CID AND normalizedNumber = :normalizedNumber
        """
    )
    suspend fun updateContactNumbersHelper(CID: String, normalizedNumber: String, rawNumber: String, versionNumber: Int?)

    @Query("SELECT * FROM contact_number WHERE CID = :CID AND normalizedNumber = :normalizedNumber")
    suspend fun getContactNumbersRow(CID: String, normalizedNumber: String): ContactNumber?

    @Query("SELECT * FROM contact_number WHERE CID = :CID")
    suspend fun getContactNumbersByCID(CID: String): List<ContactNumber>

    @Query("SELECT * FROM contact_number WHERE instanceNumber = :instanceNumber")
    suspend fun getContactNumbersByIns(instanceNumber: String): List<ContactNumber>

    @Query("SELECT * FROM contact_number")
    suspend fun getAllContactNumbers(): List<ContactNumber>

    @Query("SELECT COUNT(CID) FROM contact_number")
    suspend fun getContactNumberSize() : Int?

    @Query("DELETE FROM contact_number WHERE CID = :CID AND normalizedNumber = :normalizedNumber")
    suspend fun deleteContactNumbers(CID: String, normalizedNumber: String)

    @Query("DELETE FROM contact_number WHERE normalizedNumber = :normalizedNumber")
    suspend fun deleteContactNumbers(normalizedNumber: String)

    @Query("DELETE FROM contact_number")
    suspend fun deleteAllContactNumbers()


}