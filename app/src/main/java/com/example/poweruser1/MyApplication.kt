package com.example.poweruser1

import android.app.Application
import android.os.Build
import android.util.Log
import android.webkit.WebView
import org.kiwix.libkiwix.JNIKiwix

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WebView.setDataDirectorySuffix("poweruser_webview")
            } catch (e: Throwable) {
                Log.e("MyApplication", "Error setting WebView data directory suffix", e)
            }
        }

        if (Build.FINGERPRINT != "robolectric") {
            try {
                JNIKiwix(this)
            } catch (e: Throwable) {
                Log.e("MyApplication", "JNIKiwix failed with ReLinker, attempting System.loadLibrary fallback", e)
                try {
                    System.loadLibrary("c++_shared")
                    System.loadLibrary("zim")
                    System.loadLibrary("kiwix")
                } catch (e2: Throwable) {
                    Log.e("MyApplication", "System.loadLibrary fallback failed", e2)
                }
            }
        }
    }
}
