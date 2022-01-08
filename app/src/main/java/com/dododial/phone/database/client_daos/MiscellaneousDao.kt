package com.dododial.phone.database.client_daos

import androidx.room.Dao
import androidx.room.Query
import com.dododial.phone.database.Miscellaneous

@Dao
interface MiscellaneousDao {


    @Query("INSERT INTO miscellaneous (number) VALUES (:number)")
    suspend fun insertMiscHelper(number: String)

    @Query("UPDATE miscellaneous SET trustability = :trustability WHERE number = :number")
    suspend fun updateMiscTrust(number: String, trustability: Int)

    @Query("UPDATE miscellaneous SET counter = counter + :counterDelta WHERE number = :number")
    suspend fun updateCounterMisc(number: String, counterDelta: Int)

    @Query("SELECT * FROM miscellaneous")
    suspend fun getAllMiscellaenous() : List<Miscellaneous>

    @Query("SELECT * FROM miscellaneous WHERE number = :number")
    suspend fun getMiscellaenousRow(number : String) : Miscellaneous

    @Query("SELECT COUNT(number) from miscellaneous WHERE number = :number")
    suspend fun getMiscCount(number: String): Int

    @Query("SELECT trustability FROM miscellaneous where number = :number")
    suspend fun getMiscTrust(number: String): Int

    @Query("SELECT counter FROM miscellaneous where number = :number")
    suspend fun getCounterMisc(number: String): Int

    @Query("DELETE FROM miscellaneous WHERE number = :number")
    suspend fun deleteMisc(number: String)

    @Query("DELETE FROM miscellaneous")
    suspend fun deleteAllMisc()
    
    suspend fun insertMisc(num: String) {
        if (getMiscCount(num) != 0) {
            updateCounterMisc(num, 1)
        }
        else {
            insertMiscHelper(num)
        }
    }
}