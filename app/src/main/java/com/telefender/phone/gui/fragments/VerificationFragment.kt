package com.telefender.phone.gui.fragments

import android.content.SharedPreferences
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
import com.telefender.phone.databinding.FragmentNumberFillBinding
import com.telefender.phone.databinding.FragmentVerificationBinding
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.model.VerificationViewModel
import com.telefender.phone.gui.model.VerificationViewModelFactory
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.SharedPreferenceHelpers
import com.telefender.phone.misc_helpers.SharedPreferenceKey
import kotlinx.coroutines.*
import timber.log.Timber


class VerificationFragment : Fragment() {

    private var _binding: FragmentVerificationBinding? = null
    private val binding get() = _binding!!
    private val verificationViewModel : VerificationViewModel by activityViewModels {
        VerificationViewModelFactory(requireActivity().application)
    }

    private val scope = CoroutineScope(Dispatchers.IO)

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

        binding.verificationCard.setOnClickListener {
            val inputCode = binding.verificationEdit.text.toString().toIntOrNull()

            if (inputCode != null) {
                setLoading()

                scope.launch {
                    RequestWrappers.verifyPost(
                        context = requireContext(),
                        scope = scope,
                        otp = inputCode
                    )

                    withContext(Dispatchers.Main) {
                        val clientKey = SharedPreferenceHelpers.getClientKey(requireContext())
                        if (clientKey != null) {
                            SharedPreferenceHelpers.setUserReady(requireContext(), userReady = true)

                            val act = requireActivity()
                            if (act is MainActivity) {
                                act.userReadyOnCreateSetup()
                                act.userReadyOnStartSetup()

                                setDone()

                                val action = VerificationFragmentDirections.actionVerificationFragmentToDialerFragment()
                                findNavController().navigate(action)
                            }
                        } else {
                            setDone()
                        }
                    }
                }
            } else {
                Toast.makeText(activity, "Please enter a valid OTP!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setLoading() {
        binding.verificationProgressBar.visibility = View.VISIBLE
        binding.verificationCardText.setTextColor(ContextCompat.getColor(requireContext(), R.color.disabled_grey))
    }

    private fun setDone() {
        binding.verificationProgressBar.visibility = View.GONE
        binding.verificationCardText.setTextColor(ContextCompat.getColor(requireContext(), R.color.icon_white))
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
