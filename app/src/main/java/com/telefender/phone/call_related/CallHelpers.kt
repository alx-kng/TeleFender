package com.telefender.phone.call_related

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.net.toUri
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import timber.log.Timber

object CallHelpers {

    /**
     * Initiates outgoing call to [number].
     */
    @SuppressLint("MissingPermission")
    fun makeCall(context: Context, number: String?) {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val uri = "tel:${number}".toUri()
            telecomManager.placeCall(uri, null)
            Timber.i("$DBL: OUTGOING CALL TO $number")
        } catch (e: Exception) {
            Timber.e("$DBL: OUTGOING CALL FAILED!")
        }
    }
}