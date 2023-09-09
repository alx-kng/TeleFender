package com.telefender.phone.data.server_related.debug_engine

import android.content.Context
import com.telefender.phone.App
import com.telefender.phone.data.default_database.DefaultContacts
import com.telefender.phone.data.default_database.TestContacts
import com.telefender.phone.data.server_related.RemoteDebug
import com.telefender.phone.data.server_related.RequestWrappers
import com.telefender.phone.data.server_related.debug_engine.command_subtypes.*
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.ExperimentalWorkStates
import com.telefender.phone.data.tele_database.background_tasks.TableSynchronizer
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.data.tele_database.entities.TableType
import com.telefender.phone.data.tele_database.entities.toChangeLog
import com.telefender.phone.data.tele_database.entities.toTableType
import com.telefender.phone.misc_helpers.*
import com.telefender.phone.misc_helpers.SharedPreferenceHelpers.getServerMode
import com.telefender.phone.misc_helpers.SharedPreferenceHelpers.setServerMode
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
        Timber.i("$DBL: REMOTE EndCommand - Stopping requests...")

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
        Timber.i("$DBL: REMOTE LogCommand - $message")

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
        Timber.i("$DBL: REMOTE HelpCommand")

        val helpString = """
            Possible commands:
            
            <end> 
                | use = "<end>" 
                | Ends debug connection with device.
                
            <log> 
                | use = "<log> {message}" 
                | Echoes {message} back to the server.
                
            <help> 
                | use = "<help>" 
                | Lists helpful commands / info.
                
            <sql-read> 
                | use = "<sql-read> {table_name} {query_string}"
                | Executes {query_string} (MUST BE READ) with return type {table_name} and 
                | returns data to server.
                
            <inj-change>
                | use = "<inj-change> {change_log_json}"
                | Injects {change_log_json} as a ChangeLog into the ExecuteAgent and executes the
                | corresponding action.
                | 
                | Should not be used to inject direct (non-tree) contacts, as they won't stick
                | without the default database being changed. Instead, use 
                
            <inj-def>
                | use = "<inj-def> {operation_type} {operation_json}"
                | Inserts / updates / deletes rows into the default contact database (e.g., 
                | RawContact, Data) given the {operation_type} and {operation_json}.
            
            <test>
                | use = "<test> {test_type} {operation_json_if_applicable}"
                | Runs a test for an experimental functionality.
                | You can use {test_type} = "list_tests" to list all currently available {test_type}.
                  
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
                | instance
                | contact
                | contact_number
                | analyzed_number
                | notify_item
            
            Default operation types
                | ADDC
                    | Adds a new RawContact and Data rows with the given name / numbers.
                    
                | ADDN 
                    | Adds a new Data row for the given numbers.
                    
                | UPDN 
                    | Updates numbers specified by the given updates (oldNumber -> newNumber) 
                    | in the given RawContact (passed in as RawContact ID). 
                    
                | DELN
                    | Deletes all of the Data rows with the given numbers that are linked to the 
                    | specified RawContact (passed in as RawContact ID).
                    
                | DELC
                    | Deletes specified RawContact (passed in as RawContact ID).
                
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
        Timber.i("$DBL: REMOTE ReadQueryCommand - $queryString")

        for (i in 1..retryAmount) {
            try {
                /*
                If the query returns a numerical value (e.g., COUNT, AVG, SUM), then use the
                numerical raw query handler. Otherwise, use the regular raw query handler (which
                returns a list of rows).
                 */
                val isNumericalQuery = queryString.contains(Regex("(?i)select (count|avg|sum)\\("))

                if (isNumericalQuery) {
                    val stringResult = repository.readData_Numerical(queryString).toString()
                    RemoteDebug.enqueueData(stringResult)
                } else {
                    val stringDataList = repository.readData(queryString, tableType)
                        .map { it.toJson() }
                    RemoteDebug.enqueueList(stringDataList)
                }

                break
            } catch (e: Exception) {
                Timber.i("$DBL: ReadQueryCommand RETRYING... Error - ${e.message}")
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

/**
 * TODO: Put a check around this to only allow in secure debug mode. For example, we can start off in
 *  production (non-debug) mode and only switch to secure debug mode after some secret action (e.g.,
 *  7 taps). The important thing is that the debug mode cannot be directly modified by the server,
 *  as otherwise it would be a security leak. Note that the secure debug mode is different from the
 *  regular debug, as secure debug allows the server to inject changes into the device.
 */
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
        Timber.i("$DBL: REMOTE InjectChangeCommand - ${changeLog.toJson()}")

        for (i in 1..retryAmount) {
            try {
                // Bubble error so that error message can be seen by server.
                repository.changeFromClient(changeLog, bubbleError = true)

                break
            } catch (e: Exception) {
                Timber.i("$DBL: InjectChangeCommand RETRYING... Error - ${e.message}")
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

            Timber.e("$DBL: InjectChangeCommand - commandValue = '$commandValue'")
            val changeLog = commandValue?.toChangeLog() ?: return null
            return InjectChangeCommand(context, repository, scope, changeLog)
        }
    }
}

/**
 * TODO: Put a check around this to only allow in secure debug mode. For example, we can start off in
 *  production (non-debug) mode and only switch to secure debug mode after some secret action (e.g.,
 *  7 taps). The important thing is that the debug mode cannot be directly modified by the server,
 *  as otherwise it would be a security leak. Note that the secure debug mode is different from the
 *  regular debug, as secure debug allows the server to inject changes into the device.
 *
 * Used to insert contacts into the default database.
 */
class InjectDefaultCommand(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope,
    private val injectDefaultOp: InjectDefaultOperation
) : Command(context, repository, scope) {

    private val retryAmount = 3

    override suspend fun execute() {
        Timber.i("$DBL: REMOTE InjectDefaultCommand - $injectDefaultOp")

        val contentResolver = context.contentResolver

        for (i in 1..retryAmount) {
            try {
                when(injectDefaultOp) {
                    is InjectADDC -> {
                        RemoteDebug.enqueueData(
                            DefaultContacts.debugInsertContact(
                                contentResolver = contentResolver,
                                name = injectDefaultOp.name,
                                numbers = injectDefaultOp.numbers
                            ).toString()
                        )
                    }
                    is InjectADDN -> {
                        RemoteDebug.enqueueData(
                            DefaultContacts.debugInsertNumber(
                                contentResolver = contentResolver,
                                rawContactID = injectDefaultOp.rawCID,
                                numbers = injectDefaultOp.numbers
                            ).toString()
                        )
                    }
                    is InjectUPDN -> {
                        RemoteDebug.enqueueData(
                            DefaultContacts.debugUpdateNumber(
                                contentResolver = contentResolver,
                                rawContactID = injectDefaultOp.rawCID,
                                updates = injectDefaultOp.updates
                            ).toString()
                        )
                    }
                    is InjectDELN -> {
                        RemoteDebug.enqueueData(
                            DefaultContacts.debugDeleteNumber(
                                contentResolver = contentResolver,
                                rawContactID = injectDefaultOp.rawCID,
                                numbers = injectDefaultOp.numbers
                            ).toString()
                        )
                    }
                    is InjectDELC -> {
                        RemoteDebug.enqueueData(
                            DefaultContacts.debugDeleteRawContact(
                                contentResolver = contentResolver,
                                rawContactID = injectDefaultOp.rawCID
                            ).toString()
                        )
                    }
                }

                break
            } catch (e: Exception) {
                Timber.i("$DBL: InjectDefaultCommand RETRYING... Error - ${e.message}")
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
        return "INJECT-DEFAULT COMMAND | injectDefaultOp: $injectDefaultOp"
    }

    companion object {
        fun create(
            context: Context,
            repository: ClientRepository,
            scope: CoroutineScope,
            commandValue: String?
        ) : InjectDefaultCommand? {

            val injectDefaultOp = parseCommandValue(commandValue) ?: return null
            return InjectDefaultCommand(context, repository, scope, injectDefaultOp)
        }

        private fun parseCommandValue(commandValue: String?) : InjectDefaultOperation? {
            if (commandValue == null) return null

            val args = commandValue.split(" ", limit = 2)
            val injectDefaultType = args.getOrNull(0)?.trim()?.toInjectDefaultType()
            val paramJson = args.getOrNull(1)?.trim()

            return if (injectDefaultType != null && paramJson != null) {
                paramJson.toInjectDefaultOperation(injectDefaultType)
            } else {
                null
            }
        }
    }
}

/**
 * TODO: Add in better help logic. That is, add in more functionality to explain how the command /
 *  testOp works to the console user.
 *
 * TODO: Put a check around this to only allow in secure debug mode. For example, we can start off in
 *  production (non-debug) mode and only switch to secure debug mode after some secret action (e.g.,
 *  7 taps). The important thing is that the debug mode cannot be directly modified by the server,
 *  as otherwise it would be a security leak. Note that the secure debug mode is different from the
 *  regular debug, as secure debug allows the server to inject changes into the device.
 *
 * Used to easily test whatever new functionality we have.
 */
class ConsoleTestCommand(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope,
    private val consoleTestType: ConsoleTestType,
    private val consoleTestOp: ConsoleTestOperation?
) : Command(context, repository, scope) {

    private val retryAmount = 3

    override suspend fun execute() {
        Timber.i("$DBL: REMOTE ConsoleTestCommand - %s",
            "consoleTestType = $consoleTestType, consoleTestOp = $consoleTestType")

        val contentResolver = context.contentResolver
        val repository = (context.applicationContext as App).repository
        val database = (context.applicationContext as App).database

        for (i in 1..retryAmount) {
            try {
                when(consoleTestType) {
                    ConsoleTestType.EXAMPLE -> RemoteDebug.enqueueData(consoleTestOp!!.toString())
                    ConsoleTestType.LIST_TESTS -> {
                        for (testType in ConsoleTestType.values()) {
                            RemoteDebug.enqueueData("ConsoleTestType - \"${testType.serverString}\"")
                        }
                    }
                    ConsoleTestType.SYNC_CONTACTS -> {
                        RemoteDebug.enqueueData("sync_contacts - start = ${Instant.now().toEpochMilli()}")
                        TableSynchronizer.syncContacts(
                            context = context,
                            database = database,
                            contentResolver = contentResolver
                        )
                        RemoteDebug.enqueueData("sync_contacts - end = ${Instant.now().toEpochMilli()}")
                    }
                    ConsoleTestType.SYNC_LOGS -> {
                        RemoteDebug.enqueueData("sync_logs - start = ${Instant.now().toEpochMilli()}")
                        TableSynchronizer.syncCallLogs(
                            context = context,
                            repository = repository,
                            contentResolver = contentResolver
                        )
                        RemoteDebug.enqueueData("sync_logs - end = ${Instant.now().toEpochMilli()}")
                    }
                    ConsoleTestType.DOWNLOAD_DATA -> {
                        RemoteDebug.enqueueData("download - start = ${Instant.now().toEpochMilli()}")
                        RequestWrappers.downloadData(context, repository, scope, "DEBUG")
                        RemoteDebug.enqueueData("download - end = ${Instant.now().toEpochMilli()}")
                    }
                    ConsoleTestType.UPLOAD_DATA -> {
                        RemoteDebug.enqueueData("upload - start = ${Instant.now().toEpochMilli()}")
                        RequestWrappers.uploadChange(context, repository, scope, "DEBUG")
                        RequestWrappers.uploadAnalyzed(context, repository, scope, "DEBUG")
                        RequestWrappers.uploadError(context, repository, scope, "DEBUG")
                        RemoteDebug.enqueueData("upload - end = ${Instant.now().toEpochMilli()}")
                    }
                    ConsoleTestType.WORK_STATES -> {
                        RemoteDebug.enqueueData("Generalized Work States:")
                        RemoteDebug.enqueueData("-----------------------")
                        for (workType in WorkType.values()) {
                            RemoteDebug.enqueueData(
                                "$workType = ${ExperimentalWorkStates.generalizedGetState(workType)}"
                            )
                        }
                        RemoteDebug.enqueueData("-----------------------")
                        RemoteDebug.enqueueData("Localized Work States:")
                        RemoteDebug.enqueueData("-----------------------")
                        for (workType in WorkType.values()) {
                            RemoteDebug.enqueueData(
                                "$workType = ${ExperimentalWorkStates.localizedGetStatesByType(workType)}"
                            )
                        }
                    }
                    ConsoleTestType.EXECUTE_CHANGES -> {
                        RemoteDebug.enqueueData("execute - start = ${Instant.now().toEpochMilli()}")
                        repository.executeAll()
                        RemoteDebug.enqueueData("execute - end = ${Instant.now().toEpochMilli()}")
                    }
                    ConsoleTestType.LOG_CONTACTS_AGGR -> {
                        RemoteDebug.enqueueList(
                            TestContacts.printContactDataTable(context, contentResolver)
                        )
                    }
                    ConsoleTestType.LOG_CONTACTS_RAW -> {
                        RemoteDebug.enqueueList(
                            TestContacts.printRawContactsTable(context)
                        )
                    }
                    ConsoleTestType.LOG_CONTACTS_DATA -> {
                        RemoteDebug.enqueueList(
                            TestContacts.printContactDataTable(context, contentResolver)
                        )
                    }
                    ConsoleTestType.CHANGE_SERVER_MODE -> {
                        val currentParameters = repository.getParameters()
                        val newServerMode = (consoleTestOp as ChangeServerModeOp).newServerMode

                        if (newServerMode != null && currentParameters != null) {
                            SharedPreferenceHelpers.setServerMode(
                                context,
                                newServerMode
                            )
                        }

                        val actualServerMode = SharedPreferenceHelpers.getServerMode(context)
                        RemoteDebug.enqueueData("New server mode: $actualServerMode")
                    }
                    ConsoleTestType.SHOULD_VERIFY_SMS -> {
                        val currentParameters = repository.getParameters()
                        val newShouldVerifySMS = (consoleTestOp as ShouldVerifySMSOp).arg

                        if (currentParameters != null) {
                            repository.updateParameters(
                                currentParameters.copy(shouldVerifySMS = newShouldVerifySMS)
                            )
                        }

                        val actualShouldVerifySMS = repository.getParameters()?.shouldVerifySMS
                        RemoteDebug.enqueueData("New should verify SMS: $actualShouldVerifySMS")
                    }
                    ConsoleTestType.SHOULD_UPLOAD_CALL_STATE -> {
                        val currentParameters = repository.getParameters()
                        val newShouldUploadCallState = (consoleTestOp as ShouldUploadCallState).arg

                        if (currentParameters != null) {
                            repository.updateParameters(
                                currentParameters.copy(shouldDebugCallState = newShouldUploadCallState)
                            )
                        }

                        val actualShouldUploadCallState = repository.getParameters()?.shouldDebugCallState
                        RemoteDebug.enqueueData("New should upload call state: $actualShouldUploadCallState")
                    }
                }

                break
            } catch (e: Exception) {
                Timber.i("$DBL: ConsoleTestCommand RETRYING... Error - ${e.message}")
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
        return "CONSOLE-TEST COMMAND | consoleTestType: $consoleTestType, consoleTestOp: $consoleTestOp"
    }

    companion object {
        fun create(
            context: Context,
            repository: ClientRepository,
            scope: CoroutineScope,
            commandValue: String?
        ) : ConsoleTestCommand? {

            val parsedCommand = parseCommandValue(commandValue) ?: return null
            val consoleTestType = parsedCommand.first
            val consoleTestOp = parsedCommand.second?.toConsoleTestOperation(consoleTestType)

            /*
            If test operation needs class, and paramJson isn't correctly parsed, then return null
            so that the DebugEngine knows that the command was formatted incorrectly.
             */
            if (consoleTestType.requiresParam && consoleTestOp == null) {
                return null
            }

            return ConsoleTestCommand(context, repository, scope, consoleTestType, consoleTestOp)
        }

        private fun parseCommandValue(commandValue: String?) : Pair<ConsoleTestType, String?>? {
            if (commandValue == null) return null

            val args = commandValue.split(" ", limit = 2)
            val consoleTestType = args.getOrNull(0)?.trim()?.toConsoleTestType()
            val paramJson = args.getOrNull(1)?.trim()

            return if (consoleTestType != null) {
                Pair(consoleTestType, paramJson)
            } else {
                null
            }
        }
    }
}




