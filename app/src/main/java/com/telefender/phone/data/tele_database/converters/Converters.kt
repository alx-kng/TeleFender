package com.telefender.phone.data.tele_database.converters

import androidx.room.TypeConverter
import com.telefender.phone.call_related.HandleMode
import com.telefender.phone.data.tele_database.entities.ServerMode
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

    @kotlinx.serialization.ExperimentalSerializationApi
    @TypeConverter
    fun fromList(value : List<Long>) = Json.encodeToString(value)

    @kotlinx.serialization.ExperimentalSerializationApi
    @TypeConverter
    fun toList(value: String) = Json.decodeFromString<List<Long>>(value)

    @kotlinx.serialization.ExperimentalSerializationApi
    @TypeConverter
    fun fromHandleMode(value : HandleMode?) : String? = value?.let {
        Json.encodeToString(it)
    }

    @kotlinx.serialization.ExperimentalSerializationApi
    @TypeConverter
    fun toHandleMode(value: String?) : HandleMode? = value?.let {
        Json.decodeFromString<HandleMode>(it)
    }

    @kotlinx.serialization.ExperimentalSerializationApi
    @TypeConverter
    fun fromServerMode(value : ServerMode) = Json.encodeToString(value)

    @kotlinx.serialization.ExperimentalSerializationApi
    @TypeConverter
    fun toServerMode(value: String) = Json.decodeFromString<ServerMode>(value)
}