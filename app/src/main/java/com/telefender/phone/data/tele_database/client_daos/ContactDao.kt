package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.Contact

@Dao
interface ContactDao {
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContact(vararg contact: Contact)

    @Query("UPDATE contact SET blocked = :blocked WHERE CID = :CID")
    suspend fun updateContactBlocked(CID: String, blocked: Boolean)

    @Query("SELECT instanceNumber FROM contact WHERE CID = :CID")
    suspend fun getInstanceNumber(CID: String): String?

    @Query("SELECT blocked FROM contact WHERE CID = :CID")
    suspend fun contactBlocked(CID: String): Boolean?

    @Query("SELECT * FROM contact WHERE instanceNumber = :instanceNumber")
    suspend fun getContactsByInstance(instanceNumber : String) : List<Contact>
    
    @Query("SELECT * FROM contact WHERE CID = :CID")
    suspend fun getContactRow(CID: String): Contact?

    @Query("SELECT * FROM contact")
    suspend fun getAllContacts(): List<Contact>

    @Query("SELECT COUNT(CID) FROM contact")
    suspend fun getContactSize() : Int?

    @Query("DELETE FROM contact WHERE CID = :CID")
    suspend fun deleteContact_CID(CID: String)

    @Query("DELETE FROM contact")
    suspend fun deleteAllContacts()

}