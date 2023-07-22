package com.telefender.phone.data.server_related.debug_engine

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
        if (this.lowercase() == injectType.serverString) {
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
) : InjectDefaultOperation()

@JsonClass(generateAdapter = true)
class InjectADDN(
    val defaultCID: String,
    val numbers: List<String>?
) : InjectDefaultOperation()

@JsonClass(generateAdapter = true)
class InjectUPDN(
    val defaultCID: String,
    val updates: List<NumberUpdate>?
) : InjectDefaultOperation()

@JsonClass(generateAdapter = true)
class NumberUpdate(
    val oldNumber: String,
    val newNumber: String
)

@JsonClass(generateAdapter = true)
class InjectDELN(
    val defaultCID: String,
    val numbers: List<String>?
) : InjectDefaultOperation()

@JsonClass(generateAdapter = true)
class InjectDELC(
    val defaultCID: String,
) : InjectDefaultOperation()

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