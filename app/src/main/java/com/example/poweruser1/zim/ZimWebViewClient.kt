package com.example.poweruser1.zim

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class ZimWebViewClient(
    private val wrapper: KiwixZimWrapper?,
    private val onUrlLoaded: (String) -> Unit,
) : WebViewClient() {

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url != null) {
            onUrlLoaded(url)
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (url.startsWith("zim://") || url.startsWith("https://zim.local/") || url.startsWith("http://zim.local/")) {
            return false
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false
        }
        return false
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val uri = request?.url ?: return super.shouldInterceptRequest(view, request)
        val rawUrl = uri.toString()

        if (wrapper == null) {
            return super.shouldInterceptRequest(view, request)
        }

        val pathWithoutDomain = when {
            rawUrl.startsWith("zim://") -> rawUrl.substring(6)
            rawUrl.startsWith("https://zim.local/") -> rawUrl.substring(18)
            rawUrl.startsWith("http://zim.local/") -> rawUrl.substring(17)
            rawUrl.startsWith("https://zim.local") -> rawUrl.substring(17)
            rawUrl.startsWith("http://zim.local") -> rawUrl.substring(16)
            else -> uri.path ?: ""
        }

        val rawPath = pathWithoutDomain
            .substringBefore('?')
            .substringBefore('#')
            .removePrefix("/")

        if (rawPath.isBlank()) {
            val mainEntry = wrapper.getMainPageEntry() ?: wrapper.getRandomArticle()
            if (mainEntry != null) {
                var targetPath = mainEntry.path
                if (mainEntry.isRedirect) {
                    try {
                        targetPath = mainEntry.redirectEntry.path
                    } catch (_: Exception) {}
                }
                val headers = mapOf("Location" to "https://zim.local/$targetPath")
                return WebResourceResponse("text/html", "UTF-8", 302, "Found", headers, ByteArrayInputStream(ByteArray(0)))
            }
            return super.shouldInterceptRequest(view, request)
        }

        val entry = wrapper.getEntryForPath(rawPath)

        if (entry != null) {
            if (entry.isRedirect) {
                try {
                    val redirectEntry = entry.redirectEntry
                    val targetPath = redirectEntry.path
                    val headers = mapOf("Location" to "https://zim.local/$targetPath")
                    return WebResourceResponse("text/html", "UTF-8", 302, "Found", headers, ByteArrayInputStream(ByteArray(0)))
                } catch (_: Exception) {}
            }

            try {
                val item = entry.getItem(true)
                val blob = item.data
                val data = blob.data

                if (data != null) {
                    val rawMimeType = item.mimetype ?: ""
                    val mimeType = getMimeType(rawMimeType, rawPath)
                    val encoding = getEncodingForMimeType(mimeType)
                    return WebResourceResponse(mimeType, encoding, ByteArrayInputStream(data))
                }
            } catch (_: Exception) {}
        }

        return super.shouldInterceptRequest(view, request)
    }

    private fun getMimeType(rawMimeType: String, path: String): String {
        val lowerMime = rawMimeType.lowercase()
        val cleanMime = lowerMime.substringBefore(';').trim()
        if (cleanMime.isNotBlank() && cleanMime != "application/octet-stream") {
            return cleanMime
        }

        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "eot" -> "application/vnd.ms-fontobject"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "ogg" -> "audio/ogg"
            "webm" -> "video/webm"
            else -> if (cleanMime.isNotBlank()) cleanMime else "text/html"
        }
    }

    private fun getEncodingForMimeType(mimeType: String): String? {
        val lower = mimeType.lowercase()
        return when {
            lower.startsWith("text/") -> "UTF-8"
            lower.contains("javascript") || lower.contains("json") || lower.contains("xml") -> "UTF-8"
            else -> null
        }
    }
}
