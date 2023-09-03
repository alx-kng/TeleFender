package com.telefender.phone.gui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.telefender.phone.R
import com.telefender.phone.databinding.FragmentPermissionBinding
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.model.PermissionViewModel
import com.telefender.phone.gui.model.PermissionViewModelFactory
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.SharedPreferenceHelpers
import com.telefender.phone.misc_helpers.UserSetupStage
import com.telefender.phone.permissions.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber


class PermissionFragment : Fragment() {

    private var _binding: FragmentPermissionBinding? = null
    private val binding get() = _binding!!
    private val permissionViewModel : PermissionViewModel by activityViewModels {
        PermissionViewModelFactory(requireActivity().application)
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAppBar()
        hideBottomNavigation()
        setupButtons()

        permissionViewModel.startCheckingDoNotDisturb()

        binding.permissionDialerCard.setOnClickListener {
            if (activity is MainActivity) {
                val act = activity as MainActivity

                act.requestDefaultDialer()
            }
        }

        binding.permissionDisturbCard.setOnClickListener {
            if (activity is MainActivity) {
                val act = activity as MainActivity

                Permissions.doNotDisturbPermission(act)
            }
        }

        binding.permissionContinueCard.setOnClickListener {
            if (activity is MainActivity) {
                val act = activity as MainActivity

                SharedPreferenceHelpers.setUserSetupStage(
                    context = requireContext(),
                    userSetupStage = UserSetupStage.COMPLETE
                )

                act.completedSetupOnCreate()
                act.completedSetupOnStart()

                val action = PermissionFragmentDirections.actionPermissionFragmentToDialerFragment()
                findNavController().navigate(action)            }
        }

        permissionViewModel.isDefaultDialer.observe(viewLifecycleOwner) {
            defaultDialerUIChange(isDefaultDialer = it)
        }

        permissionViewModel.hasDoNotDisturb.observe(viewLifecycleOwner) {
            doNotDisturbUIChange(hasDoNotDisturb = it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        permissionViewModel.stopCheckingDoNotDisturb()
        _binding = null
    }

    private fun setupButtons() {
        binding.permissionDialerCheck.setIconResource(
            if (permissionViewModel.isDefaultDialerDirect) {
                R.drawable.ic_baseline_check_box_24
            } else {
                R.drawable.ic_baseline_check_box_outline_blank_24
            }
        )

        binding.permissionDisturbCheck.setIconResource(
            if (permissionViewModel.hasDoNotDisturbDirect) {
                R.drawable.ic_baseline_check_box_24
            } else {
                R.drawable.ic_baseline_check_box_outline_blank_24
            }
        )

        updateContinueEnabled()
    }

    private fun defaultDialerUIChange(isDefaultDialer: Boolean) {
        Timber.e("$DBL: UPDATE UI - isDialer = $isDefaultDialer")

        binding.permissionDialerCheck.setIconResource(
            if (isDefaultDialer) {
                R.drawable.ic_baseline_check_box_24
            } else {
                R.drawable.ic_baseline_check_box_outline_blank_24
            }
        )

        updateContinueEnabled()
    }

    private fun doNotDisturbUIChange(hasDoNotDisturb: Boolean) {
        binding.permissionDisturbCheck.setIconResource(
            if (hasDoNotDisturb) {
                R.drawable.ic_baseline_check_box_24
            } else {
                R.drawable.ic_baseline_check_box_outline_blank_24
            }
        )

        updateContinueEnabled()
    }

    private fun setupAppBar() {
        if (activity is MainActivity) {
            Timber.i("$DBL: PermissionFragment - setupAppBar()!")

            val act = activity as MainActivity

            // Cleans old app bar before setting up new app bar
            act.revertAppBar()

            // New app bar stuff
            act.setTitle(getString(R.string.permission_title))
            act.displayMoreMenu(false)

            // Actually show app bar
            act.displayAppBar(true)
        }
    }

    private fun updateContinueEnabled() {
        val validSetup = permissionViewModel.isDefaultDialerDirect && permissionViewModel.hasDoNotDisturbDirect
        binding.permissionContinueCard.isClickable = validSetup
        binding.permissionContinueCard.isFocusable = validSetup
        binding.permissionContinueText.setTextColor(
            if (validSetup) {
                ContextCompat.getColor(requireContext(), R.color.icon_white)
            } else {
                ContextCompat.getColor(requireContext(), R.color.purple_200)
            }
        )

        binding.permissionContinueCard.backgroundTintList = if (validSetup) {
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.purple_200))
        } else {
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.grey))
        }
    }

    private fun hideBottomNavigation() {
        if (activity is MainActivity) {
            val act = (activity as MainActivity)
            act.displayBottomNavigation(false)
        }
    }
}
