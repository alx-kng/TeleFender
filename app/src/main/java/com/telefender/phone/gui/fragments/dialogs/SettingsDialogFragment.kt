package com.telefender.phone.gui.fragments.dialogs

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.telefender.phone.App
import com.telefender.phone.R
import com.telefender.phone.call_related.HandleMode
import com.telefender.phone.databinding.DialogSettingsBinding
import com.telefender.phone.gui.CommonIntentsForUI
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


class SettingsDialogFragment : DialogFragment() {

    private var _binding: DialogSettingsBinding? = null
    private val binding get() = _binding!!

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = DialogSettingsBinding.inflate(inflater, container, false)
        return binding.root    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scope.launch {
            updateHandleModeColors()
        }

        binding.handleAllowOption.setOnClickListener {
            scope.launch {
                updateHandleMode(HandleMode.ALLOW_MODE)
                updateHandleModeColors()
            }
        }

        binding.handleSilenceOption.setOnClickListener {
            scope.launch {
                updateHandleMode(HandleMode.SILENCE_MODE)
                updateHandleModeColors()
            }
        }

        binding.handleBlockOption.setOnClickListener {
            scope.launch {
                Timber.e("$DBL: SET BLOCK MODE!")
                updateHandleMode(HandleMode.BLOCK_MODE)
                updateHandleModeColors()
            }
        }

        binding.reportIssueButton.setOnClickListener {
            CommonIntentsForUI.openLink(requireActivity(), "https://telefender.com/contactus.html")
        }

        binding.settingCloseButton.setOnClickListener {
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

    private suspend fun updateHandleMode(newHandleMode: HandleMode) {
        val repository = (requireContext().applicationContext as App).repository
        repository.updateStoredMap(handleMode = newHandleMode)
    }

    private suspend fun updateHandleModeColors() {
        val currentMode = TeleHelpers.currentHandleMode(requireContext())

        Timber.e("$DBL: Handle Mode = $currentMode")

        withContext(Dispatchers.Main) {
            when (currentMode) {
                HandleMode.ALLOW_MODE -> {
                    setOptionButtonSelected(binding.handleAllowOption)
                    setOptionButtonUnselected(binding.handleSilenceOption)
                    setOptionButtonUnselected(binding.handleBlockOption)
                }
                HandleMode.SILENCE_MODE -> {
                    setOptionButtonSelected(binding.handleSilenceOption)
                    setOptionButtonUnselected(binding.handleAllowOption)
                    setOptionButtonUnselected(binding.handleBlockOption)
                }
                HandleMode.BLOCK_MODE -> {
                    setOptionButtonSelected(binding.handleBlockOption)
                    setOptionButtonUnselected(binding.handleAllowOption)
                    setOptionButtonUnselected(binding.handleSilenceOption)
                }
            }
        }
    }

    private fun setOptionButtonSelected(button: MaterialButton) {
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.icon_white))
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.purple_200)
        )
    }

    private fun setOptionButtonUnselected(button: MaterialButton) {
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_200))
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.grey)
        )
    }
}