package com.telefender.phone.data.server_related.debug_engine

import android.content.Context
import com.telefender.phone.data.server_related.RemoteDebug
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber


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
}

class LogCommand(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope,
    val message: String
) : Command(context, repository, scope) {

    override suspend fun execute() {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: REMOTE LogCommand - $message")
        RemoteDebug.commandRunning = false
    }

    override fun toString(): String {
        return "LOG COMMAND | message: $message"
    }
}

class QueryCommand(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope,
    val queryString: String
) : Command(context, repository, scope) {

    override suspend fun execute() {
        Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: REMOTE QueryCommand - $queryString")
        RemoteDebug.commandRunning = false
    }

    override fun toString(): String {
        return "QUERY COMMAND | queryString: $queryString"
    }
}