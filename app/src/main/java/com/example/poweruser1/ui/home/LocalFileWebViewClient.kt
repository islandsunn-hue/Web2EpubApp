package com.web2epub1.poweruser1.ui.home

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection

class LocalFileWebViewClient(
    private val onUrlLoaded: (String) -> Unit
) : WebViewClient() {

    companion object {
        private const val TAG = "LocalFileWebViewClient"
        const val LOCAL_SCHEME = "https://local.file"
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Log.d(TAG, "onPageFinished: $url")
        if (url != null) {
            onUrlLoaded(url)
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null

        if (url.startsWith(LOCAL_SCHEME) || url.startsWith("file:/")) {
            Log.d(TAG, "Intercepting request: $url")
            
            val path = try {
                val rawPath = if (url.startsWith("file:/")) {
                    request.url.path
                } else {
                    url.removePrefix(LOCAL_SCHEME).substringBefore('?').substringBefore('#')
                }
                // Use Uri.decode for file paths instead of URLDecoder.decode to handle + literally
                val decoded = android.net.Uri.decode(rawPath)
                if (!decoded.startsWith("/")) "/$decoded" else decoded
            } catch (e: Exception) {
                Log.e(TAG, "Path resolution error", e)
                null
            } ?: return null

            var file = File(path)
            
            // Alias Resolver: If the WebView is asking for .mht (due to our trick) but only .mhtml exists, resolve it.
            if (!file.exists() && path.endsWith(".mht", ignoreCase = true)) {
                val mhtmlFile = File(path + "l") // Try .mhtml
                if (mhtmlFile.exists()) {
                    file = mhtmlFile
                    Log.d(TAG, "Resolved alias: .mht -> .mhtml")
                }
            }

            if (file.exists() && file.isFile && file.canRead()) {
                val mimeType = getMimeType(file.name)
                val encoding = getEncoding(mimeType)
                Log.d(TAG, "Serving physical file: ${file.absolutePath} as $mimeType")
                
                return try {
                    val response = WebResourceResponse(mimeType, encoding, FileInputStream(file))
                    val headers = mutableMapOf(
                        "Access-Control-Allow-Origin" to "*",
                        "Cache-Control" to "no-cache",
                        "X-Content-Type-Options" to "nosniff"
                    )
                    
                    if (mimeType == "message/rfc822" || mimeType == "multipart/related") {
                        headers["Content-Disposition"] = "inline; filename=\"${file.name}\""
                    }
                    
                    response.responseHeaders = headers
                    response
                } catch (e: Exception) {
                    Log.e(TAG, "Stream error", e)
                    null
                }
            } else {
                Log.w(TAG, "File not found or not readable: $path")
            }
        }

        return super.shouldInterceptRequest(view, request)
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html", "htm" -> "text/html"
            "mhtml", "mht", "webarchive" -> "message/rfc822" // Primary native trigger for archives
            "css" -> "text/css"
            "js" -> "application/javascript"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
        }
    }

    private fun getEncoding(mimeType: String): String? {
        if (mimeType == "message/rfc822" || mimeType == "multipart/related") return null
        return if (mimeType.startsWith("text/") || mimeType.contains("javascript") || mimeType.contains("json") || mimeType.contains("xml")) {
            "UTF-8"
        } else {
            null
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        return false
    }
}
