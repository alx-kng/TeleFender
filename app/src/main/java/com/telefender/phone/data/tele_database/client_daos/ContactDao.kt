package com.telefender.phone.data.tele_database.client_daos

import androidx.room.*
import com.telefender.phone.data.tele_database.entities.Contact

@Dao
interface ContactDao {

    /**
     * Inserts Contact and returns inserted rowID. Probably returns -1 if insert failure.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContact(contact: Contact) : Long

    /**
     * Returns a nullable Int that indicates whether the update was successful. If 1 is returned,
     * then the update was successful, otherwise the update failed.
     */
    @Query("UPDATE contact SET blocked = :blocked WHERE CID = :CID")
    suspend fun updateContactBlocked(CID: String, blocked: Boolean) : Int?

    @Query("SELECT instanceNumber FROM contact WHERE CID = :CID")
    suspend fun getInstanceNumber(CID: String): String?

    @Query("SELECT blocked FROM contact WHERE CID = :CID")
    suspend fun contactBlocked(CID: String): Boolean?

    // Transaction to prevent data corruption on large returns.
    @Transaction
    @Query("SELECT * FROM contact WHERE instanceNumber = :instanceNumber")
    suspend fun getContactsByInstance(instanceNumber : String) : List<Contact>
    
    @Query("SELECT * FROM contact WHERE CID = :CID")
    suspend fun getContactRow(CID: String): Contact?

    // Transaction to prevent data corruption on large returns.
    @Transaction
    @Query("SELECT * FROM contact")
    suspend fun getAllContacts(): List<Contact>

    @Query("SELECT COUNT(CID) FROM contact")
    suspend fun getContactSize() : Int?

    /**
     * Returns a nullable Int that indicates whether the delete was successful. If 1 is returned,
     * then the delete was successful, otherwise the delete failed.
     */
    @Query("DELETE FROM contact WHERE CID = :CID")
    suspend fun deleteContact(CID: String) : Int?

    /**
     * Returns a nullable Int that indicates whether the delete was successful (number of rows
     * delete). If a value >0 is returned, then the delete was at least partially successful,
     * otherwise the delete completely failed (if there were existing rows).
     */
    @Query("DELETE FROM contact")
    suspend fun deleteAllContacts() : Int?

}