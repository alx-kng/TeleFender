package com.dododial.phone.database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dododial.phone.database.entities.Contact

@Dao
interface ContactDao {
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContact(vararg contact: Contact)

    @Query("UPDATE contact SET name = :name WHERE CID = :CID")
    suspend fun updateContactName(CID: String, name: String)

    @Query("SELECT ParentNumber FROM contact WHERE CID = :CID")
    suspend fun getParentNumber(CID: String): String
    
    @Query("SELECT * FROM contact WHERE parentNumber = :parentNumber")
    suspend fun getContacts_ParentNumber(parentNumber : String) : List<Contact>
    
    @Query("SELECT * FROM contact WHERE CID = :CID")
    suspend fun getContactRow(CID: String): Contact

    @Query("SELECT * FROM contact")
    suspend fun getAllContacts(): List<Contact>

    @Query("DELETE FROM contact WHERE CID = :CID")
    suspend fun deleteContact_CID(CID: String)

    @Query("DELETE FROM contact WHERE name = :name")
    suspend fun deleteContact_Name(name: String)

    @Query("DELETE FROM contact")
    suspend fun deleteAllContacts()
    
}