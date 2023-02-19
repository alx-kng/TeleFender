package com.telefender.phone.data.server_related.debug_engine

import android.content.Context
import android.os.Build.VERSION_CODES.P
import androidx.work.WorkInfo
import com.telefender.phone.data.server_related.RemoteDebug
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

object DebugEngine {

    suspend fun execute(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        commandString: String?
    ) {
        if (commandString != null) {
            RemoteDebug.commandRunning = true
        }

        val command = parse(context, repository, scope, commandString)

        scope.launch(Dispatchers.IO) {
            command?.execute()
        }

        if (commandString != null && command == null) {
            Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: Command formatted incorrectly! $command")
        }

        // If not EndCommand keep sending exchange data requests.
        if (command !is EndCommand) {
            delay(1000)
            RemoteDebug.debugExchangeRequest(context, repository, scope)
        } else {
            WorkStates.setState(WorkType.DEBUG_EXCHANGE_POST, WorkInfo.State.SUCCEEDED)
        }
    }

    fun parse(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        commandString: String?
    ) : Command? {
        if (commandString == null) return null

        val commandParts = commandString.split(" ", limit = 2)

        val commandType = commandParts.firstOrNull()?.trim()
        val commandValue = if (commandParts.size == 2) {
            commandParts[1].trim()
        } else {
            null
        }

        return when (commandType) {
            "<end>" -> EndCommand(context, repository, scope)
            "<sql>" -> if (commandValue != null) QueryCommand(context, repository, scope, commandValue) else null
            "<log>" -> LogCommand(context, repository, scope, commandValue ?: "")
            else -> null
        }
    }
}