package com.telefender.phone.gui.fragments.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.telefender.phone.R
import com.telefender.phone.databinding.DialogDoNotDisturbBinding
import com.telefender.phone.permissions.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers


class DoNotDisturbDialog : DialogFragment() {

    private var _binding: DialogDoNotDisturbBinding? = null
    private val binding get() = _binding!!

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = DialogDoNotDisturbBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.disturbDialogButton.setOnClickListener {
            Permissions.doNotDisturbPermission(requireActivity())
            dismiss()
        }

        binding.disturbCloseButton.setOnClickListener {
            // Dismiss the dialog when the close button is clicked.
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()

        // Sets dialog width to actually match the parent fragment (XML doesn't always work).
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getTheme(): Int {
        return R.style.TransparentDialog
    }
}