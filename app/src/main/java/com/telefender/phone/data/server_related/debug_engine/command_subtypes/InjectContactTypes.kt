package com.telefender.phone.data.server_related.debug_engine.command_subtypes

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi


enum class InjectDefaultType(val serverString: String) {
    INJ_ADDC("ADDC"),
    INJ_ADDN("ADDN"),
    INJ_UPDN("UPDN"),
    INJ_DELN("DELN"),
    INJ_DELC("DELC"),
}

/**
 * Converts serverStr to InjectDefaultType if possible.
 */
fun String.toInjectDefaultType() : InjectDefaultType? {
    for (injectType in InjectDefaultType.values()) {
        if (this == injectType.serverString) {
            return injectType
        }
    }

    return null
}

sealed class InjectDefaultOperation

@JsonClass(generateAdapter = true)
class InjectADDC(
    val name: String?,
    val numbers: List<String>?
) : InjectDefaultOperation() {

    override fun toString(): String {
        return "InjectADDC - name = $name, numbers = $numbers"
    }
}

@JsonClass(generateAdapter = true)
class InjectADDN(
    val rawCID: String,
    val numbers: List<String>?
) : InjectDefaultOperation() {

    override fun toString(): String {
        return "InjectADDN - rawCID = $rawCID, numbers = $numbers"
    }
}

@JsonClass(generateAdapter = true)
class InjectUPDN(
    val rawCID: String,
    val updates: List<NumberUpdate>?
) : InjectDefaultOperation() {

    override fun toString(): String {
        return "InjectUPDN - rawCID = $rawCID, updates = $updates"
    }
}

@JsonClass(generateAdapter = true)
class NumberUpdate(
    val oldNumber: String,
    val newNumber: String
) {
    override fun toString(): String {
        return "{ Update | oldNumber = $oldNumber, newNumber = $newNumber }"
    }
}

@JsonClass(generateAdapter = true)
class InjectDELN(
    val rawCID: String,
    val numbers: List<String>?
) : InjectDefaultOperation() {

    override fun toString(): String {
        return "InjectDELN - rawCID = $rawCID, numbers = $numbers"
    }
}

@JsonClass(generateAdapter = true)
class InjectDELC(
    val rawCID: String,
) : InjectDefaultOperation() {

    override fun toString(): String {
        return "InjectDELC - rawCID = $rawCID"
    }
}

/**
 * Need to put try-catch around any sort of Moshi string-to-object function.
 */
fun String.toInjectDefaultOperation(type: InjectDefaultType) : InjectDefaultOperation? {
    return try {
        val operationClass = when(type) {
            InjectDefaultType.INJ_ADDC -> InjectADDC::class.java
            InjectDefaultType.INJ_ADDN -> InjectADDN::class.java
            InjectDefaultType.INJ_UPDN -> InjectUPDN::class.java
            InjectDefaultType.INJ_DELN -> InjectDELN::class.java
            InjectDefaultType.INJ_DELC -> InjectDELC::class.java
        }
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(operationClass)

        adapter.serializeNulls().fromJson(this)
    } catch (e: Exception) {
        null
    }
}