package com.example.poweruser1.zim

import android.net.Uri
import android.webkit.WebResourceRequest
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ZimWebViewClientTest {

    private class MockRequest(private val urlString: String) : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(urlString)
        override fun isForMainFrame(): Boolean = true
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = false
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): Map<String, String> = Collections.emptyMap()
    }

    @Test
    fun testNullWrapperReturnsNull() {
        val client = ZimWebViewClient(null) {}
        val response = client.shouldInterceptRequest(null, MockRequest("https://zim.local/A/Test"))
        assertNull(response)
    }

    @Test
    fun testEmptyPathReturnsNullWhenWrapperNull() {
        val client = ZimWebViewClient(null) {}
        val response = client.shouldInterceptRequest(null, MockRequest("https://zim.local/"))
        assertNull(response)
    }
}
