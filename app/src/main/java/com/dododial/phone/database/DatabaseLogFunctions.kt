package com.dododial.phone.database

import android.annotation.SuppressLint
import android.util.Log
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
            Timber.e("DODODEBUG: CONTACT SIZE ARRAY.SIZE: %s", contacts.size.toString())
            
            for (contact in contacts) {
                Timber.e("DODODEBUG: %s", contact.toString())
            }
        }}
    }

    fun logContactNumbers(database: ClientDatabase?, repository : ClientRepository?) {
        runBlocking { launch {
            val contactNumbers = (database?.contactNumbersDao()?.getAllContactNumbers() ?: repository?.getAllContactNumbers()) ?: listOf()
            Timber.e("DODODEBUG: CONTACT NUMBERS SIZE: %s", contactNumbers.size.toString())
            for (contactNumber in contactNumbers) {
                Timber.e("DODODEBUG: %s", contactNumber.toString())
            }
        }}
    }

    fun logChangeLogs(database : ClientDatabase?, repository: ClientRepository?) {
        runBlocking { launch {
            val changeLogs = (database?.changeLogDao()?.getAllChangeLogs() ?: repository?.getAllChangeLogs()) ?: listOf()
            Timber.e("DODODEBUG: CHANGE LOG SIZE: %s", changeLogs.size.toString())

            for (changeLog in changeLogs) {
                Timber.e("DODODEBUG: %s", changeLog.toString())
                //Log.i("DODODEBUG: CHANGE LOG JSON", ServerHelpers.changeLogToJson(changeLog))
            }
        }}
    }

    fun logExecuteLogs(database : ClientDatabase?, repository: ClientRepository?) {
        runBlocking { launch {
            val executeLogs = (database?.queueToExecuteDao()?.getAllQTEs() ?:repository?.getAllQTE()) ?: listOf()
            Timber.e("DODODEBUG: EXECUTE LOG SIZE: %s", executeLogs.size.toString())

            for (executeLog in executeLogs) {
                Timber.e("DODODEBUG: %s", executeLog.toString())
            }
        }}
    }

    fun logInstanceLogs(database : ClientDatabase?, repository: ClientRepository?) {
        runBlocking { launch {
            val instanceLogs = (database?.instanceDao()?.getAllInstance()) ?: listOf()
            Timber.e("DODODEBUG: INSTANCE LOG SIZE: %s", instanceLogs.size.toString())

            for (instanceLog in instanceLogs) {
                Timber.e("DODODEBUG: %s", instanceLog.toString())
            }
        }}
    }

    fun logUploadLogs(database : ClientDatabase?, repository: ClientRepository?) {
        runBlocking { launch {
            val uploadLogs = (database?.queueToUploadDao()?.getAllQTU() ?: repository?.getAllQTU()) ?: listOf()
            Timber.e("DODODEBUG: UPLOAD LOG SIZE: %s", uploadLogs.size.toString())

            for (uploadLog in uploadLogs) {
                Timber.e("DODODEBUG: %s", uploadLog.toString())
            }
        }}
    }

    fun logCallLogs(database : ClientDatabase?, repository: ClientRepository?) {
        runBlocking { launch {
            val callLogs = (database?.callLogDao()?.getCallLogs() ?: repository?.getCallLogs()) ?: listOf()
            Timber.e("DODODEBUG: CALL LOG SIZE: %s", callLogs.size.toString())

            for (callLog: CallLog in callLogs) {
                Timber.e("DODODEBUG: CALL LOG: %s", callLog.toString())
            }
        }}
    }
}