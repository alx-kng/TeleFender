package com.dododial.phone.database

import android.annotation.SuppressLint
import android.util.Log
import com.dododial.phone.database.entities.CallLog
import com.dododial.phone.database.entities.ContactNumbers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object DatabaseLogFunctions {

    @SuppressLint("LogNotTimber")
    fun logContacts(database : ClientDatabase) {
        runBlocking { launch {
            val contacts = database.contactDao().getAllContacts()
            for (contact in contacts) {
                Log.i("DODODEBUG: ", contact.toString())
            }
            Log.i("DODODEBUG: CONTACT SIZE ARRAY.SIZE: ", contacts.size.toString())
        }}
    }

    @SuppressLint("LogNotTimber")
    fun logContactNumbers(database: ClientDatabase) {
        runBlocking { launch {
            val contactNumbers : List<ContactNumbers> = database.contactNumbersDao().getAllContactNumbers()
            for (contactNumber in contactNumbers) {
                Log.i("DODODEBUG: ", contactNumber.toString())
            }
            Log.i("DODODEBUG: CONTACT NUMBERS .SIZE size: ", contactNumbers.size.toString())
        }}
    }

    @SuppressLint("LogNotTimber")
    fun logChangeLogs(database : ClientDatabase) {
        runBlocking { launch {
            val changeLogs = database.changeLogDao().getAllChangeLogs()
            for (changeLog in changeLogs) {
                Log.i("DODODEBUG: ", changeLog.toString())
            }
            Log.i("DODODEBUG: CHANGE LOG SIZE ARRAY.SIZE: ", changeLogs.size.toString())
        }}
    }

    @SuppressLint("LogNotTimber")
    fun logExecuteLogs(database : ClientDatabase) {
        runBlocking { launch {
            val executeLogs = database.queueToExecuteDao().getAllQTEs()
            for (executeLog in executeLogs) {
                Log.i("DODODEBUG: ", executeLog.toString())
            }
            Log.i("DODODEBUG: EXECUTE LOG SIZE: ", executeLogs.size.toString())
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