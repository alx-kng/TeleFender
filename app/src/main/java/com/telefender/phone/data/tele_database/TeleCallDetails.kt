package com.telefender.phone.data.tele_database

import android.telecom.Call
import com.telefender.phone.call_related.number
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.helpers.DatabaseLogFunctions
import com.telefender.phone.helpers.MiscHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object TeleCallDetails {

    /**
     * TODO: Consider edge case scenario where this query doesn't go through.
     *
     * Used to insert skeleton call log that states whether the number was unallowed or not into
     * the Tele database, as we have no way to tell if the number was unallowed or not just by
     * looking at the default database during sync.
     */
    fun insertCallDetail(call: Call, unallowed: Boolean, repository: ClientRepository?) {
        if (repository == null) { return }

        val number = MiscHelpers.cleanNumber(call.number())
        val epochDate = call.details.creationTimeMillis

        CoroutineScope(Dispatchers.Default).launch {
            repository.insertDetailClient(
                CallDetail(
                number!!,
                null,
                epochDate,
                null,
                null,
                null,
                unallowed)
            )

            DatabaseLogFunctions.logCallLogs(null, repository, 1)
        }
    }
}