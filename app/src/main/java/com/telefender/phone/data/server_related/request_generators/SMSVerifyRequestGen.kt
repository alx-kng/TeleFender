package com.telefender.phone.data.server_related.request_generators

import android.content.Context
import androidx.work.WorkInfo
import com.android.volley.Response
import com.telefender.phone.data.server_related.json_classes.DefaultResponse
import com.telefender.phone.data.server_related.json_classes.SMSVerifyResponse
import com.telefender.phone.data.server_related.json_classes.ServerResponseType
import com.telefender.phone.data.server_related.json_classes.toServerResponse
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.data.tele_database.background_tasks.WorkStates
import com.telefender.phone.data.tele_database.background_tasks.WorkType
import com.telefender.phone.data.tele_database.entities.Change
import com.telefender.phone.data.tele_database.entities.ChangeLog
import com.telefender.phone.data.tele_database.entities.ChangeType
import com.telefender.phone.data.tele_database.entities.SafeAction
import com.telefender.phone.helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.util.*


class SMSVerifyRequestGen(
    method: Int,
    url: String,
    listener: Response.Listener<String>,
    errorListener: Response.ErrorListener,
    requestJson: String?,
) : RequestGen(method, url, listener, errorListener, requestJson) {

    companion object {
        fun create(
            method: Int,
            url: String,
            requestJson: String?,
            context: Context,
            repository: ClientRepository,
            scope: CoroutineScope,
            number: String
        ) : SMSVerifyRequestGen {

            return SMSVerifyRequestGen(
                method = method,
                url = url,
                listener = smsVerifyResponseHandler(context, repository, scope, number),
                errorListener = smsVerifyErrorHandler,
                requestJson = requestJson
            )
        }
    }
}

/**
 * Retrieves DownloadResponse object containing (status, error, number, verified)
 * and updates AnalyzedNumber through ExecuteAgent.
 */
private fun smsVerifyResponseHandler(
    context: Context,
    repository: ClientRepository,
    scope: CoroutineScope,
    number: String
) : Response.Listener<String> {

    return Response.Listener<String> { response ->
        Timber.i("VOLLEY %s", response!!)

        val smsVerifyResponse: DefaultResponse? =
            response.toServerResponse(ServerResponseType.SMS_VERIFY) ?:
            response.toServerResponse(ServerResponseType.DEFAULT)

        /**
         * Guarantees that response has the right status before trying to update database.
         */
        if (smsVerifyResponse != null && smsVerifyResponse.status == "ok" && smsVerifyResponse is SMSVerifyResponse) {

            val responseNumber = TeleHelpers.normalizedNumber(smsVerifyResponse.number)

            // Makes sure that the server checked the sms verified status for the right number.
            if (responseNumber != number) {
                Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: SMS_VERIFY - Returned wrong number!")
                WorkStates.setState(WorkType.SMS_VERIFY_POST, WorkInfo.State.FAILED)
                return@Listener
            }

            /**
             * If number is verified, then create a SMS_VERIFY event.
             * If server says sms was sent, then create a SMS_SENT event.
             * Otherwise, create a SMS_REQUEST EVENT.
             *
             * The idea is that these events are created in order of precedence. Specifically,
             * if a number is verified, there's no need to create a SMS_SENT event. If the
             * server sent an sms, then there's no need to create a SMS_REQUEST event (which is
             * mainly used once max server sent is reached).
             *
             * Also, remember that the lambda might not be called in a suspend environment, so you
             * should launch another coroutine to do database work or launch another post request.
             */
            scope.launch(Dispatchers.IO) {
                val changeID = UUID.randomUUID().toString()
                val changeTime = Instant.now().toEpochMilli()

                /*
                Technically, verified and smsSent should not both be true, as the server should
                not have sent another SMS if the number was already verified. However, in the
                case that both are true, SMS_VERIFY takes precedence.
                 */
                val change = Change.create(
                    normalizedNumber = responseNumber,
                    safeAction = if (smsVerifyResponse.verified) {
                        SafeAction.SMS_VERIFY
                    } else if (smsVerifyResponse.smsSent){
                        SafeAction.SMS_SENT
                    } else {
                        SafeAction.SMS_REQUEST
                    }
                )

                repository.changeFromClient(
                    ChangeLog.create(
                        changeID = changeID,
                        changeTime = changeTime,
                        type = ChangeType.NON_CONTACT_UPDATE,
                        instanceNumber = TeleHelpers.getUserNumberStored(context)!!,
                        changeJson = change.toJson()
                    )
                )

                WorkStates.setState(WorkType.SMS_VERIFY_POST, WorkInfo.State.SUCCEEDED)
            }
        } else {
            WorkStates.setState(WorkType.SMS_VERIFY_POST, WorkInfo.State.FAILED)

            Timber.i("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY: ERROR WHEN SMS_VERIFY: ${smsVerifyResponse?.error}")
        }
    }
}

private val smsVerifyErrorHandler = Response.ErrorListener { error ->
    if (error.toString() != "null") {
        Timber.e("${TeleHelpers.DEBUG_LOG_TAG}: VOLLEY $error")
        WorkStates.setState(WorkType.SMS_VERIFY_POST, WorkInfo.State.FAILED)
    }
}
