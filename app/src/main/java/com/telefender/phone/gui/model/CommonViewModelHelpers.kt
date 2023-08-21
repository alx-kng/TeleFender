package com.telefender.phone.gui.model

import android.content.Context
import com.telefender.phone.App
import com.telefender.phone.data.tele_database.entities.Change
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.data.tele_database.entities.ChangeType
import com.telefender.phone.data.tele_database.entities.SafeAction
import com.telefender.phone.gui.adapters.recycler_view_items.common_types.SafetyStatus
import com.telefender.phone.gui.adapters.recycler_view_items.common_types.getSafetyStatus
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.util.*


object CommonViewModelHelpers {

    /**
     * TODO: Maybe we need to modify the default database too?
     * TODO: Probably need to do extra checks for database initialization or query fails.
     *
     * Changes the blocked status of the contact in our database when the blocked button is pressed
     * in the ViewContactFragment OR CallHistoryFragment.
     */
    fun changeIsBlocked(
        applicationContext: Context,
        scope: CoroutineScope,
        selectCIDSnapshot: String?,
        currentBlockedStatus: Boolean,
        updateUIDataListLambda: suspend (Boolean) -> Unit
    )  {
        val database = (applicationContext as App).database
        val repository = applicationContext.repository

        // Negates current blocked status as new blocked status
        val newBlockedStatus = !currentBlockedStatus

        // Only changed blocked status is contact exists.
        if (selectCIDSnapshot != null) {
            scope.launch {
                /*
                Tries to defaultCID to teleCID. Note that this requires that our database is
                initialized, otherwise this just returns.
                 */
                val instanceNumber = TeleHelpers.getUserNumberUncertain(applicationContext)
                val teleCID = instanceNumber?.let {
                    TeleHelpers.defaultCIDToTeleCID(selectCIDSnapshot, instanceNumber)
                } ?: return@launch

                val changeID = UUID.randomUUID().toString()
                val changeTime = Instant.now().toEpochMilli()

                val change = Change.create(
                    CID = teleCID,
                    blocked = newBlockedStatus
                )

                database.changeAgentDao().changeFromClient(
                    ChangeLog.create(
                        changeID = changeID,
                        changeTime = changeTime,
                        type = ChangeType.CONTACT_UPDATE,
                        instanceNumber = instanceNumber,
                        changeJson = change.toJson()
                    ),
                    fromSync = false,
                    bubbleError = false
                )

                repository.getContact(teleCID = teleCID)?.blocked?.let { actualBlockedStatus ->
                    // Update the UI with the actual blocked status if change was successful.
                    if (actualBlockedStatus == newBlockedStatus) {
                        /*
                        Unfortunately, we need to make a new copy BlockedStatus item in order for
                        the UI to pick up the changes, as updating the BlockedStatus item already
                        in the UI data list, will not register with the adapter (it basically does
                        this == this).
                         */
                        updateUIDataListLambda(actualBlockedStatus)
                    }
                }
            }
        }
    }

    /**
     * Changes the safety status of the number in our database when a safety button is pressed in
     * the CallHistoryFragment OR NotifyListFragment.
     */
    fun changeSafetyStatus(
        applicationContext: Context,
        scope: CoroutineScope,
        number: String,
        newSafetyStatus: SafetyStatus,
        updateUIDataListLambda: suspend (SafetyStatus) -> Unit
    )  {
        val database = (applicationContext as App).database
        val repository = applicationContext.repository

        scope.launch {
            val normalizedNumber = TeleHelpers.normalizedNumber(number)
                ?: return@launch

            val instanceNumber = TeleHelpers.getUserNumberUncertain(applicationContext)
                ?:return@launch

            val changeID = UUID.randomUUID().toString()
            val changeTime = Instant.now().toEpochMilli()

            val change = Change.create(
                normalizedNumber = normalizedNumber,
                safeAction = when (newSafetyStatus) {
                    SafetyStatus.SPAM -> SafeAction.BLOCKED
                    SafetyStatus.DEFAULT -> SafeAction.DEFAULT
                    SafetyStatus.SAFE -> SafeAction.SAFE
                }
            )

            database.changeAgentDao().changeFromClient(
                ChangeLog.create(
                    changeID = changeID,
                    changeTime = changeTime,
                    type = ChangeType.NON_CONTACT_UPDATE,
                    instanceNumber = instanceNumber,
                    changeJson = change.toJson()
                ),
                fromSync = false,
                bubbleError = false
            )

            val analyzedNum = repository.getAnalyzedNum(normalizedNumber)
            val analyzed = analyzedNum?.getAnalyzed() ?: return@launch
            val actualSafetyStatus = getSafetyStatus(
                isBlocked = analyzed.isBlocked,
                markedSafe = analyzed.markedSafe
            )

            // Update the UI with the actual safety status if change was successful.
            if (actualSafetyStatus == newSafetyStatus) {
                /*
                Unfortunately, we need to make a new copy SafetyStatus item in order for
                the UI to pick up the changes, as updating the SafetyStatus item already
                in the UI data list, will not register with the adapter (it basically does
                this == this).
                 */
                updateUIDataListLambda(actualSafetyStatus)
            }
        }
    }
}