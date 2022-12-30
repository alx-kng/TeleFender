package com.telefender.phone.data.tele_database

import android.content.Context
import android.telecom.Call
import com.telefender.phone.call_related.callDurationMILLI
import com.telefender.phone.call_related.createTime
import com.telefender.phone.call_related.number
import com.telefender.phone.data.tele_database.background_tasks.TableSynchronizer
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

object TeleCallDetails {

    private const val retryAmount = 5

    fun syncCallImmediate(context: Context?, repository: ClientRepository?, scope: CoroutineScope) {
        if (repository == null || context == null) {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: syncCallImmediate() PROBLEM - repository = $repository - context = $context")
            return
        }

        scope.launch {
            for (i in 1..retryAmount) {
                val success = TableSynchronizer.syncCallLogs(context, repository, context.contentResolver)
                if (success) break
                Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: syncCallImmediate() RETRYING...")
                delay(2000)
            }
        }
    }

    /**
     * TODO: Consider edge case scenario where this query doesn't go through.
     * TODO: Call this at end of in call activity.
     *
     * Used to insert skeleton call log that states whether the number was unallowed or not into
     * the Tele database, as we have no way to tell if the number was unallowed or not just by
     * looking at the default database during sync.
     *
     * NOTE: You should call insertCallDetail() AS CLOSE TO THE END OF CALL AS POSSIBLE so that
     * calculated call duration is more accurate.
     */
    fun insertCallDetail(repository: ClientRepository?, call: Call, unallowed: Boolean, direction: Int?) {
        if (repository == null) { return }

        CoroutineScope(Dispatchers.Default).launch {
            val instanceNumber = repository.getInstanceNumber()
            val rawNumber = call.number()
            val normalizedNumber = MiscHelpers.normalizedNumber(rawNumber)
                ?: MiscHelpers.bareNumber(rawNumber)
            val epochDate = call.createTime()
            val duration = call.callDurationMILLI()

            val callDetail = CallDetail(
                rawNumber = rawNumber ?: MiscHelpers.INVALID_NUMBER,
                normalizedNumber = normalizedNumber,
                callType = null,
                callEpochDate = epochDate,
                callDuration = duration,
                callLocation = null,
                callDirection = direction,
                instanceNumber = instanceNumber!!,
                unallowed = unallowed
            )

            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: $callDetail")

            for (i in 1..retryAmount) {
                try {
                    repository.insertDetailSkeleton(callDetail)
                    break
                } catch (e: Exception) {
                    Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: insertCallDetail() RETRYING...")
                    delay(2000)
                }
            }
        }
    }
}