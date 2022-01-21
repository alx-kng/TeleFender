package com.dododial.phone.database

import android.annotation.SuppressLint
import com.dododial.phone.database.background_tasks.server_related.ServerHelpers
import com.dododial.phone.database.entities.CallLog
import com.dododial.phone.database.entities.ContactNumbers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

object DatabaseLogFunctions {

    fun logContacts(database : ClientDatabase?, repository : ClientRepository?) {
        runBlocking { launch {
            val contacts = (database?.contactDao()?.getAllContacts() ?: repository?.getAllContacts()) ?: listOf()
            Timber.i("DODODEBUG: CONTACT SIZE ARRAY.SIZE: %s", contacts.size.toString())
            
            for (contact in contacts) {
                Timber.i("DODODEBUG: %s", contact.toString())
            }
        }}
    }

    fun logContactNumbers(database: ClientDatabase?, repository : ClientRepository?) {
        runBlocking { launch {
            val contactNumbers = (database?.contactNumbersDao()?.getAllContactNumbers() ?: repository?.getAllContactNumbers()) ?: listOf()
            Timber.i("DODODEBUG: CONTACT NUMBERS SIZE: %s", contactNumbers.size.toString())
            for (contactNumber in contactNumbers) {
                Timber.i("DODODEBUG: %s", contactNumber.toString())
            }
        }}
    }

    fun logChangeLogs(database : ClientDatabase?, repository: ClientRepository?) {
        runBlocking { launch {
            val changeLogs = (database?.changeLogDao()?.getAllChangeLogs() ?: repository?.getAllChangeLogs()) ?: listOf()
            Timber.i("DODODEBUG: CHANGE LOG SIZE: %s", changeLogs.size.toString())

            for (changeLog in changeLogs) {
                Timber.i("DODODEBUG: %s", changeLog.toString())
                //Log.i("DODODEBUG: CHANGE LOG JSON", ServerHelpers.changeLogToJson(changeLog))
            }
        }}
    }

    fun logExecuteLogs(database : ClientDatabase?, repository: ClientRepository?) {
        runBlocking { launch {
            val executeLogs = (database?.queueToExecuteDao()?.getAllQTEs() ?:repository?.getAllQTE()) ?: listOf()
            Timber.i("DODODEBUG: EXECUTE LOG SIZE: %s", executeLogs.size.toString())

            for (executeLog in executeLogs) {
                Timber.i("DODODEBUG: %s", executeLog.toString())
            }
        }}
    }

    fun logUploadLogs(database : ClientDatabase?, repository: ClientRepository?) {
        runBlocking { launch {
            val uploadLogs = (database?.queueToUploadDao()?.getAllQTU() ?: repository?.getAllQTU()) ?: listOf()
            Timber.i("DODODEBUG: UPLOAD LOG SIZE: %s", uploadLogs.size.toString())

            for (uploadLog in uploadLogs) {
                Timber.i("DODODEBUG: %s", uploadLog.toString())
            }
        }}
    }

    fun logCallLogs(database : ClientDatabase?, repository: ClientRepository?) {
        runBlocking { launch {
            val callLogs = (database?.callLogDao()?.getCallLogs() ?: repository?.getCallLogs()) ?: listOf()
            Timber.i("DODODEBUG: CALL LOG SIZE: %s", callLogs.size.toString())

            for (callLog: CallLog in callLogs) {
                Timber.i("DODODEBUG: CALL LOG: %s", callLog.toString())
            }
        }}
    }
}