package com.example.poweruser1.ui.home

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection

class LocalFileWebViewClient(
    private val cacheDir: File,
    private val onUrlLoaded: (String) -> Unit
) : WebViewClient() {

    companion object {
        const val LOCAL_SCHEME = "https://local.file/"
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url != null) {
            onUrlLoaded(url)
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null

        if (url.startsWith(LOCAL_SCHEME)) {
            val fileName = url.removePrefix(LOCAL_SCHEME).substringBefore('?').substringBefore('#').removePrefix("/")
            if (fileName.isEmpty()) return null

            try {
                val file = File(cacheDir, fileName).canonicalFile
                val canonicalCacheDir = cacheDir.canonicalFile

                if (!file.path.startsWith(canonicalCacheDir.path)) {
                    return WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", null, null)
                }

                if (file.exists() && file.isFile) {
                    val mimeType = URLConnection.guessContentTypeFromName(fileName) ?: "text/html"
                    return WebResourceResponse(mimeType, "UTF-8", FileInputStream(file))
                }
            } catch (e: Exception) {
                return WebResourceResponse("text/plain", "UTF-8", 500, "Internal Error", null, null)
            }
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        return false // Allow loading local.file and external links (handled by default browser if not intercepted)
    }
}
