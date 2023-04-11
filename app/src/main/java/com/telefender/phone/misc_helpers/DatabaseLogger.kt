package com.telefender.phone.misc_helpers

import com.telefender.phone.data.tele_database.ClientDatabase
import com.telefender.phone.data.tele_database.ClientRepository
import kotlinx.coroutines.*
import timber.log.Timber

enum class PrintTypes {
    INSTANCE, CONTACT, CONTACT_NUMBER, CHANGE_LOG, EXECUTE_LOG, UPLOAD_CHANGE, UPLOAD_ANALYZED,
    ANALYZED_NUMBER, CALL_LOG, ERROR_LOG
}

/**
 * TODO: Although Timber has it's own enable / disable control, we need to make sure that the
 *  logger functions are also not enabled during production. We should probably put loggerEnabled
 *  as a Parameter column.
 */
object DatabaseLogger {

    private val loggingScope = CoroutineScope(Dispatchers.IO)
    private var loggerEnabled = true

    /**
     * Database debugging logger. Returns Job just in case you would like other suspending code to
     * wait for logger to finish before continuing.
     */
    fun omegaLogger(
        database : ClientDatabase? = null,
        repository: ClientRepository? = null,
        logSelect: List<PrintTypes>,
        callLogAmount: Int? = null
    ) : Job? {
        if (!loggerEnabled) return null

        return loggingScope.launch {
            var callLogJob : Job? = null
            if (PrintTypes.CALL_LOG in logSelect) {
                callLogJob = logCallLogs(database, repository, callLogAmount ?: 5)
            }

            callLogJob?.join()

            var instanceJob : Job? = null
            if (PrintTypes.INSTANCE in logSelect) {
                instanceJob = logInstances(database, repository)
            }

            instanceJob?.join()

            var contactJob : Job? = null
            if (PrintTypes.CONTACT in logSelect) {
                contactJob = logContacts(database, repository)
            }

            contactJob?.join()

            var contactNumberJob : Job? = null
            if (PrintTypes.CONTACT_NUMBER in logSelect) {
                contactNumberJob = logContactNumbers(database, repository)
            }

            contactNumberJob?.join()

            var changeLogJob : Job? = null
            if (PrintTypes.CHANGE_LOG in logSelect) {
                changeLogJob = logChangeLogs(database, repository)
            }

            changeLogJob?.join()

            var executeLogJob : Job? = null
            if (PrintTypes.EXECUTE_LOG in logSelect) {
                executeLogJob = logExecuteLogs(database, repository)
            }

            executeLogJob?.join()

            var uploadChangeJob : Job? = null
            if (PrintTypes.UPLOAD_CHANGE in logSelect) {
                uploadChangeJob = logUploadChangeLogs(database, repository)
            }

            uploadChangeJob?.join()

            var uploadAnalyzedJob : Job? = null
            if (PrintTypes.UPLOAD_ANALYZED in logSelect) {
                uploadAnalyzedJob = logUploadAnalyzedLogs(database, repository)
            }

            uploadAnalyzedJob?.join()

            var errorLogJob : Job? = null
            if (PrintTypes.ERROR_LOG in logSelect) {
                errorLogJob = logErrorLogs(database, repository)
            }

            errorLogJob?.join()

            var analyzedNumberJob : Job? = null
            if (PrintTypes.ANALYZED_NUMBER in logSelect) {
                analyzedNumberJob = logAnalyzedNumbers(database, repository)
            }
        }
    }

    private fun logInstances(database : ClientDatabase?, repository: ClientRepository?) : Job {
        return loggingScope.launch {
            val instances = database?.instanceDao()?.getAllInstance()
                ?: repository?.getAllInstance()
                ?: listOf()

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: INSTANCE TABLE SIZE: ${instances.size}")

            for (instance in instances) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: $instance")
            }
        }
    }

    private fun logContacts(database : ClientDatabase?, repository : ClientRepository?) : Job {
        return loggingScope.launch {
            val contacts = database?.contactDao()?.getAllContacts()
                ?: repository?.getAllContacts()
                ?: listOf()

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: CONTACT TABLE SIZE: ${contacts.size}")
            
            for (contact in contacts) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: $contact")
            }
        }
    }

    private fun logContactNumbers(database: ClientDatabase?, repository : ClientRepository?) : Job {
        return loggingScope.launch {
            val contactNumbers = database?.contactNumberDao()?.getAllContactNumbers()
                ?: repository?.getAllContactNumbers()
                ?: listOf()

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: CONTACT NUMBERS SIZE: ${contactNumbers.size}")

            for (contactNumber in contactNumbers) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: $contactNumber")
            }
        }
    }

    private fun logChangeLogs(database : ClientDatabase?, repository: ClientRepository?) : Job {
        return loggingScope.launch {
            val changeLogs = database?.changeLogDao()?.getAllChangeLogs()
                ?: repository?.getAllChangeLogs()
                ?: listOf()

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: CHANGE LOG SIZE: ${changeLogs.size}")

            for (changeLog in changeLogs) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: $changeLog")
            }
        }
    }

    private fun logExecuteLogs(database : ClientDatabase?, repository: ClientRepository?) : Job {
        return loggingScope.launch {
            val executeLogs = database?.executeQueueDao()?.getAllQTE()
                ?: repository?.getAllQTE()
                ?: listOf()

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: EXECUTE LOG SIZE: ${executeLogs.size}")

            for (executeLog in executeLogs) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: $executeLog")
            }
        }
    }

    private fun logUploadChangeLogs(database : ClientDatabase?, repository: ClientRepository?) : Job {
        return loggingScope.launch {
            val uploadLogs = database?.uploadChangeQueueDao()?.getAllChangeQTU()
                ?: repository?.getAllChangeQTU()
                ?: listOf()

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: UPLOAD_CHANGE LOG SIZE: ${uploadLogs.size}")

            for (uploadLog in uploadLogs) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: $uploadLog")
            }
        }
    }

    private fun logUploadAnalyzedLogs(database : ClientDatabase?, repository: ClientRepository?) : Job {
        return loggingScope.launch {
            val uploadLogs = database?.uploadAnalyzedQueueDao()?.getAllAnalyzedQTU()
                ?: repository?.getAllAnalyzedQTU()
                ?: listOf()

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: UPLOAD_ANALYZED LOG SIZE: ${uploadLogs.size}")

            for (uploadLog in uploadLogs) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: $uploadLog")
            }
        }
    }

    private fun logCallLogs(
        database : ClientDatabase? = null,
        repository: ClientRepository? = null,
        amount: Int? = null
    ) : Job {

        return loggingScope.launch {
            val callLogs = if (amount == null) {
                database?.callDetailDao()?.getCallDetails()
                    ?: repository?.getCallDetails()
                    ?: listOf()
            } else {
                database?.callDetailDao()?.getCallDetailsPartial(amount = amount)
                    ?: repository?.getCallDetailsPartial(amount = amount)
                    ?: listOf()
            }

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: # OF PRINTED CALL LOGS: ${callLogs.size}")

            for (callLog in callLogs) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: CALL LOG: $callLog")
            }
        }
    }

    private fun logAnalyzedNumbers(database : ClientDatabase? = null, repository: ClientRepository? = null) : Job {
        return loggingScope.launch {
            val analyzedNumbers = database?.analyzedNumberDao()?.getAllAnalyzedNum()
                ?: repository?.getAllAnalyzedNum()
                ?: listOf()

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: ANALYZED NUMBER SIZE: ${analyzedNumbers.size}")

            for (analyzedNumber in analyzedNumbers) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: $analyzedNumber")
            }
        }
    }

    private fun logErrorLogs(database : ClientDatabase? = null, repository: ClientRepository? = null) : Job {
        return loggingScope.launch {
            val errorLogs = database?.errorQueueDao()?.getAllErrorLog()
                ?: repository?.getAllErrorLog()
                ?: listOf()

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: ERROR LOG SIZE: ${errorLogs.size}")

            for (errorLog in errorLogs) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: $errorLog")
            }
        }
    }
}