package com.telefender.phone.data.tele_database.converters

import androidx.room.TypeConverter
import com.telefender.phone.data.tele_database.entities.Parameters
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * TODO: CONVERT FROM MOSHI TO KOTLINX SERIALIZATION?
 *
 * TODO: USE CONVERTERS INSTEAD OF MANUAL CONVERSION FOR AnalyzedNumbers / Parameters / ChangeLog?
 *  --> maybe later or for next table. Stuff works decently and is clean right now. No need to
 *  change yet.
 */
class Converters {

    @TypeConverter
    fun fromList(value : List<Long>) = Json.encodeToString(value)

    @TypeConverter
    fun toList(value: String) = Json.decodeFromString<List<Long>>(value)

}