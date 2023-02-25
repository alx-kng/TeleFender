package com.telefender.phone.data.server_related.debug_engine

import android.content.Context
import com.telefender.phone.data.server_related.RemoteDebug
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.TableSynchronizer
import com.telefender.phone.data.tele_database.entities.TableType
import com.telefender.phone.data.tele_database.entities.toTableType
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import timber.log.Timber
import java.time.Instant


abstract class Command(
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

class ReadQueryCommand(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope,
    private val queryString: String,
    private val tableType: TableType
) : Command(context, repository, scope) {

    private val retryAmount = 5

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
                    RemoteDebug.error = e.message
                }
            }
        }

        RemoteDebug.lastCommandTime = Instant.now().toEpochMilli()
        RemoteDebug.commandRunning = false
    }

    override fun toString(): String {
        return "QUERY COMMAND | queryString: $queryString"
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