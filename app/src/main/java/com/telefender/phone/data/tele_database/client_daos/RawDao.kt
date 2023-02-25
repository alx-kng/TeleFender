package com.telefender.phone.data.tele_database.client_daos

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.telefender.phone.data.tele_database.entities.*

@Dao
interface RawDao {

    /**********************************************************************************************
     * DANGEROUS. Used to query database given any string query, although query MUST BE READ and
     * CANNOT BE WRITE. All queries are wrapped in Transactions to prevent data corruption on
     * large returns.
     **********************************************************************************************/

    @Transaction
    @RawQuery
    suspend fun readDataChangeLog(query: SupportSQLiteQuery) : List<ChangeLog>

    @Transaction
    @RawQuery
    suspend fun readDataExecuteQueue(query: SupportSQLiteQuery) : List<ExecuteQueue>

    @Transaction
    @RawQuery
    suspend fun readDataUploadChangedQueue(query: SupportSQLiteQuery) : List<UploadChangeQueue>

    @Transaction
    @RawQuery
    suspend fun readDataUploadAnalyzedQueue(query: SupportSQLiteQuery) : List<UploadAnalyzedQueue>

    @Transaction
    @RawQuery
    suspend fun readDataErrorQueue(query: SupportSQLiteQuery) : List<ErrorQueue>

    @Transaction
    @RawQuery
    suspend fun readDataStoredMap(query: SupportSQLiteQuery) : List<StoredMap>

    @Transaction
    @RawQuery
    suspend fun readDataParameters(query: SupportSQLiteQuery) : List<Parameters>

    @Transaction
    @RawQuery
    suspend fun readDataCallDetail(query: SupportSQLiteQuery) : List<CallDetail>

    @Transaction
    @RawQuery
    suspend fun readDataInstance(query: SupportSQLiteQuery) : List<Instance>

    @Transaction
    @RawQuery
    suspend fun readDataContact(query: SupportSQLiteQuery) : List<Contact>

    @Transaction
    @RawQuery
    suspend fun readDataContactNumber(query: SupportSQLiteQuery) : List<ContactNumber>

    @Transaction
    @RawQuery
    suspend fun readDataAnalyzedNumber(query: SupportSQLiteQuery) : List<AnalyzedNumber>
}