package com.telefender.phone.data.server_related.debug_engine.command_subtypes

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.telefender.phone.data.tele_database.entities.toServerMode


enum class ConsoleTestType(val serverString: String, val requiresParam: Boolean = false) {
    EXAMPLE(serverString = "example", requiresParam = true),
    LIST_TESTS(serverString = "list_tests"),
    SYNC_CONTACTS(serverString = "sync_contacts"),
    SYNC_LOGS(serverString = "sync_logs"),
    DOWNLOAD_DATA(serverString = "download"),
    UPLOAD_DATA(serverString = "upload"),
    WORK_STATES(serverString = "work_states"),
    EXECUTE_CHANGES(serverString = "execute"),
    LOG_CONTACTS_AGGR(serverString = "log_contacts_aggr"),
    LOG_CONTACTS_RAW(serverString = "log_contacts_raw"),
    LOG_CONTACTS_DATA(serverString = "log_contacts_data"),
    CHANGE_SERVER_MODE(serverString = "change_server_mode", requiresParam = true),
    SHOULD_VERIFY_SMS(serverString = "should_sms", requiresParam = true)
}

/**
 * Converts serverStr to ConsoleTestType if possible.
 */
fun String.toConsoleTestType() : ConsoleTestType? {
    for (testType in ConsoleTestType.values()) {
        if (this == testType.serverString) {
            return testType
        }
    }

    return null
}

sealed class ConsoleTestOperation

@JsonClass(generateAdapter = true)
class ExampleOp(val arg: String) : ConsoleTestOperation() {
    override fun toString(): String {
        return "ExampleOp - arg = $arg - Does nothing!"
    }
}

@JsonClass(generateAdapter = true)
class ChangeServerModeOp(val arg: String) : ConsoleTestOperation() {
    val newServerMode = arg.toServerMode()

    override fun toString(): String {
        return "ChangeServerModeOp - arg = $arg!"
    }
}

@JsonClass(generateAdapter = true)
class ShouldVerifySMSOp(val arg: Boolean) : ConsoleTestOperation() {

    override fun toString(): String {
        return "ShouldVerifySMSOp - arg = $arg!"
    }
}

/**
 * Converts argument into [ConsoleTestOperation] given the [ConsoleTestType].
 * Need to put try-catch around any sort of Moshi string-to-object function.
 */
fun String.toConsoleTestOperation(type: ConsoleTestType) : ConsoleTestOperation? {
    try {
        val operationClass = when(type) {
            ConsoleTestType.EXAMPLE -> ExampleOp::class.java
            ConsoleTestType.CHANGE_SERVER_MODE -> ChangeServerModeOp::class.java
            ConsoleTestType.SHOULD_VERIFY_SMS -> ShouldVerifySMSOp::class.java
            else -> return null
        }
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(operationClass)

        return adapter.serializeNulls().fromJson(this)
    } catch (e: Exception) {
        return null
    }
}