package com.dododial.phone.database

import android.annotation.SuppressLint
import android.util.Log
import com.dododial.phone.database.background_tasks.server_related.ServerHelpers
import com.dododial.phone.database.entities.CallLog
import com.dododial.phone.database.entities.ContactNumbers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object DatabaseLogFunctions {

    @SuppressLint("LogNotTimber")
    fun logContacts(database : ClientDatabase) {
        runBlocking { launch {
            val contacts = database.contactDao().getAllContacts()
            Log.i("DODODEBUG: CONTACT SIZE ARRAY.SIZE: ", contacts.size.toString())
            
            for (contact in contacts) {
                Log.i("DODODEBUG: ", contact.toString())
            }
        }}
    }

    @SuppressLint("LogNotTimber")
    fun logContactNumbers(database: ClientDatabase) {
        runBlocking { launch {
            val contactNumbers : List<ContactNumbers> = database.contactNumbersDao().getAllContactNumbers()
            Log.i("DODODEBUG: CONTACT NUMBERS SIZE: ", contactNumbers.size.toString())

            for (contactNumber in contactNumbers) {
                Log.i("DODODEBUG: ", contactNumber.toString())
            }
        }}
    }

    @SuppressLint("LogNotTimber")
    fun logChangeLogs(database : ClientDatabase) {
        runBlocking { launch {
            val changeLogs = database.changeLogDao().getAllChangeLogs()
            Log.i("DODODEBUG: CHANGE LOG SIZE: ", changeLogs.size.toString())

            for (changeLog in changeLogs) {
                Log.i("DODODEBUG: ", changeLog.toString())
                //Log.i("DODODEBUG: CHANGE LOG JSON", ServerHelpers.changeLogToJson(changeLog))
            }
        }}
    }

    @SuppressLint("LogNotTimber")
    fun logExecuteLogs(database : ClientDatabase) {
        runBlocking { launch {
            val executeLogs = database.queueToExecuteDao().getAllQTEs()
            Log.i("DODODEBUG: EXECUTE LOG SIZE: ", executeLogs.size.toString())

            for (executeLog in executeLogs) {
                Log.i("DODODEBUG: ", executeLog.toString())
            }
        }}
    }

    @SuppressLint("LogNotTimber")
    fun logUploadLogs(database : ClientDatabase) {
        runBlocking { launch {
            val uploadLogs = database.queueToUploadDao().getAllQTU()
            Log.i("DODODEBUG: UPLOAD LOG SIZE: ", uploadLogs.size.toString())

            for (uploadLog in uploadLogs) {
                Log.i("DODODEBUG: ", uploadLog.toString())
            }
        }}
    }

    @SuppressLint("LogNotTimber")
    fun logCallLogs(database : ClientDatabase) {
        runBlocking { launch {
            val callLogs = database.callLogDao().getCallLogs()
            Log.i("DODODEBUG: CALL LOG SIZE", callLogs.size.toString())

            for (callLog: CallLog in callLogs) {
                Log.i("DODODEBUG: CALL LOG: ", callLog.toString())
            }
        }}
    }
}