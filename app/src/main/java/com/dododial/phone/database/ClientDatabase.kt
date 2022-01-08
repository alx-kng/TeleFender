package com.dododial.phone.database

import android.annotation.SuppressLint
import android.content.ContentResolver
import com.dododial.phone.database.client_daos.ChangeAgentDao
import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.database.Cursor
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_INSERT
import com.dododial.phone.database.ClientDBConstants.CHANGELOG_TYPE_CONTACT_NUMBER_INSERT
import com.dododial.phone.database.android_db.ContactDetailsHelper
import com.dododial.phone.database.client_daos.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.*

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
        val context: Context,
        val contentResolver: ContentResolver
    ) : RoomDatabase.Callback() {
        
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            Log.i("DODODEBUG: ", "INSIDE DATABASE CALLBACK")
            INSTANCE?.let { database ->
                scope.launch {
                    while (context.getSystemService(TelecomManager::class.java).defaultDialerPackage != context.packageName) {
                        delay(500)
                        Log.i("DODODEBUG: ", "INSIDE COROUTINE")
                    }
                    initContact(context, database, contentResolver)

                    // Goes through each call log and inserts to db
                    //TableInitializers.initCallLog(context, database)
                    
                    // Inserts the single user instance with changeAgentDao
                    //TableInitializers.initInstance(context, database)

                    // Goes through contacts and inserts contacts (and corresponding numbers) into db
                    //TableInitializers.initContact(context, database)
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        @SuppressLint("LogNotTimber")
        suspend fun initContact(context: Context, database: ClientDatabase, contentResolver: ContentResolver) {

//        For each contact and contact number on device, create ChangeLog for cInsert
//        Pseudo code:

//        for (contact in Contacts) {
//            changeFromClient( with contact)
//            initContactNumbers(context, database, contact)
//        }

            Log.i("DODODEBUG TRACE: ", "DO YOU SEE ME?")
            val curs: Cursor? = ContactDetailsHelper.getContactCursor2(contentResolver)

            if (curs == null) {
                Log.i("CURS NULL", "bad")
            } else {
                while (!curs.isAfterLast) {
                    cursContactInsert(curs, context, database)

                    Log.i(
                        "DODODEBUG: Contact Test",
                        "CID: " + curs.getString(0)
                            + " Name: " + curs.getString(1)
                            + " Number: " + curs.getString(2)
                    )

                    curs.moveToNext()
                }
            }

            Log.d("DODODEBUG TRACE", "hereuieruieauhaeruahrphahauhuappuaruparuareu")
            val changeLogs = database.changeLogDao().getAllChangeLogs()
            Log.d("DODODEBUG changeLogs", changeLogs.size.toString())
            for (changeLog : ChangeLog in changeLogs) {
                Log.d("DODODEBUG: ChangeLog", changeLog.toString())

            }

        }

        @SuppressLint("MissingPermission")
        @RequiresApi(Build.VERSION_CODES.O)
        suspend fun cursContactInsert(cursor: Cursor, context : Context, database: ClientDatabase) {
            val tMgr = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager

            val cChangeID = UUID.randomUUID().toString()
            val cnChangeID = UUID.randomUUID().toString()
            val changeTime = Instant.now().toEpochMilli().toString()
            val CID = UUID.nameUUIDFromBytes(cursor.getString(0).toByteArray()).toString()
            val name = cursor.getString(1)
            val number = cursor.getString(2)
            val parentNumber = tMgr.line1Number

            // To insert into Contacts table
            database.changeAgentDao().changeFromClient(
                cChangeID,
                null, // parentNumber is instanceNumber when contacts come from user's phone
                changeTime,
                CHANGELOG_TYPE_CONTACT_INSERT,
                CID,
                name,
                null,
                null,
                parentNumber,
                null,
                null
            )

            // To insert into ContactNumbers table
            database.changeAgentDao().changeFromClient(
                cnChangeID,
                parentNumber,
                changeTime,
                CHANGELOG_TYPE_CONTACT_NUMBER_INSERT,
                CID,
                name,
                null,
                number,
                null,
                null,
                null
            )
        }
    }

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: ClientDatabase? = null

        fun getDatabase(context: Context,
                        scope: CoroutineScope,
                        contentResolver: ContentResolver
        ): ClientDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClientDatabase::class.java,
                    "client_database"
                ).addCallback(ClientDatabaseCallback(scope, context, contentResolver)).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }

}