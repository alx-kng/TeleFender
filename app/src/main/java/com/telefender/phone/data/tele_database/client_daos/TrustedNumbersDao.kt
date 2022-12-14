package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.telefender.phone.data.tele_database.entities.TrustedNumbers

@Dao
interface TrustedNumbersDao {
    
    @Insert
    suspend fun insertTrustedNumbersHelper(trustedNumber: TrustedNumbers)

    @Query("UPDATE trusted_numbers SET number = :newNum WHERE number = :oldNum")
    suspend fun updateTrustedNumbersHelper(oldNum: String, newNum : String)

    @Query("UPDATE trusted_numbers SET counter = counter + :counterDelta WHERE number = :number")
    suspend fun updateCounterTrustedNumbers(number: String, counterDelta: Int)

    @Query("SELECT COUNT(number) from trusted_numbers WHERE number = :number")
    suspend fun getTrustedNumbersCount(number: String): Int

    @Query("SELECT counter FROM trusted_numbers where number = :number")
    suspend fun getCounterTrustedNumbers(number: String): Int

    @Query("SELECT * FROM trusted_numbers")
    suspend fun getAllTrustedNumbers() : List<TrustedNumbers>

    @Query("SELECT * FROM trusted_numbers WHERE number = :number")
    suspend fun getTrustedNumbersRow(number : String) : TrustedNumbers

    @Query("DELETE FROM trusted_numbers WHERE number = :number")
    suspend fun deleteTrustedNumbers_Number(number: String)

    @Query("DELETE FROM trusted_numbers")
    suspend fun deleteAllTrustedNumbers()

    suspend fun insertTrustedNumbers(num: String) {
        if (getTrustedNumbersCount(num) != 0) {
            updateCounterTrustedNumbers(num, 1)
        }
        else {
            val trustedNumber = TrustedNumbers(num)
            insertTrustedNumbersHelper(trustedNumber)
        }
    }

    suspend fun updateTrustedNumbers(oldNum: String, newNum: String) {
        // if counter == 1,update number
        // else, counter -= 1 and insert new number newNum
        if (getCounterTrustedNumbers(oldNum) == 1) {
            updateTrustedNumbersHelper(oldNum, newNum)
        }
        else {
            updateCounterTrustedNumbers(oldNum, -1)
            insertTrustedNumbers(newNum)
        }
    }

    suspend fun deleteTrustedNumbers(num : String) {
        if (getCounterTrustedNumbers(num) == 1) {
            deleteTrustedNumbers_Number(num)
        }
        else {
            updateCounterTrustedNumbers(num, -1)
        }
    }
}