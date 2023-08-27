package com.telefender.phone.gui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.telefender.phone.R
import com.telefender.phone.databinding.FragmentPrivacyPolicyBinding
import com.telefender.phone.gui.MainActivity
import com.telefender.phone.gui.fragments.dialogs.PrivacyDialogFragment
import com.telefender.phone.misc_helpers.DBL
import timber.log.Timber


class PrivacyPolicyFragment : Fragment() {

    private var _binding: FragmentPrivacyPolicyBinding? = null
    private val binding get() = _binding!!

    private var webView: WebView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPrivacyPolicyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAppBar()
        hideBottomNavigation()

        binding.privacyAccept.setOnClickListener {
            val action = PrivacyPolicyFragmentDirections.actionPrivacyPolicyFragmentToNumberFillFragment()
            findNavController().navigate(action)
        }

        binding.privacyReject.setOnClickListener {
            PrivacyDialogFragment().show(parentFragmentManager, "privacyDialog")
        }

        // Initialize WebView
        webView = view.findViewById(R.id.webview)

        webView?.apply {
            // Set WebView settings
            settings.javaScriptEnabled = true

            // Optional: Set WebViewClient to ensure URLs open within the WebView
            webViewClient = WebViewClient()

            // Load a URL
            loadUrl("https://test.scribblychat.com/privacy.html")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null

        // Step 1: Release the WebView's resources and clear its internal state.
        webView?.apply {
            clearHistory()
            clearCache(true)
            loadUrl("about:blank")
            onPause()
            removeAllViews()
        }

        // Step 2: Set WebViewClient and WebChromeClient to null / empty implementations.
        webView?.apply {
            webViewClient = WebViewClient()
            webChromeClient = null
        }

        // Now, nullify your reference to the WebView itself.
        webView = null
    }

    private fun setupAppBar() {
        if (activity is MainActivity) {
            Timber.i("$DBL: PrivacyPolicyFragment - setupAppBar()!")

            val act = activity as MainActivity

            // Cleans old app bar before setting up new app bar
            act.revertAppBar()

            // New app bar stuff
            act.setTitle(getString(R.string.privacy_policy_title))
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
