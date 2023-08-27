package com.telefender.phone.gui.fragments.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.telefender.phone.R
import kotlin.system.exitProcess


class PrivacyDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)
            builder.setMessage(getString(R.string.privacy_dialog_text))
                .setNeutralButton("Review again") { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton("Exit app") { dialog, _ ->
                    // Exits app
                    exitProcess(0)
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}