package com.github.arekolek.phone.database.client_daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.arekolek.phone.database.TrustedNumbers

@Dao
interface TrustedNumbersDao {
    
    @Query("INSERT INTO trusted_numbers (number) VALUES (:number)")
    suspend fun insertTrustedNumbersHelper(number: String)

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
            insertTrustedNumbersHelper(num)
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