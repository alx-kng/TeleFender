package com.telefender.phone.helpers

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.telefender.phone.data.tele_database.ClientDatabase
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.entities.AnalyzedNumber
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.helpers.DatabaseLogFunctions.logCallLogs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

object DatabaseLogFunctions {

    fun logInstanceLogs(database : ClientDatabase?, repository: ClientRepository?) {
        CoroutineScope(Dispatchers.Default).launch {
            val instanceLogs = (database?.instanceDao()?.getAllInstance()) ?: listOf()
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: INSTANCE LOG SIZE: %s", instanceLogs.size.toString())

            for (instanceLog in instanceLogs) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: %s", instanceLog.toString())
            }
        }
    }

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
            val contactNumbers = (database?.contactNumberDao()?.getAllContactNumbers() ?: repository?.getAllContactNumbers()) ?: listOf()
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
                //Log.i("${MiscHelpers.DEBUG_LOG_TAG}: CHANGE LOG JSON", ServerInteractions.changeLogToJson(changeLog))
            }
        }
    }

    fun logExecuteLogs(database : ClientDatabase?, repository: ClientRepository?) {
        CoroutineScope(Dispatchers.Default).launch {
            val executeLogs = (database?.executeQueueDao()?.getAllQTEs() ?: repository?.getAllQTE()) ?: listOf()
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: EXECUTE LOG SIZE: %s", executeLogs.size.toString())

            for (executeLog in executeLogs) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: %s", executeLog.toString())
            }
        }
    }

    fun logUploadLogs(database : ClientDatabase?, repository: ClientRepository?) {
        CoroutineScope(Dispatchers.Default).launch {
            val uploadLogs = (database?.uploadQueueDao()?.getAllQTU() ?: repository?.getAllQTU()) ?: listOf()
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: UPLOAD LOG SIZE: %s", uploadLogs.size.toString())

            for (uploadLog in uploadLogs) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: %s", uploadLog.toString())
            }
        }
    }

    fun logCallLogs(database : ClientDatabase? = null, repository: ClientRepository? = null, amount: Int? = null) {
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

    fun logAnalyzedNumbers(database : ClientDatabase? = null, repository: ClientRepository? = null) {
        CoroutineScope(Dispatchers.Default).launch {
            val analyzedNumbers = (database?.analyzedNumberDao()?.getAllAnalyzedNum()) ?: listOf()
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: ANALYZED NUMBER SIZE: %s", analyzedNumbers.size.toString())

            for (analyzedNumber in analyzedNumbers) {
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: $analyzedNumber")
            }
        }
    }

    /**
     * TODO: Make this easier to use by using enums or something.
     *
     * Temp log for all.
     */
    suspend fun logSelect(database : ClientDatabase? = null, repository: ClientRepository? = null, logWhich: List<Int>) {
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
        if (6 in logWhich) {
            logAnalyzedNumbers(database, repository)
        }
    }

    /**********************************************************************************************
     * TODO: Remove later since we will formally send up data to server in ServerInteractions later
     *  on. Currently, this is just used to quickly get the JSON for some data analysis.
     *********************************************************************************************/

    @JsonClass(generateAdapter = true)
    data class CallDetailChunk(
        val callDetails: List<CallDetail>
    )

    private fun callDetailChunkToJson(callDetailChunk : CallDetailChunk) : String {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<CallDetailChunk> = moshi.adapter(CallDetailChunk::class.java)

        return adapter.serializeNulls().toJson(callDetailChunk)
    }

    /**
     * Gets all call logs as JSON in chunks of 100. Temp solution used for data analysis.
     */
    fun logCallLogsJson(database : ClientDatabase? = null, repository: ClientRepository? = null) {
        CoroutineScope(Dispatchers.Default).launch {

            val callLogs = database?.callDetailDao()?.getCallDetails() ?: repository?.getCallDetails() ?: listOf()

            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: # OF CALL LOGS: %s", callLogs.size.toString())

            var callDetailChunk = mutableListOf<CallDetail>()
            var i = 0
            for (callDetail in callLogs) {
                if (i == 100) {
                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: JSON = ${callDetailChunkToJson(CallDetailChunk(callDetailChunk))}")
                    callDetailChunk = mutableListOf()
                    i = 0
                }
                callDetailChunk.add(callDetail)
                i++
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class AnalyzedChunk(
        val analyzed: List<AnalyzedNumber>
    )

    private fun analyzedChunkToJson(analyzedChunk : AnalyzedChunk) : String {
        val moshi : Moshi = Moshi.Builder().build()
        val adapter : JsonAdapter<AnalyzedChunk> = moshi.adapter(AnalyzedChunk::class.java)

        return adapter.serializeNulls().toJson(analyzedChunk)
    }

    /**
     * Gets all AnalyzedNumbers as JSON in chunks of 100. Temp solution used for data analysis.
     */
    fun logAnalyzedNumbersJson(database : ClientDatabase? = null, repository: ClientRepository? = null) {
        CoroutineScope(Dispatchers.Default).launch {
            val analyzedNumbers = (database?.analyzedNumberDao()?.getAllAnalyzedNum()) ?: listOf()
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: ANALYZED NUMBER SIZE: %s", analyzedNumbers.size.toString())

            var analyzedChunk = mutableListOf<AnalyzedNumber>()
            var i = 0
            for (analyzedNumber in analyzedNumbers) {
                if (i == 100) {
                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: JSON = ${analyzedChunkToJson(AnalyzedChunk(analyzedChunk))}")
                    analyzedChunk = mutableListOf()
                    i = 0
                }
                analyzedChunk.add(analyzedNumber)
                i++
            }

            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: JSON = ${analyzedChunkToJson(AnalyzedChunk(analyzedChunk))}")
        }
    }
}