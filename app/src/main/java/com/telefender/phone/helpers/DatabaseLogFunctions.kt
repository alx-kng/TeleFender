package com.telefender.phone.helpers

import com.telefender.phone.data.tele_database.ClientDatabase
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.entities.CallDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

object DatabaseLogFunctions {

    fun logContacts(database : ClientDatabase?, repository : ClientRepository?) {
        CoroutineScope(Dispatchers.Default).launch {
            val contacts = (database?.contactDao()?.getAllContacts() ?: repository?.getAllContacts()) ?: listOf()
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: CONTACT SIZE ARRAY.SIZE: %s", contacts.size.toString())
            
            for (contact in contacts) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: %s", contact.toString())
            }
        }
    }

    fun logContactNumbers(database: ClientDatabase?, repository : ClientRepository?) {
        CoroutineScope(Dispatchers.Default).launch {
            val contactNumbers = (database?.contactNumbersDao()?.getAllContactNumbers() ?: repository?.getAllContactNumbers()) ?: listOf()
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: CONTACT NUMBERS SIZE: %s", contactNumbers.size.toString())
            for (contactNumber in contactNumbers) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: %s", contactNumber.toString())
            }
        }
    }

    fun logChangeLogs(database : ClientDatabase?, repository: ClientRepository?) {
        CoroutineScope(Dispatchers.Default).launch {
            val changeLogs = (database?.changeLogDao()?.getAllChangeLogs() ?: repository?.getAllChangeLogs()) ?: listOf()
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: CHANGE LOG SIZE: %s", changeLogs.size.toString())

            for (changeLog in changeLogs) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: %s", changeLog.toString())
                //Log.i("${MiscHelpers.DEBUG_LOG_TAG}: CHANGE LOG JSON", ServerHelpers.changeLogToJson(changeLog))
            }
        }
    }

    fun logExecuteLogs(database : ClientDatabase?, repository: ClientRepository?) {
        CoroutineScope(Dispatchers.Default).launch {
            val executeLogs = (database?.queueToExecuteDao()?.getAllQTEs() ?: repository?.getAllQTE()) ?: listOf()
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: EXECUTE LOG SIZE: %s", executeLogs.size.toString())

            for (executeLog in executeLogs) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: %s", executeLog.toString())
            }
        }
    }

    fun logInstanceLogs(database : ClientDatabase?, repository: ClientRepository?) {
        CoroutineScope(Dispatchers.Default).launch {
            val instanceLogs = (database?.instanceDao()?.getAllInstance()) ?: listOf()
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: INSTANCE LOG SIZE: %s", instanceLogs.size.toString())

            for (instanceLog in instanceLogs) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: %s", instanceLog.toString())
            }
        }
    }

    fun logUploadLogs(database : ClientDatabase?, repository: ClientRepository?) {
        CoroutineScope(Dispatchers.Default).launch {
            val uploadLogs = (database?.queueToUploadDao()?.getAllQTU() ?: repository?.getAllQTU()) ?: listOf()
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: UPLOAD LOG SIZE: %s", uploadLogs.size.toString())

            for (uploadLog in uploadLogs) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: %s", uploadLog.toString())
            }
        }
    }

    fun logCallLogs(database : ClientDatabase?, repository: ClientRepository?, amount: Int?) {
        CoroutineScope(Dispatchers.Default).launch {

            val callLogs = if (amount == null) {
                (database?.callDetailDao()?.getCallDetails() ?: repository?.getCallDetails()) ?: listOf()
            } else {
                (database?.callDetailDao()?.getCallDetailsPartial(amount) ?: repository?.getCallDetailsPartial(amount)) ?: listOf()
            }

            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: # OF PRINTED CALL LOGS: %s", callLogs.size.toString())

            for (callDetail: CallDetail in callLogs) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: CALL LOG: %s", callDetail.toString())
            }
        }
    }

    /**
     * Temp log for all.
     */
    suspend fun logSelect(database : ClientDatabase?, repository: ClientRepository?, logWhich: List<Int>) {
        if (0 in logWhich) {
            logCallLogs(database, repository, 5)
            delay(300)
        }
        if (1 in logWhich) {
            logContacts(database, repository)
            delay(300)
        }
        if (2 in logWhich) {
            logContactNumbers(database, repository)
            delay(300)
        }
        if (3 in logWhich) {
            logChangeLogs(database, repository)
            delay(300)
        }
        if (4 in logWhich) {
            logExecuteLogs(database, repository)
            delay(300)
        }
        if (5 in logWhich) {
            logUploadLogs(database, repository)
        }
    }
}