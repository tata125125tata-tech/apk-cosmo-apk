package com.cosmogamestore.app.core.downloader

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.Locale

/**
 * Custom WebViewClient that intercepts APK downloads, tracks loading state,
 * handles external schemes, and injects seamless download event hooks.
 */
open class CosmoWebViewClient(
    private val onDownloadRequested: (url: String) -> Unit,
    private val onPageLoadingChanged: ((isLoading: Boolean) -> Unit)? = null,
    private val onSearchQueryDetected: ((query: String) -> Unit)? = null,
    private val onErrorReceived: ((errorCode: Int, description: String) -> Unit)? = null
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request == null || request.url == null) return false
        val url = request.url.toString()
        return handleUrl(view, url)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return false
        return handleUrl(view, url)
    }

    private fun handleUrl(view: WebView?, url: String): Boolean {
        // Intercept direct APK and binary download links
        if (isDownloadUrl(url)) {
            Log.d(TAG, "Intercepted download link navigation: $url")
            onDownloadRequested(url)
            return true
        }

        // Intercept search queries in the URL (e.g. ?q=query or ?search=query)
        checkAndExtractSearch(url)

        // Delegate external protocols (market://, intent://, mailto://, etc.)
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            Log.d(TAG, "Non-HTTP protocol encountered: $url")
            return false
        }

        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageLoadingChanged?.invoke(true)
        if (url != null) {
            checkAndExtractSearch(url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageLoadingChanged?.invoke(false)

        // Inject in-page JavaScript hook to catch button clicks that initiate APK downloads
        injectDownloadAndSearchInterceptor(view)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request != null && request.isForMainFrame) {
            onPageLoadingChanged?.invoke(false)
            val description = error?.description?.toString() ?: "Network error"
            val errorCode = error?.errorCode ?: -1
            onErrorReceived?.invoke(errorCode, description)
        }
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        Log.e(TAG, "WebView render process exited: didCrash=${detail?.didCrash()}")
        return true
    }

    private fun checkAndExtractSearch(url: String) {
        try {
            val uri = Uri.parse(url)
            val query = uri.getQueryParameter("q")
                ?: uri.getQueryParameter("search")
                ?: uri.getQueryParameter("query")
            if (!query.isNullOrBlank()) {
                onSearchQueryDetected?.invoke(query.trim())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse search parameter: $url", e)
        }
    }

    private fun injectDownloadAndSearchInterceptor(view: WebView?) {
        val script = """
            (function() {
                if (window.__cosmoInterceptorAttached) return;
                window.__cosmoInterceptorAttached = true;
                
                document.addEventListener('click', function(e) {
                    var el = e.target;
                    while (el && el.tagName !== 'A' && el.tagName !== 'BUTTON') { 
                        el = el.parentElement; 
                    }
                    if (el && el.tagName === 'A' && el.href) {
                        var h = el.href.toLowerCase();
                        if (h.endsWith('.apk') || h.indexOf('.apk?') !== -1 || h.indexOf('/download/') !== -1) {
                            e.preventDefault();
                            if (window.CosmoSearchBridge && window.CosmoSearchBridge.onDownloadTriggered) {
                                window.CosmoSearchBridge.onDownloadTriggered(el.href);
                            }
                        }
                    }
                }, true);
            })();
        """.trimIndent()

        view?.evaluateJavascript(script, null)
    }

    companion object {
        private const val TAG = "CosmoWebViewClient"

        fun isDownloadUrl(url: String): Boolean {
            val lower = url.lowercase(Locale.ROOT)
            return lower.endsWith(".apk") ||
                    lower.contains(".apk?") ||
                    lower.contains(".apk#") ||
                    (lower.contains("/download/") && lower.contains(".apk")) ||
                    lower.endsWith(".zip")
        }
    }
}
