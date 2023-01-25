package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.entities.ContactNumber

@Dao
interface ContactNumberDao {

    /**
     * Inserts ContactNumber and returns inserted rowID. Probably returns -1 if insert failure.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContactNumbers(contactNumber: ContactNumber) : Long

    /**
     * We clean old rawNumber so that we can update with PK (CID and normalizedNumber).
     *
     * Returns a nullable Int that indicates whether the update was successful. If 1 is returned,
     * then the update was successful, otherwise the update failed.
     */
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
    suspend fun updateContactNumber(
        CID: String,
        normalizedNumber: String,
        rawNumber: String,
        versionNumber: Int?
    ) : Int?

    @Query("SELECT * FROM contact_number WHERE CID = :CID AND normalizedNumber = :normalizedNumber")
    suspend fun getContactNumberRow(CID: String, normalizedNumber: String): ContactNumber?

    @Query("SELECT * FROM contact_number WHERE CID = :CID")
    suspend fun getContactNumbersByCID(CID: String): List<ContactNumber>

    // Transaction to prevent data corruption on large returns.
    @Transaction
    @Query("SELECT * FROM contact_number WHERE instanceNumber = :instanceNumber")
    suspend fun getContactNumbersByIns(instanceNumber: String): List<ContactNumber>

    // Transaction to prevent data corruption on large returns.
    @Transaction
    @Query("SELECT * FROM contact_number")
    suspend fun getAllContactNumbers(): List<ContactNumber>

    @Query("SELECT COUNT(CID) FROM contact_number")
    suspend fun getContactNumberSize() : Int?

    /**
     * Returns a nullable Int that indicates whether the delete was successful. If 1 is returned,
     * then the delete was successful, otherwise the delete failed.
     */
    @Query("DELETE FROM contact_number WHERE CID = :CID AND normalizedNumber = :normalizedNumber")
    suspend fun deleteContactNumber(CID: String, normalizedNumber: String) : Int?

    /**
     * Returns a nullable Int that indicates whether the delete was successful (number of rows
     * delete). If a value >0 is returned, then the delete was at least partially successful,
     * otherwise the delete completely failed (if there were existing rows).
     */
    @Query("DELETE FROM contact_number WHERE normalizedNumber = :normalizedNumber")
    suspend fun deleteContactNumber(normalizedNumber: String) : Int?

    @Query("DELETE FROM contact_number")
    suspend fun deleteAllContactNumbers()


}