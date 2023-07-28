package com.telefender.phone.data.server_related.debug_engine.command_subtypes

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi


enum class ConsoleTestType(val serverString: String, val requiresParam: Boolean = false) {
    EXAMPLE(serverString = "example", requiresParam = true),
    LIST_TESTS(serverString = "list_tests"),
    SYNC_CONTACTS(serverString = "sync_contacts"),
    SYNC_LOGS(serverString = "sync_logs"),
    DOWNLOAD_DATA(serverString = "download"),
    UPLOAD_DATA(serverString = "upload"),
    WORK_STATES(serverString = "work_states")
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

/**
 * Need to put try-catch around any sort of Moshi string-to-object function.
 */
fun String.toConsoleTestOperation(type: ConsoleTestType) : ConsoleTestOperation? {
    try {
        val operationClass = when(type) {
            ConsoleTestType.EXAMPLE -> ExampleOp::class.java
            else -> return null
        }
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(operationClass)

        return adapter.serializeNulls().fromJson(this)
    } catch (e: Exception) {
        return null
    }
}