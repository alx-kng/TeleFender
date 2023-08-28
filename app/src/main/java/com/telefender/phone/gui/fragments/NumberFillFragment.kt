package com.telefender.phone.gui.fragments

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
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.model.VerificationViewModel
import com.telefender.phone.gui.model.VerificationViewModelFactory
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.SharedPreferenceHelpers
import com.telefender.phone.misc_helpers.TeleHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


class NumberFillFragment : Fragment() {

    private var _binding: FragmentNumberFillBinding? = null
    private val binding get() = _binding!!
    private val verificationViewModel : VerificationViewModel by activityViewModels {
        VerificationViewModelFactory(requireActivity().application)
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentNumberFillBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAppBar()
        hideBottomNavigation()

        // Set to previous value if it exists.
        binding.numberFillEdit.setText(verificationViewModel.manualInstanceNumber)

        binding.numberFillCard.setOnClickListener {
            val inputNumber = binding.numberFillEdit.text.toString()

            if (inputNumber.trim() != "") {
                setLoading()

                verificationViewModel.setManualInstanceNumber(number = inputNumber)
                val normalizedInstanceNumber = TeleHelpers.normalizedNumber(inputNumber)

                SharedPreferenceHelpers.setInstanceNumber(
                    context = requireContext(),
                    instanceNumber = normalizedInstanceNumber
                )

                scope.launch {
                    val success = RequestWrappers.initialPost(
                        context = requireContext(),
                        scope = scope,
                        instanceNumber = normalizedInstanceNumber
                    )

                    withContext(Dispatchers.Main) {
                        if (success) {
                            setDone()

                            val action = NumberFillFragmentDirections.actionNumberFillFragmentToVerificationFragment()
                            findNavController().navigate(action)
                        } else {
                            Toast.makeText(activity, "Oops! Something went wrong. Please try again!", Toast.LENGTH_SHORT).show()

                            setDone()
                        }
                    }
                }
            } else {
                Toast.makeText(activity, "Please enter a valid number!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoading() {
        binding.numberFillProgressBar.visibility = View.VISIBLE
        binding.numberFillCardText.setTextColor(ContextCompat.getColor(requireContext(), R.color.disabled_grey))
    }

    private fun setDone() {
        binding.numberFillProgressBar.visibility = View.GONE
        binding.numberFillCardText.setTextColor(ContextCompat.getColor(requireContext(), R.color.icon_white))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupAppBar() {
        if (activity is MainActivity) {
            Timber.i("$DBL: NumberFillFragment - setupAppBar()!")

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
