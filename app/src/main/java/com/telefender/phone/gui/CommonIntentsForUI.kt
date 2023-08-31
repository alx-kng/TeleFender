package com.telefender.phone.gui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import com.telefender.phone.misc_helpers.DBL
import timber.log.Timber


object CommonIntentsForUI {

    fun sendSMSIntent(
        activity: Activity,
        number: String
    ) {
        val smsIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("smsto:")  // This will allow the user to choose an SMS app
            putExtra("address", number)  // Phone number to send to
            putExtra("sms_body", "")  // Optional: predefined message text
        }

        try {
            activity.startActivity(smsIntent)
        } catch (e: Exception) {
            // Error might occur if no apps support this intent.
            Timber.e("$DBL: smsIntent() - Error = ${e.message}")
            Toast.makeText(activity, "No SMS app found!", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendEmailIntent(
        activity: Activity,
        email: String
    ) {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            // Only email apps should handle this intent
            data = Uri.parse("mailto:")

            // Add email recipients, subject, and body
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, "")
            putExtra(Intent.EXTRA_TEXT, "")
        }

        // Check if an app exists to handle the intent
        val packageManager = activity.packageManager
        if (emailIntent.resolveActivity(packageManager) != null) {
            activity.startActivity(emailIntent)
        } else {
            // No email app found
            Toast.makeText(activity, "No email app found!", Toast.LENGTH_SHORT).show()
        }
    }

    fun openLink(
        activity: Activity,
        url: String
    ) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

        // Ensure that there's a browser to handle the intent
        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(intent)
        } else {
            // Handle the error. For instance, show a Toast that there's no browser.
            Toast.makeText(activity, "No browser available to open the link.", Toast.LENGTH_SHORT).show()
        }
    }
}