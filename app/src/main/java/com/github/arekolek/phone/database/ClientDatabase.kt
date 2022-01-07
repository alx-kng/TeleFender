package com.github.arekolek.phone.database

import com.github.arekolek.phone.database.client_daos.ChangeAgentDao
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.arekolek.phone.database.client_daos.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(entities = arrayOf(
    CallLog::class,
    KeyStorage::class,
    Instance::class,
    Contact::class,
    ContactNumbers::class,
    TrustedNumbers::class,
    Organizations::class,
    Miscellaneous::class,
    ChangeLog::class,
    QueueToExecute::class,
    QueueToUpload::class
), version = 1, exportSchema = false)
public abstract class ClientDatabase : RoomDatabase() {

    abstract fun callLogDao() : CallLogDao
    abstract fun changeAgentDao() : ChangeAgentDao
    abstract fun uploadAgentDao() : UploadAgentDao
    abstract fun changeLogDao() : ChangeLogDao
    abstract fun executeAgentDao() : ExecuteAgentDao
    abstract fun keyStorageDao() : KeyStorageDao
    abstract fun queueToExecuteDao() : QueueToExecuteDao
    abstract fun queueToUploadDao() : QueueToUploadDao
    
    private class ClientDatabaseCallback(
        private val scope: CoroutineScope,
        val context: Context
    ) : RoomDatabase.Callback() {

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch {
                    
                    // Goes through each call log and inserts to db
                    //TableInitializers.initCallLog(context, database)
                    
                    // Inserts the single user instance with changeAgentDao
                    //TableInitializers.initInstance(context, database)

                    // Goes through contacts and inserts contacts (and corresponding numbers) into db
                    //TableInitializers.initContact(context, database)
                }
            }
        }
    }

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: ClientDatabase? = null

        fun getDatabase(context: Context,
                        scope: CoroutineScope
        ): ClientDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClientDatabase::class.java,
                    "client_database"
                ).addCallback(ClientDatabaseCallback(scope, context)).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}