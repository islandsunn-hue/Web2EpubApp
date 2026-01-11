package com.example.poweruser1.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.poweruser1.R
import com.example.poweruser1.databinding.FragmentHomeBinding
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.math.roundToInt

class HomeFragment : Fragment(), NavigationView.OnNavigationItemSelectedListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var webView: WebView
    private var editorUpdateJob: Job? = null
    private var currentUrl: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, you can now save
        } else {
            Toast.makeText(requireContext(), "Permission denied to write to storage", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        webView = binding.webview
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)


        binding.toolbar.inflateMenu(R.menu.browser_toolbar_menu)
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_tools -> {
                    binding.drawerLayout.openDrawer(GravityCompat.START)
                    true
                }
                else -> false
            }
        }

        binding.navView.setNavigationItemSelectedListener(this)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                editorUpdateJob?.cancel()
                currentUrl = url
                val isRealPage = url != null && url != "about:blank" && !url.startsWith("data:text/html")
                if (isRealPage) {
                    binding.editPanel.visibility = View.GONE
                    binding.htmlInput.setText("")
                    binding.cssInput.setText("")
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && url.startsWith("data:text/html")) {
                    binding.urlInput.setText("")
                } else {
                    binding.urlInput.setText(url)
                }
            }
        }

        webView.loadData("<html><body></body></html>", "text/html", "UTF-8")

        fun loadUrlFromInput() {
            val input = binding.urlInput.text.toString().trim()
            Log.d("HomeFragment", "loadUrlFromInput called with input: $input")
            if (input.isNotBlank()) {
                val url = if (Patterns.WEB_URL.matcher(input).matches()) {
                    if (!input.startsWith("https://") && !input.startsWith("http://")) {
                        "https://$input"
                    } else {
                        input
                    }
                } else {
                    val encodedQuery = URLEncoder.encode(input, "UTF-8")
                    "https://www.google.com/search?q=$encodedQuery"
                }
                webView.loadUrl(url)
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(binding.urlInput.windowToken, 0)
            }
        }

        binding.goButton.setOnClickListener {
            Log.d("HomeFragment", "Go button clicked")
            loadUrlFromInput()
        }



        binding.showHtmlButton.setOnClickListener {
            binding.htmlScroller.visibility = View.VISIBLE
            binding.cssScroller.visibility = View.GONE
        }

        binding.showCssButton.setOnClickListener {
            binding.htmlScroller.visibility = View.GONE
            binding.cssScroller.visibility = View.VISIBLE
        }

        binding.applyButton.setOnClickListener {
            val htmlText = binding.htmlInput.text.toString()
            val escapedHtml = htmlText.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "")
            val htmlJs = "document.body.innerHTML = '$escapedHtml';"
            webView.evaluateJavascript(htmlJs, null)

            val cssText = binding.cssInput.text.toString()
            val escapedCss = cssText.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("`", "\\`")
                .replace("\${", "\\\${")

            val cssJs = """
                (function() {
                    var style = document.getElementById('injected-style');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = 'injected-style';
                        document.head.appendChild(style);
                    }
                    style.innerHTML = `$escapedCss`;
                })();
            """.trimIndent()
            webView.evaluateJavascript(cssJs, null)
        }

        binding.dragHandle.setOnTouchListener { _, event ->
            val layoutParams = binding.editPanel.layoutParams
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    val newHeight = (root.height - event.rawY).toInt()
                    val minHeight = (root.height * 0.15).roundToInt()
                    val maxHeight = (root.height * 0.85).roundToInt()
                    if (newHeight in minHeight..maxHeight) {
                        layoutParams.height = newHeight
                        binding.editPanel.layoutParams = layoutParams
                    }
                }
            }
            true
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.urlInput.requestFocus()

        if (savedInstanceState == null) {
            binding.editPanel.visibility = View.VISIBLE
            loadEditorContent()
        }

        // Add this callback for handling the back button press
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

    private fun loadEditorContent() {
        if (binding.editPanel.visibility != View.VISIBLE) {
            return
        }

        val url = webView.url
        val isRealPage = url != null && url != "about:blank" && !url.startsWith("data:text/html")

        if (isRealPage) {
            binding.editorSpinner.visibility = View.VISIBLE
            editorUpdateJob = lifecycleScope.launch {
                val formattedHtmlDeferred = async {
                    val rawHtml = getHtmlFromWebView(webView)
                    withContext(Dispatchers.Default) {
                        val doc = Jsoup.parseBodyFragment(rawHtml)
                        doc.select("script, style").remove()
                        doc.outputSettings().indentAmount(4)
                        doc.body().html()
                    }
                }

                val formattedCssDeferred = async {
                    val rawCss = getCssFromWebView(webView)
                    withContext(Dispatchers.Default) {
                        rawCss.replace(";", ";\n")
                            .replace("{", "{\n")
                            .replace("}", "}\n\n")
                    }
                }

                binding.htmlInput.setText(formattedHtmlDeferred.await())
                binding.cssInput.setText(formattedCssDeferred.await())
                binding.editorSpinner.visibility = View.GONE
            }
        } else {
            binding.editorSpinner.visibility = View.GONE
        }
    }

    private suspend fun getHtmlFromWebView(webView: WebView): String = suspendCancellableCoroutine {
        continuation ->
        webView.evaluateJavascript("document.documentElement.outerHTML") { htmlJson ->
            if (htmlJson == null) {
                continuation.resume("")
                return@evaluateJavascript
            }
            val rawHtml = (JSONTokener(htmlJson).nextValue() as? String) ?: ""
            continuation.resume(rawHtml)
        }
    }

    private suspend fun getCssFromWebView(webView: WebView): String = suspendCancellableCoroutine{
        continuation ->
        val cssJs = """
            (function() {
                var css = '';
                var styles = document.head.getElementsByTagName('style');
                for (var i = 0; i < styles.length; i++) {
                    css += styles[i].innerHTML + '\n';
                }
                return css;
            })();
        """.trimIndent()
        webView.evaluateJavascript(cssJs) { cssJson ->
            if (cssJson == null) {
                continuation.resume("")
                return@evaluateJavascript
            }
            val rawCss = (JSONTokener(cssJson).nextValue() as? String) ?: ""
            continuation.resume(rawCss)
        }
    }

    private fun saveEpub() {
        lifecycleScope.launch {
            val rawHtml = getHtmlFromWebView(webView)
            val (title, cleanHtml) = withContext(Dispatchers.Default) {
                val doc = Jsoup.parse(rawHtml)
                doc.select("script, style, link").remove()
                val title = doc.title()
                val cleanHtml = doc.html()
                Pair(title, cleanHtml)
            }

            val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$title.epub")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/epub+zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = requireContext().contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let { requireContext().contentResolver.openOutputStream(it) }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, "$title.epub")
                FileOutputStream(file)
            }

            outputStream?.use { outStream ->
                ZipOutputStream(outStream).use { zip ->
                    val mimetypeContent = "application/epub+zip".toByteArray()
                    val mimetypeEntry = ZipEntry("mimetype").apply {
                        method = ZipEntry.STORED
                        size = mimetypeContent.size.toLong()
                        val crc = CRC32()
                        crc.update(mimetypeContent)
                        this.crc = crc.value
                    }
                    zip.putNextEntry(mimetypeEntry)
                    zip.write(mimetypeContent)
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry("META-INF/container.xml"))
                    val containerXml = """
                        <?xml version="1.0"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                            <rootfiles>
                                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                            </rootfiles>
                        </container>
                    """.trimIndent()
                    zip.write(containerXml.toByteArray())
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
                    val contentOpf = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package version="2.0" xmlns="http://www.idpf.org/2007/opf">
                            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                                <dc:title>$title</dc:title>
                                <dc:language>en</dc:language>
                            </metadata>
                            <manifest>
                                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                                <item id="content" href="index.html" media-type="application/xhtml+xml"/>
                            </manifest>
                            <spine toc="ncx">
                                <itemref idref="content"/>
                            </spine>
                        </package>
                    """.trimIndent()
                    zip.write(contentOpf.toByteArray())
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
                    val tocNcx = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <ncx version="2005-1" xmlns="http://www.daisy.org/z3986/2005/ncx/">
                            <head>
                                <meta name="dtb:uid" content=""/>
                                <meta name="dtb:depth" content="1"/>
                                <meta name="dtb:totalPageCount" content="0"/>
                                <meta name="dtb:maxPageNumber" content="0"/>
                            </head>
                            <docTitle>
                                <text>$title</text>
                            </docTitle>
                            <navMap>
                                <navPoint id="navPoint-1" playOrder="1">
                                    <navLabel>
                                        <text>$title</text>
                                    </navLabel>
                                    <content src="index.html"/>
                                </navPoint>
                            </navMap>
                        </ncx>
                    """.trimIndent()
                    zip.write(tocNcx.toByteArray())
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry("OEBPS/index.html"))
                    zip.write(cleanHtml.toByteArray())
                    zip.closeEntry()
                }
            }

            Toast.makeText(requireContext(), "ePub saved to Downloads", Toast.LENGTH_SHORT).show()
        }
    }

    private fun savePdf() {
        val printManager = requireContext().getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "${webView.title} Document"
        val printAdapter: PrintDocumentAdapter = webView.createPrintDocumentAdapter(jobName)
        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
    }

    private fun saveHtml() {
        lifecycleScope.launch {
            val title = webView.title ?: "page"
            val fileName = "$title.html"
            val htmlContent = getHtmlFromWebView(webView)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/html")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = requireContext().contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    requireContext().contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(htmlContent.toByteArray())
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use {
                    it.write(htmlContent.toByteArray())
                }
            }
            Toast.makeText(requireContext(), "HTML saved to Downloads", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveMhtml() {
        val title = webView.title ?: "page"
        val fileName = "$title.mhtml"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On Android Q+, save to app's cache dir first, then move to Downloads.
            val tempFile = File(requireContext().cacheDir, fileName)
            webView.saveWebArchive(tempFile.absolutePath, false) { path ->
                if (path != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "message/rfc822")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }

                        val resolver = requireContext().contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

                        if (uri != null) {
                            try {
                                resolver.openOutputStream(uri)?.use { outputStream ->
                                    tempFile.inputStream().use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                                values.clear()
                                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                                resolver.update(uri, values, null, null)

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), "MHTML saved to Downloads", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), "Failed to move MHTML to Downloads", Toast.LENGTH_SHORT).show()
                                }
                            } finally {
                                tempFile.delete()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Failed to create MHTML in Downloads", Toast.LENGTH_SHORT).show()
                            }
                            tempFile.delete()
                        }
                    }
                } else {
                    lifecycleScope.launch(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to save MHTML", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            // For older versions, save directly.
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = File(downloadsDir, fileName)
            webView.saveWebArchive(file.absolutePath, false) { path ->
                lifecycleScope.launch(Dispatchers.Main) {
                    if (path != null) {
                        Toast.makeText(requireContext(), "MHTML saved to Downloads", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to save MHTML", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        editorUpdateJob?.cancel()
        // Prevent memory leaks with WebView
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        webView.destroy()
        _binding = null
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                webView.loadData("<html><body></body></html>", "text/html", "UTF-8")
                binding.htmlInput.setText("")
                binding.cssInput.setText("")
                binding.editPanel.visibility = View.VISIBLE
            }
            R.id.nav_toggle_editor -> {
                binding.editPanel.visibility = if (binding.editPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                if (binding.editPanel.visibility == View.VISIBLE) {
                    loadEditorContent()
                }
            }
            R.id.nav_text_only_mode -> {
                lifecycleScope.launch {
                    val rawHtml = getHtmlFromWebView(webView)
                    val textOnlyHtml = withContext(Dispatchers.Default) {
                        val doc = Jsoup.parse(rawHtml)
                        doc.select("script, style, link, img, video, audio, iframe, embed, object, svg, canvas, input, button, select, textarea").remove()
                        doc.html()
                    }
                    webView.url?.let {
                        webView.loadDataWithBaseURL(it, textOnlyHtml, "text/html", "UTF-8", null)
                    }

                    val formattedHtml = withContext(Dispatchers.Default) {
                        val doc = Jsoup.parse(textOnlyHtml)
                        doc.outputSettings().indentAmount(4)
                        doc.body().html()
                    }
                    binding.htmlInput.setText(formattedHtml)
                    binding.cssInput.setText("")
                }
            }
            R.id.nav_save_epub -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    saveEpub()
                }
            }
            R.id.nav_save_html -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    saveHtml()
                }
            }
            R.id.nav_save_mhtml -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    saveMhtml()
                }
            }
            R.id.nav_save_pdf -> {
                savePdf()
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}
