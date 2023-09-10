package com.telefender.phone.gui.fragments.web_views

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient


class CustomWebViewClient : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        request?.url?.let {
            // If you want to check for a specific host you can do so here
            // For example: if (Uri.parse(it).host == "www.example.com")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.toString()))
            view?.context?.startActivity(intent)
            return true
        }

        return false
    }
}
