package com.web2epub1.poweruser1.ui.home

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.web2epub1.poweruser1.R
import com.web2epub1.poweruser1.databinding.FragmentHomeBinding
import com.web2epub1.poweruser1.zim.KiwixZimWrapper
import com.web2epub1.poweruser1.zim.ZimWebViewClient
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.kiwix.libzim.Entry
import java.io.File
import java.net.URLEncoder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.print.PrintAttributes
import android.print.PrintManager
import android.os.Environment
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.DocumentsContract
import android.content.ContentUris
import android.system.Os
import kotlin.coroutines.resume

class HomeFragment : Fragment(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "HomeFragment"
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var webView: WebView
    private var isZimMode = false
    private var currentZimWrapper: KiwixZimWrapper? = null
    private var lastModeInDrawer: Boolean? = null

    private val openZimLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        Log.d(TAG, "ZIM Picker result: $uri")
        uri?.let { handlePickedUri(it, "ZIM") }
    }

    private val openLocalFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        Log.d(TAG, "Local File Picker result: $uri")
        uri?.let { handlePickedUri(it, "HTML") }
    }

    private val saveHtmlLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        uri?.let { exportHtmlToUri(it) } ?: run { capturedHtmlForExport = null }
    }

    private val saveMhtmlLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-webarchive")) { uri ->
        uri?.let { exportMhtmlToUri(it) }
    }

    private val saveEpubLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/epub+zip")) { uri ->
        uri?.let { exportEpubToUri(it) } ?: run { capturedHtmlForExport = null }
    }

    private var capturedHtmlForExport: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        webView = binding.webview
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        binding.toolbar.inflateMenu(R.menu.browser_toolbar_menu)
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_tools -> {
                    updateMenuVisibility()
                    binding.drawerLayout.openDrawer(GravityCompat.START)
                    true
                }
                else -> false
            }
        }

        binding.navView.setNavigationItemSelectedListener(this)
        
        binding.drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                if (slideOffset > 0.1f) {
                    updateMenuVisibility()
                }
            }
            override fun onDrawerOpened(drawerView: View) { updateMenuVisibility() }
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        switchToInternetMode()
        webView.loadData("<html><body style='background:transparent;'></body></html>", "text/html", "UTF-8")

        fun loadUrlFromInput() {
            val input = binding.urlInput.text.toString().trim()
            if (input.isNotBlank()) {
                val url = if (android.util.Patterns.WEB_URL.matcher(input).matches()) {
                    if (!input.startsWith("https://") && !input.startsWith("http://")) {
                        "https://$input"
                    } else {
                        input
                    }
                } else {
                    val encodedQuery = URLEncoder.encode(input, "UTF-8")
                    "https://www.google.com/search?q=$encodedQuery"
                }
                webView.setBackgroundColor(android.graphics.Color.WHITE)
                webView.loadUrl(url)
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(binding.urlInput.windowToken, 0)
            }
        }

        binding.goButton.setOnClickListener {
            if (isZimMode) {
                val input = binding.urlInput.text.toString().trim()
                val wrapper = currentZimWrapper
                if (wrapper == null) {
                    Toast.makeText(requireContext(), "No ZIM file open.", Toast.LENGTH_SHORT).show()
                    requestOpenFile("ZIM")
                } else if (input.isNotBlank()) {
                    val directEntry = wrapper.getEntryForPath(input)
                    if (directEntry != null) {
                        loadZimArticle(directEntry)
                        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.hideSoftInputFromWindow(binding.urlInput.windowToken, 0)
                    } else {
                        showZimSearchDialog(initialQuery = input)
                    }
                } else {
                    showZimSearchDialog(initialQuery = "")
                }
            } else {
                loadUrlFromInput()
            }
        }

        binding.urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                binding.goButton.performClick()
                true
            } else {
                false
            }
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.urlInput.requestFocus()
        updateMenuVisibility()

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun requestOpenFile(type: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Files Access")
                    .setMessage("Please grant 'All Files Access' to enable file loading.")
                    .setPositiveButton("Grant") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${requireContext().packageName}")
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }
        }

        if (type == "ZIM") {
            openZimLauncher.launch(arrayOf("*/*"))
        } else {
            openLocalFileLauncher.launch(arrayOf("text/html", "application/x-webarchive", "multipart/related", "message/rfc822"))
        }
    }

    private fun handlePickedUri(uri: Uri, type: String) {
        val path = tryGetPathFromUri(uri)
        Log.d(TAG, "handlePickedUri: type=$type, uri=$uri, resolvedPath=$path")
        if (path != null && File(path).canRead()) {
            val file = File(path)
            if (type == "ZIM") {
                openZimByFile(file)
            } else {
                openLocalFileByPath(file)
            }
        } else {
            // Fallback for restricted paths: Use URI/FD directly
            if (type == "ZIM") {
                openZimByUri(uri)
            } else {
                openLocalFileByUri(uri)
            }
        }
    }

    private fun openLocalFileByUri(uri: Uri) {
        switchToInternetMode()
        webView.setBackgroundColor(android.graphics.Color.WHITE)
        webView.loadUrl(uri.toString())
        
        // Restore standard look
        binding.urlInput.setText("")
        binding.urlInput.hint = "Enter URL or Search"
        
        Toast.makeText(requireContext(), "Opened", Toast.LENGTH_SHORT).show()
    }

    private fun openLocalFileByPath(file: File) {
        if (!file.exists() || !file.canRead()) {
            openLocalFileByUri(Uri.fromFile(file))
            return
        }

        switchToInternetMode()
        
        var urlPath = file.absolutePath
        if (urlPath.endsWith(".mhtml", ignoreCase = true)) {
            urlPath = urlPath.substring(0, urlPath.length - 1)
        }
        
        val fileUrl = "file://$urlPath"
        Log.d(TAG, "Loading local file via file:// URL: $fileUrl")
        webView.setBackgroundColor(android.graphics.Color.WHITE)
        webView.loadUrl(fileUrl)
        
        // Ensure search bar is empty and hint is restored to default
        binding.urlInput.setText("")
        binding.urlInput.hint = "Enter URL or Search"
        
        Toast.makeText(requireContext(), "Opened: ${file.name}", Toast.LENGTH_SHORT).show()
    }

    private fun openZimByUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pfd = requireContext().contentResolver.openFileDescriptor(uri, "r")
                if (pfd == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to open system file handle.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.VISIBLE
                }

                val wrapper = KiwixZimWrapper(pfd)
                withContext(Dispatchers.Main) {
                    finalizeZimOpening(wrapper)
                    Toast.makeText(requireContext(), "Opened ZIM", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error opening ZIM via URI", e)
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error opening ZIM: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openZimByFile(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!file.exists() || !file.canRead()) {
                    withContext(Dispatchers.Main) {
                        openZimByUri(Uri.fromFile(file))
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.VISIBLE
                }

                val wrapper = KiwixZimWrapper(file)
                withContext(Dispatchers.Main) {
                    finalizeZimOpening(wrapper)
                    Toast.makeText(requireContext(), "Opened ZIM (Direct Path)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error opening ZIM file", e)
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error opening ZIM: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun finalizeZimOpening(wrapper: KiwixZimWrapper) {
        binding.loadingProgress.visibility = View.GONE
        currentZimWrapper?.close()
        currentZimWrapper = wrapper
        switchToZimMode()
        val mainEntry = wrapper.getMainPageEntry() ?: wrapper.getRandomArticle()
        if (mainEntry != null) {
            loadZimArticle(mainEntry)
        }
    }

    private fun tryGetPathFromUri(uri: Uri): String? {
        val context = requireContext()
        if (uri.scheme == "file") return uri.path
        
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val path = Os.readlink("/proc/self/fd/${pfd.fd}")
                if (path != null && (path.startsWith("/storage") || path.startsWith("/data") || path.startsWith("/mnt"))) {
                    return path
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed readlink resolution: ${e.message}")
        }

        if (DocumentsContract.isDocumentUri(context, uri)) {
            when {
                uri.authority == "com.android.externalstorage.documents" -> {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":")
                    val type = split[0]
                    return if ("primary".equals(type, ignoreCase = true)) {
                        Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                    } else {
                        "/storage/$type/${split[1]}"
                    }
                }
                uri.authority == "com.android.providers.downloads.documents" -> {
                    val id = DocumentsContract.getDocumentId(uri)
                    if (id.startsWith("raw:")) return id.substring(4)
                }
            }
        }
        
        if ("content".equals(uri.scheme, ignoreCase = true)) {
            try {
                context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex("_data")
                        if (index != -1) return cursor.getString(index)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed _data query: ${e.message}")
            }
        }
        
        return null
    }

    private fun loadZimArticle(entry: Entry) {
        var targetEntry = entry
        if (targetEntry.isRedirect) {
            try {
                targetEntry = targetEntry.redirectEntry
            } catch (_: Exception) {}
        }
        val serverUrl = "https://zim.local/${targetEntry.path}"
        webView.setBackgroundColor(android.graphics.Color.WHITE)
        webView.loadUrl(serverUrl)
        binding.urlInput.setText(targetEntry.path)
    }

    private fun updateMenuVisibility() {
        val navView = binding.navView
        if (lastModeInDrawer == isZimMode) return
        lastModeInDrawer = isZimMode

        navView.menu.clear()
        navView.inflateMenu(R.menu.browser_drawer_menu)
        
        val menu = navView.menu
        menu.findItem(R.id.nav_zim_search)?.isVisible = isZimMode
        menu.findItem(R.id.nav_zim_random)?.isVisible = isZimMode
        menu.findItem(R.id.nav_text_only_mode)?.isVisible = !isZimMode
    }

    private fun switchToZimMode() {
        isZimMode = true
        binding.urlInput.hint = "Search ZIM articles or type title..."
        updateMenuVisibility()
        
        webView.webViewClient = ZimWebViewClient(currentZimWrapper) { url ->
            val path = url.replace("https://zim.local/", "").replace("https://zim.local", "")
            if (!path.startsWith("data:") && path.isNotBlank()) {
               binding.urlInput.setText(path)
            }
        }
    }

    private fun switchToInternetMode() {
        isZimMode = false
        binding.urlInput.hint = "Enter URL or Search"
        updateMenuVisibility()
        
        webView.webViewClient = LocalFileWebViewClient { url ->
            if (url.startsWith("https://text-only.local/")) {
                return@LocalFileWebViewClient
            }
            if (url.startsWith("data:text/html")) {
                binding.urlInput.setText("")
            } else if (url.startsWith("file:///") || url.startsWith("content://")) {
                // Ensure bar is empty and hint is restored for local files
                binding.urlInput.setText("")
                binding.urlInput.hint = "Enter URL or Search"
            } else {
                binding.urlInput.setText(url)
            }
        }
    }

    private fun showZimSearchDialog(initialQuery: String = "") {
        val wrapper = currentZimWrapper
        if (wrapper == null) {
            Toast.makeText(requireContext(), "No ZIM file open.", Toast.LENGTH_SHORT).show()
            requestOpenFile("ZIM")
            return
        }

        val searchResults = wrapper.searchArticles(initialQuery, limit = 50)

        if (searchResults.isEmpty()) {
            Toast.makeText(requireContext(), "No ZIM articles found matching '$initialQuery'", Toast.LENGTH_SHORT).show()
            return
        }

        val displayTitles = searchResults.map { entry ->
            val title = try { entry.title } catch (_: Exception) { "" }
            if (title.isNotBlank() && title != entry.path) {
                "$title (${entry.path})"
            } else {
                entry.path
            }
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(if (initialQuery.isBlank()) "ZIM Articles" else "Search Results for '$initialQuery'")
            .setItems(displayTitles) { _, which ->
                val selected = searchResults[which]
                selected?.let { loadZimArticle(it) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentZimWrapper?.close()
        currentZimWrapper = null
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        _binding = null
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                switchToInternetMode()
                webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webView.loadData("<html><body style='background:transparent;'></body></html>", "text/html", "UTF-8")
                binding.urlInput.setText("")
            }
            R.id.nav_open_zim -> {
                requestOpenFile("ZIM")
            }
            R.id.nav_open_local -> {
                requestOpenFile("HTML/MHTML")
            }
            R.id.nav_zim_search -> {
                showZimSearchDialog()
            }
            R.id.nav_zim_random -> {
                val wrapper = currentZimWrapper
                if (wrapper == null) {
                    Toast.makeText(requireContext(), "No ZIM file open.", Toast.LENGTH_SHORT).show()
                    requestOpenFile("ZIM")
                } else {
                    val randomEntry = wrapper.getRandomArticle()
                    if (randomEntry != null) {
                        loadZimArticle(randomEntry)
                        Toast.makeText(requireContext(), "Random: ${randomEntry.title}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "No articles found in ZIM file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            R.id.nav_text_only_mode -> {
                val currentUrl = webView.url ?: ""
                val currentInput = binding.urlInput.text.toString()
                if (currentUrl.startsWith("https://text-only.local/") || currentInput.startsWith(getString(R.string.text_mode_prefix).substringBefore("%s"))) {
                    // Already in text only mode
                } else {
                    lifecycleScope.launch(Dispatchers.Main) {
                        val originalTitle = currentInput
                        val html = captureCurrentHtml()
                        if (html != null) {
                            val cleanedHtml = withContext(Dispatchers.IO) {
                                val body = TextOnlyCleaner.clean(html)
                                """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta charset="UTF-8">
                                    <title>Text Mode</title>
                                    <style>
                                        body { font-family: sans-serif; line-height: 1.6; padding: 20px; max-width: 800px; margin: 0 auto; background: #fdfdfd; color: #333; }
                                        h1 { color: #111; border-bottom: 2px solid #eee; padding-bottom: 10px; }
                                        img, video, iframe, nav, footer, sidebar { display: none !important; }
                                        pre { background: #f4f4f4; padding: 10px; overflow-x: auto; }
                                        code { font-family: monospace; background: #f4f4f4; padding: 2px 4px; }
                                    </style>
                                </head>
                                <body>
                                    $body
                                </body>
                                </html>
                                """.trimIndent()
                            }
                            webView.loadDataWithBaseURL("https://text-only.local/", cleanedHtml, "text/html", "UTF-8", null)
                            binding.urlInput.setText(getString(R.string.text_mode_prefix, originalTitle))
                        } else {
                            Toast.makeText(requireContext(), "Failed to capture content for Text Only Mode", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            R.id.nav_save_html -> {
                lifecycleScope.launch(Dispatchers.Main) {
                    val html = captureCurrentHtml()
                    if (html != null) {
                        capturedHtmlForExport = html
                        val title = TextOnlyCleaner.getTitle(html)
                        val cleanFileName = title.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(30)
                        saveHtmlLauncher.launch("$cleanFileName.html")
                    } else {
                        Toast.makeText(requireContext(), "Failed to capture content", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            R.id.nav_save_mhtml -> {
                saveMhtmlLauncher.launch("article_${System.currentTimeMillis()}.mhtml")
            }
            R.id.nav_save_pdf -> {
                exportPdf()
            }
            R.id.nav_save_epub -> {
                lifecycleScope.launch(Dispatchers.Main) {
                    val html = captureCurrentHtml()
                    if (html != null) {
                        capturedHtmlForExport = html
                        val title = TextOnlyCleaner.getTitle(html)
                        val cleanFileName = title.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(30)
                        saveEpubLauncher.launch("$cleanFileName.epub")
                    } else {
                        Toast.makeText(requireContext(), "Failed to capture content", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun exportPdf() {
        val printCss = """
            (function() {
                var style = document.getElementById('print-fix-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'print-fix-style';
                    style.type = 'text/css';
                    style.innerHTML = `
                        @media print {
                            html, body, div, article, section, main, #mw-content-text {
                                height: auto !important;
                                overflow: visible !important;
                                position: static !important;
                            }
                            /* Force visibility of all elements to prevent hidden overflow parents */
                            * {
                                overflow: visible !important;
                            }
                            /* Hide navigation elements that might take up space */
                            nav, footer, header, .header, .footer, .nav, .sidebar {
                                display: none !important;
                            }
                        }
                    `;
                    document.head.appendChild(style);
                }
            })()
        """.trimIndent()

        webView.evaluateJavascript(printCss) {
            // Wait for a few frames to allow the WebView to recalculate its internal scroll height
            // with the newly injected print CSS.
            webView.postDelayed({
                if (isAdded && activity != null) {
                    val printManager = requireContext().getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val jobName = "${getString(R.string.app_name)} Document"
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                    printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                }
            }, 500)
        }
    }

    private fun unescapeJsonString(json: String): String {
        if (json.isBlank() || json == "null") return ""
        return try {
            val tokener = org.json.JSONTokener(json)
            val value = tokener.nextValue()
            if (value is String) value else json
        } catch (e: Exception) {
            // Manual fallback for cases where JSONTokener fails on real devices
            json.trim().removeSurrounding("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\")
        }
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun exportHtmlToUri(uri: Uri) {
        val html = capturedHtmlForExport
        capturedHtmlForExport = null
        if (html != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(html.toByteArray())
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "HTML exported successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to save HTML: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            Toast.makeText(requireContext(), "No content to save", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportMhtmlToUri(uri: Uri) {
        val currentUrl = webView.url
        if (currentUrl != null && currentUrl.startsWith("file:///")) {
            val path = Uri.parse(currentUrl).path
            val file = if (path != null) File(path) else null
            if (file != null && (file.exists() || File(path + "l").exists())) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val realFile = if (file.exists()) file else File(path + "l")
                        requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                            realFile.inputStream().use { input -> input.copyTo(output) }
                        }
                        withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "MHTML exported successfully", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) {
                    }
                }
                return
            }
        }

        val tempFile = File(requireContext().cacheDir, "temp_archive.mhtml")
        webView.saveWebArchive(tempFile.absolutePath, false) { path ->
            if (path == null) {
                Toast.makeText(requireContext(), "Failed to generate MHTML", Toast.LENGTH_SHORT).show()
                return@saveWebArchive
            }
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val file = File(path)
                    requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                        file.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    file.delete()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "MHTML exported successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to save MHTML: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun exportEpubToUri(uri: Uri) {
        val html = capturedHtmlForExport
        capturedHtmlForExport = null
        if (html != null) {
            lifecycleScope.launch(Dispatchers.Main) {
                generateEpub(uri, html)
            }
        } else {
            Toast.makeText(requireContext(), "No content to save", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun captureCurrentHtml(): String? = withContext(Dispatchers.Main) {
        val currentUrl = webView.url ?: return@withContext null
        Log.d(TAG, "captureCurrentHtml: url=$currentUrl")

        // 1. ZIM mode
        if (currentUrl.contains("zim.local")) {
            val path = currentUrl.replace("https://zim.local/", "").replace("http://zim.local/", "")
            return@withContext withContext(Dispatchers.IO) {
                try {
                    currentZimWrapper?.getEntryForPath(path)?.getItem(true)?.data?.data?.let { String(it, Charsets.UTF_8) }
                } catch (e: Exception) {
                    Log.e(TAG, "ZIM capture failed", e)
                    null
                }
            }
        }

        // 2. Try JS first (Fastest and usually accurate for rendered DOM)
        val jsHtml = captureViaJs()
        val isInternalTextOnly = currentUrl.startsWith("https://text-only.local/")
        val jsThreshold = if (isInternalTextOnly) 10 else 500
        
        if (jsHtml != null && jsHtml.length >= jsThreshold) {
            Log.d(TAG, "Captured via JS, length=${jsHtml.length}")
            return@withContext jsHtml
        }

        // 3. Try saveWebArchive (Robust fallback for tricky/MHTML-based pages)
        // Skip web archive if we are in internal text-only mode as it's unreliable for data loads
        var mhtmlResult: String? = null
        if (!isInternalTextOnly) {
            mhtmlResult = captureViaWebArchive()
            if (mhtmlResult != null) {
                val extracted = extractHtmlFromMhtml(mhtmlResult)
                if (extracted.length > 500) {
                    Log.d(TAG, "Captured via saveWebArchive, length=${extracted.length}")
                    return@withContext extracted
                }
            }
        }

        // 4. Final fallback: Direct URI reading (if file:/// or content://)
        if (currentUrl.startsWith("file:///") || currentUrl.startsWith("content://")) {
            val raw = readUriContent(currentUrl)
            if (raw != null) {
                Log.d(TAG, "Captured via direct URI read")
                return@withContext if (isMhtml(raw)) extractHtmlFromMhtml(raw) else raw
            }
        }

        Log.e(TAG, "All capture methods failed for $currentUrl")
        return@withContext jsHtml ?: mhtmlResult?.let { extractHtmlFromMhtml(it) }
    }

    private suspend fun captureViaJs(): String? = try {
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript("document.documentElement.outerHTML") { rawHtml ->
                val html = unescapeJsonString(rawHtml ?: "null")
                if (continuation.isActive) {
                    continuation.resume(if (html.isNotBlank() && html != "null") html else null)
                }
            }
        }
    } catch (e: Exception) {
        null
    }

    private suspend fun captureViaWebArchive(): String? = suspendCancellableCoroutine { continuation ->
        val tempFile = File(requireContext().cacheDir, "cap_${System.currentTimeMillis()}.mhtml")
        webView.saveWebArchive(tempFile.absolutePath, false) { path ->
            if (path == null) {
                if (continuation.isActive) continuation.resume(null)
            } else {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val content = tempFile.readText(Charsets.UTF_8)
                        tempFile.delete()
                        if (continuation.isActive) continuation.resume(content)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        }
    }

    private suspend fun readUriContent(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(url)
            if (url.startsWith("file:///")) {
                val file = File(uri.path ?: "")
                if (file.exists() && file.canRead()) file.readText() else null
            } else {
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader().readText()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "readUriContent failed", e)
            null
        }
    }

    private fun isMhtml(content: String): Boolean {
        val head = content.take(1000).lowercase()
        return head.contains("multipart/related") || head.contains("content-type: message/rfc822")
    }

    private fun extractHtmlFromMhtml(mhtml: String): String {
        // Find boundary
        val boundaryMatch = Regex("boundary=\"?([^\";\\s]+)\"?").find(mhtml)
        val boundary = boundaryMatch?.groupValues?.get(1)

        // Split by boundary
        val parts = if (boundary != null) {
            mhtml.split("--$boundary")
        } else {
            mhtml.split(Regex("\r?\n--"))
        }

        // Find the best text/html part (usually largest)
        val htmlPart = parts.filter { it.contains("Content-Type: text/html", ignoreCase = true) }
            .maxByOrNull { it.length } ?: return mhtml

        val splitIndex = htmlPart.indexOf("\n\n").takeIf { it != -1 } ?: htmlPart.indexOf("\r\n\r\n")
        if (splitIndex == -1) return htmlPart

        val headers = htmlPart.substring(0, splitIndex)
        val body = htmlPart.substring(splitIndex).trim()

        return if (headers.contains("base64", ignoreCase = true)) {
            try {
                String(android.util.Base64.decode(body.replace(Regex("\\s"), ""), android.util.Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) {
                body
            }
        } else if (headers.contains("quoted-printable", ignoreCase = true)) {
            decodeQuotedPrintable(body)
        } else {
            body
        }
    }

    private fun decodeQuotedPrintable(input: String): String {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        val cleanInput = input.replace("=\r\n", "").replace("=\n", "")
        while (i < cleanInput.length) {
            val c = cleanInput[i]
            if (c == '=') {
                if (i + 2 < cleanInput.length) {
                    try {
                        val hex = cleanInput.substring(i + 1, i + 3)
                        out.write(hex.toInt(16))
                        i += 3
                        continue
                    } catch (_: Exception) {}
                }
            }
            out.write(c.code)
            i++
        }
        return out.toString("UTF-8")
    }

    private suspend fun generateEpub(uri: Uri, html: String) = withContext(Dispatchers.IO) {
        try {
            val cleanedBody = TextOnlyCleaner.clean(html)
            val articleTitle = TextOnlyCleaner.getTitle(html)
            val escapedTitle = escapeXml(articleTitle)

            requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                ZipOutputStream(output).use { zip ->
                    zip.setMethod(ZipOutputStream.STORED)
                    val mimeBytes = "application/epub+zip".toByteArray()
                    val crc = java.util.zip.CRC32()
                    crc.update(mimeBytes)
                    val mimeEntry = ZipEntry("mimetype")
                    mimeEntry.size = mimeBytes.size.toLong()
                    mimeEntry.compressedSize = mimeBytes.size.toLong()
                    mimeEntry.crc = crc.value
                    zip.putNextEntry(mimeEntry)
                    zip.write(mimeBytes)
                    zip.closeEntry()
                    
                    zip.setMethod(ZipOutputStream.DEFLATED)

                    zip.putNextEntry(ZipEntry("META-INF/container.xml"))
                    zip.write(
                        """<?xml version="1.0"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                           <rootfiles>
                              <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                           </rootfiles>
                        </container>""".trimIndent().toByteArray()
                    )
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
                    zip.write(
                        """<?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
                           <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                              <dc:title>$escapedTitle</dc:title>
                              <dc:language>en</dc:language>
                              <dc:identifier id="BookId">urn:uuid:${java.util.UUID.randomUUID()}</dc:identifier>
                           </metadata>
                           <manifest>
                              <item id="article" href="article.xhtml" media-type="application/xhtml+xml"/>
                              <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                           </manifest>
                           <spine toc="ncx">
                              <itemref idref="article"/>
                           </spine>
                        </package>""".trimIndent().toByteArray()
                    )
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
                    zip.write(
                        """<?xml version="1.0" encoding="UTF-8"?>
                        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                           <head>
                              <meta name="dtb:uid" content="urn:uuid:12345"/>
                              <meta name="dtb:depth" content="1"/>
                           </head>
                           <docTitle><text>$escapedTitle</text></docTitle>
                           <navMap>
                              <navPoint id="navpoint-1" playOrder="1">
                                 <navLabel><text>Article</text></navLabel>
                                 <content src="article.xhtml"/>
                              </navPoint>
                           </navMap>
                        </ncx>""".trimIndent().toByteArray()
                    )
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry("OEBPS/article.xhtml"))
                    zip.write(
                        """<?xml version="1.0" encoding="UTF-8"?>
                        <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
                        <html xmlns="http://www.w3.org/1999/xhtml">
                        <head><title>$escapedTitle</title></head>
                        <body>
                        $cleanedBody
                        </body>
                        </html>""".trimIndent().toByteArray()
                    )
                    zip.closeEntry()
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "ePub exported successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Failed to save ePub: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
