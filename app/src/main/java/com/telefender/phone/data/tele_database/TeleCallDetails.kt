package com.telefender.phone.data.tele_database

import android.content.Context
import android.telecom.Call
import com.telefender.phone.App
import com.telefender.phone.call_related.callDurationMILLI
import com.telefender.phone.call_related.createTime
import com.telefender.phone.call_related.number
import com.telefender.phone.data.tele_database.background_tasks.TableSynchronizer
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

object TeleCallDetails {

    private const val retryAmount = 3

    fun syncCallImmediate(context: Context?, repository: ClientRepository?, scope: CoroutineScope) {
        if (repository == null || context == null) {
            Timber.e("$DBL: syncCallImmediate() PROBLEM - repository = $repository - context = $context")
            return
        }

        scope.launch {
            TableSynchronizer.syncCallLogs(context, repository, context.contentResolver)
        }
    }

    /**
     * TODO: Consider edge case scenario where this query doesn't go through.
     * TODO: Call this at end of in call activity -> maybe no need to, as it won't be unallowed.
     *
     * Used to insert skeleton call log that states whether the number was unallowed or not into
     * the Tele database, as we have no way to tell if the number was unallowed or not just by
     * looking at the default database during sync.
     *
     * NOTE: You should call insertCallDetail() AS CLOSE TO THE END OF CALL AS POSSIBLE so that
     * calculated call duration is more accurate.
     */
    fun insertCallDetail(context: Context, call: Call, unallowed: Boolean, direction: Int) {
        if (!TeleHelpers.hasValidStatus(context, setupRequired = false)) { return }

        val repository = (context.applicationContext as App).repository

        CoroutineScope(Dispatchers.Default).launch {
            val instanceNumber = TeleHelpers.getUserNumberStored(context) ?: return@launch
            val rawNumber = call.number()
            val normalizedNumber = TeleHelpers.normalizedNumber(rawNumber)
                ?: TeleHelpers.bareNumber(rawNumber)
            val epochDate = call.createTime()
            val duration = call.callDurationMILLI()

            val callDetail = CallDetail(
                rawNumber = rawNumber ?: TeleHelpers.UNKNOWN_NUMBER,
                normalizedNumber = normalizedNumber,
                callType = null,
                callEpochDate = epochDate,
                callDuration = duration,
                callLocation = null,
                callDirection = direction,
                instanceNumber = instanceNumber,
                unallowed = unallowed
            )

            Timber.e("$DBL: $callDetail")

            for (i in 1..retryAmount) {
                try {
                    repository.insertCallDetailSkeleton(callDetail)
                    break
                } catch (e: Exception) {
                    Timber.i("$DBL: insertCallDetail() RETRYING...")
                    delay(2000)
                }
            }
        }
    }
}