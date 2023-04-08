package com.telefender.phone.data.server_related.debug_engine

import android.content.Context
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import com.telefender.phone.data.server_related.RemoteDebug
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.TableSynchronizer
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.data.tele_database.entities.TableType
import com.telefender.phone.data.tele_database.entities.toChangeLog
import com.telefender.phone.data.tele_database.entities.toTableType
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import timber.log.Timber
import java.time.Instant


sealed class Command(
    val context: Context,
    val repository: ClientRepository,
    val scope: CoroutineScope,
) {
    abstract suspend fun execute()
}

class EndCommand(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope,
) : Command(context, repository, scope) {

    override suspend fun execute() {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: REMOTE EndCommand - Stopping requests...")

        RemoteDebug.resetStates()
    }

    override fun toString(): String {
        return "END COMMAND"
    }

    companion object {
        fun create(
            context: Context,
            repository: ClientRepository,
            scope: CoroutineScope,
        ) : EndCommand {

            return EndCommand(context, repository, scope)
        }
    }
}

class LogCommand(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope,
    val message: String?
) : Command(context, repository, scope) {

    override suspend fun execute() {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: REMOTE LogCommand - $message")

        // Echoes message to server.
        RemoteDebug.enqueueData(message ?: "null")

        RemoteDebug.lastCommandTime = Instant.now().toEpochMilli()
        RemoteDebug.commandRunning = false
    }

    override fun toString(): String {
        return "LOG COMMAND | message: $message"
    }

    companion object {
        fun create(
            context: Context,
            repository: ClientRepository,
            scope: CoroutineScope,
            commandValue: String?
        ) : LogCommand {

            return LogCommand(context, repository, scope, commandValue)
        }
    }
}

class HelpCommand(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope,
) : Command(context, repository, scope) {

    override suspend fun execute() {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: REMOTE HelpCommand")

        val helpString = """
            Possible commands:
            
            <end> 
                | use = "<end>" 
                | Ends debug connection with device.
                
            <log> 
                | use = "<log> {message}" 
                | Echoes {message} back to the server.
                
            <help> 
                | use = "<help>" | Lists helpful commands / info.
                
            <sql-read> 
                | use = "<sql-read> {table_name} {query_string}"
                | Executes {query_string} (MUST BE READ) with return type {table_name} and 
                | returns data to server.
                
            <inj-change>
                | use = "<inj-read> {change_log_json}"
                | Injects {change_log_json} as a ChangeLog into the ExecuteAgent and executes the
                | corresponding action.
                  
            Helpful info:
            
            Table types
                | change_log
                | execute_queue
                | upload_change_queue
                | upload_analyzed_queue
                | error_queue
                | stored_map
                | parameters
                | call_detail
                | contact
                | contact_number
                | analyzed_number
                | notify_item
                
        """.trimIndent()

        // Sends helpString to server.
        RemoteDebug.enqueueData(helpString)

        RemoteDebug.lastCommandTime = Instant.now().toEpochMilli()
        RemoteDebug.commandRunning = false
    }

    override fun toString(): String {
        return "HELP COMMAND"
    }

    companion object {
        fun create(
            context: Context,
            repository: ClientRepository,
            scope: CoroutineScope,
        ) : HelpCommand {

            return HelpCommand(context, repository, scope)
        }
    }
}

class ReadQueryCommand(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope,
    private val queryString: String,
    private val tableType: TableType
) : Command(context, repository, scope) {

    private val retryAmount = 3

    override suspend fun execute() {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: REMOTE ReadQueryCommand - $queryString")

        for (i in 1..retryAmount) {
            try {
                val stringDataList = repository.readData(queryString, tableType)
                    .map { it.toJson() }
                RemoteDebug.enqueueList(stringDataList)

                break
            } catch (e: Exception) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: ReadQueryCommand RETRYING... Error - ${e.message}")
                delay(2000)

                // On last retry, set the debug exchange error message (so server can see it).
                if (i == retryAmount) {
                    RemoteDebug.error = "${e.message}\nType <help> for helpful info."
                }
            }
        }

        RemoteDebug.lastCommandTime = Instant.now().toEpochMilli()
        RemoteDebug.commandRunning = false
    }

    override fun toString(): String {
        return "READ-QUERY COMMAND | queryString: $queryString"
    }

    companion object {
        fun create(
            context: Context,
            repository: ClientRepository,
            scope: CoroutineScope,
            commandValue: String?
        ) : ReadQueryCommand? {

            val args = parseCommandValue(commandValue) ?: return null
            return ReadQueryCommand(context, repository, scope, args.first, args.second)
        }

        private fun parseCommandValue(commandValue: String?) : Pair<String, TableType>? {
            if (commandValue == null) return null

            val args = commandValue.split(" ", limit = 2)
            val tableType = args.getOrNull(0)?.trim()?.toTableType()
            val queryString = args.getOrNull(1)?.trim()

            return if (queryString != null && tableType != null) {
                Pair(queryString, tableType)
            } else {
                null
            }
        }
    }
}

class InjectChangeCommand(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope,
    private val changeLog: ChangeLog
) : Command(context, repository, scope) {

    private val retryAmount = 1

    /**
     * TODO: Need to be able to insert / delete / update default database as well in order for
     *  ChangeLogs affecting the user's direct contacts to stick. --> May eventually be handled
     *  in changeFromClient.
     */
    override suspend fun execute() {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: REMOTE InjectChangeCommand - ${changeLog.toJson()}")

        for (i in 1..retryAmount) {
            try {
                // Bubble error so that error message can be seen by server.
                repository.changeFromClient(changeLog, bubbleError = true)

                break
            } catch (e: Exception) {
                Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: InjectChangeCommand RETRYING... Error - ${e.message}")
                delay(2000)

                // On last retry, set the debug exchange error message (so server can see it).
                if (i == retryAmount) {
                    RemoteDebug.error = "${e.message}\nType <help> for helpful info."
                }
            }
        }

        RemoteDebug.lastCommandTime = Instant.now().toEpochMilli()
        RemoteDebug.commandRunning = false
    }

    override fun toString(): String {
        return "INJECT-CHANGE COMMAND | changeLog = ${changeLog.toJson()}"
    }

    companion object {
        fun create(
            context: Context,
            repository: ClientRepository,
            scope: CoroutineScope,
            commandValue: String?
        ) : InjectChangeCommand? {

            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: InjectChangeCommand - commandValue = '$commandValue'")
            val changeLog = commandValue?.toChangeLog() ?: return null
            return InjectChangeCommand(context, repository, scope, changeLog)
        }
    }
}