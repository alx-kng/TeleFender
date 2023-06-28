package com.telefender.phone.notifications


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import com.telefender.phone.R
import com.telefender.phone.call_related.AudioHelpers
import com.telefender.phone.call_related.CallManager
import com.telefender.phone.misc_helpers.DBL
import timber.log.Timber

class ActiveCallNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (val action = intent?.getStringExtra("action")) {
            ActiveCallNotificationService.SPEAKER_ACTION -> AudioHelpers.toggleSpeaker(context)
            ActiveCallNotificationService.MUTE_ACTION -> AudioHelpers.toggleMute(context)
            ActiveCallNotificationService.HANGUP_ACTION -> CallManager.hangup()
            else -> Timber.e("$DBL: ActiveCallNotificationReceiver - %s",
                "No action match! action = $action")
        }
    }
}