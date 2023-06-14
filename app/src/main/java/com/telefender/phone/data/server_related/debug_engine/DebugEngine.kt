package com.telefender.phone.data.server_related.debug_engine

import android.content.Context
import androidx.work.WorkInfo
import com.telefender.phone.data.server_related.RemoteDebug
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant

object DebugEngine {

    suspend fun execute(
        context: Context,
        repository: ClientRepository,
        scope: CoroutineScope,
        commandString: String?,
        workerName: String
    ) {
        val currentTime = Instant.now().toEpochMilli()

        if (RemoteDebug.startTime == 0L) {
            RemoteDebug.startTime = currentTime
        }

        if (commandString != null) {
            RemoteDebug.commandRunning = true
        }

        /*
        If the debug exchange has been running for [maxIdlePeriod] amount of time since the last
        completed command, and there is no new command, then we end the debug exchange to prevent
        infinite exchange.
         */
        val timeSinceCommandComplete = currentTime - (RemoteDebug.lastCommandTime ?: RemoteDebug.startTime)
        if (!RemoteDebug.commandRunning && timeSinceCommandComplete > RemoteDebug.maxIdlePeriod) {
            Timber.i("$DBL: " +
                "REMOTE - Max idle period reached! Ending debug exchange!")

            RemoteDebug.resetStates()
            WorkStates.setState(WorkType.DEBUG_EXCHANGE_POST, WorkInfo.State.SUCCEEDED)
            return
        }

        val command = parse(context, repository, scope, commandString)

        scope.launch(Dispatchers.IO) {
            command?.execute()
        }

        /*
        Only determines if command if shallowly incorrectly formatted. That is, if the command
        doesn't have <command_type> at the front or has the wrong number of arguments. This doesn't
        catch cases where the command is accepted by our parser but fails during execution. For
        example, if the table name in a query command is incorrect, it will not be caught here
        (and will instead be caught in the actual command class).
         */
        if (commandString != null && command == null) {
            val errorString = """
                Command formatted incorrectly! (Shallow check) | $commandString
                Type <help> for helpful info.
            """.trimIndent()

            RemoteDebug.error = errorString
            RemoteDebug.commandRunning = false

            Timber.e("$DBL: $errorString")
        }

        // If not EndCommand keep sending exchange data requests.
        if (command !is EndCommand) {
            delay(1000)
            RemoteDebug.debugExchangeRequest(context, repository, scope, workerName)

            // Reset error to null after we send it up to the server.
            RemoteDebug.error = null
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
        val commandType = commandParts.getOrNull(0)?.trim()
        val commandValue = commandParts.getOrNull(1)?.trim()

        return when (commandType) {
            "<end>" -> EndCommand.create(context, repository, scope)
            "<log>" -> LogCommand.create(context, repository, scope, commandValue)
            "<help>" -> HelpCommand.create(context, repository, scope)
            "<sql-read>" -> ReadQueryCommand.create(context, repository, scope, commandValue)
            "<inj-change>" -> InjectChangeCommand.create(context, repository, scope, commandValue)
            else -> null
        }
    }
}