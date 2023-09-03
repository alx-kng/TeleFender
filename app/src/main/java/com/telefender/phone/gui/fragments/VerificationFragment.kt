package com.telefender.phone.gui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.telefender.phone.R
import com.telefender.phone.data.server_related.RequestWrappers
import com.telefender.phone.databinding.FragmentVerificationBinding
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.model.VerificationViewModel
import com.telefender.phone.gui.model.VerificationViewModelFactory
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.SharedPreferenceHelpers
import com.telefender.phone.misc_helpers.UserSetupStage
import kotlinx.coroutines.*
import timber.log.Timber


class VerificationFragment : Fragment() {

    private var _binding: FragmentVerificationBinding? = null
    private val binding get() = _binding!!
    private val verificationViewModel : VerificationViewModel by activityViewModels {
        VerificationViewModelFactory(requireActivity().application)
    }

    private val postScope = CoroutineScope(Dispatchers.IO)
    private val resendScope = CoroutineScope(Dispatchers.IO)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAppBar()
        hideBottomNavigation()

        binding.apply {
            viewModel = verificationViewModel
            lifecycleOwner = viewLifecycleOwner
        }

        showTimerResend()

        resendScope.launch {
            verificationViewModel.startCountDown()

            withContext(Dispatchers.Main) {
                showActiveResend()
            }
        }

        binding.verificationCard.setOnClickListener {
            val inputCode = binding.verificationEdit.text.toString().toIntOrNull()

            if (inputCode != null) {
                setLoading()

                postScope.launch {
                    RequestWrappers.verifyPost(
                        context = requireContext(),
                        scope = postScope,
                        otp = inputCode
                    )

                    verifyPostOnResult()
                }
            } else {
                Toast.makeText(activity, "Please enter a valid OTP!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.verificationResendCard.setOnClickListener {
            showTimerResend()

            resendScope.launch {
                verificationViewModel.startCountDown()

                withContext(Dispatchers.Main) {
                    showActiveResend()
                }
            }

            resendScope.launch {
                val normalizedInstanceNumber = SharedPreferenceHelpers.getInstanceNumber(requireContext())

                if (normalizedInstanceNumber != null) {
                    val success = RequestWrappers.initialPost(
                        context = requireContext(),
                        scope = resendScope,
                        instanceNumber = normalizedInstanceNumber
                    )

                    withContext(Dispatchers.Main) {
                        if (!success) {
                            Toast.makeText(activity, "Oops! Something went wrong. Please try again!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Oops! It seems like you didn't input a number! " +
                            "Please go back to the previous page and try again!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // TODO: FIX THIS FOR PERMISSION FRAGMENT
    private suspend fun verifyPostOnResult() {
        withContext(Dispatchers.Main) {
            val clientKey = SharedPreferenceHelpers.getClientKey(requireContext())
            if (clientKey != null) {
                SharedPreferenceHelpers.setUserSetupStage(
                    context = requireContext(),
                    userSetupStage = UserSetupStage.PERMISSIONS
                )

                val act = requireActivity()
                if (act is MainActivity) {
                    resendScope.cancel()

                    val action = VerificationFragmentDirections.actionVerificationFragmentToPermissionFragment()
                    findNavController().navigate(action)
                }
            } else {
                Toast.makeText(activity, "Oops! Something went wrong. Please try again!", Toast.LENGTH_SHORT).show()

                setDone()
            }
        }
    }

    private fun setLoading() {
        binding.verificationProgressBar.visibility = View.VISIBLE
        binding.verificationCard.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.grey))
        binding.verificationCardText.setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_200))
    }

    private fun setDone() {
        binding.verificationProgressBar.visibility = View.GONE
        binding.verificationCard.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.purple_200))
        binding.verificationCardText.setTextColor(ContextCompat.getColor(requireContext(), R.color.icon_white))
    }

    /**
     * NOTE: We use nullable [_binding] in case of quick switching between screens, which may lead
     * [_binding] to become null.
     */
    private fun showActiveResend() {
        _binding?.verificationResendCard?.isClickable = true
        _binding?.verificationResendCard?.isFocusable = true
        _binding?.verificationResendCard?.visibility = View.VISIBLE
        _binding?.verificationResendTimer?.visibility = View.GONE
        _binding?.verificationResendIcon?.iconTint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_white))
        _binding?.verificationResendText?.setTextColor(ContextCompat.getColor(requireContext(), R.color.icon_white))
    }

    /**
     * NOTE: We use nullable [_binding] in case of quick switching between screens, which may lead
     * [_binding] to become null.
     */
    private fun showTimerResend() {
        _binding?.verificationResendCard?.isClickable = false
        _binding?.verificationResendCard?.isFocusable = false
        _binding?.verificationResendCard?.visibility = View.VISIBLE
        _binding?.verificationResendTimer?.visibility = View.VISIBLE
        _binding?.verificationResendIcon?.iconTint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.disabled_grey))
        _binding?.verificationResendText?.setTextColor(ContextCompat.getColor(requireContext(), R.color.disabled_grey))
    }

    private fun setupAppBar() {
        if (activity is MainActivity) {
            Timber.i("$DBL: VerificationFragment - setupAppBar()!")

            val act = activity as MainActivity

            // Cleans old app bar before setting up new app bar
            act.revertAppBar()

            // New app bar stuff
            act.setTitle(getString(R.string.verification_title))
            act.displayUpButton(true)
            act.displayMoreMenu(false)

            // Actually show app bar
            act.displayAppBar(true)
        }
    }

    private fun hideBottomNavigation() {
        if (activity is MainActivity) {
            val act = (activity as MainActivity)
            act.displayBottomNavigation(false)
        }
    }
}
