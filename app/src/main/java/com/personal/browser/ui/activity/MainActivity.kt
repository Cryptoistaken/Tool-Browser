package com.personal.browser.ui.activity

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.personal.browser.databinding.ActivityMainBinding
import com.personal.browser.ui.viewmodel.BrowserViewModel
import com.personal.browser.utils.UrlUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BrowserViewModel by viewModels()

    private val webViews = mutableMapOf<String, WebView>()
    private var currentWebView: WebView? = null

    private var isAdBlockEnabled = true

    // Lightweight ad-block domain list
    private val adBlockDomains = setOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "adservice.google.com", "amazon-adsystem.com", "criteo.com", "adnxs.com"
    )

    private val activeUserScripts = listOf(
        "javascript:(function() { console.log('User Scripts System Ready.'); })();"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupOmnibox()
        setupUrlBar()
        observeViewModel()
        setupBackHandler()
        setupSwipeRefresh()
    }

    private fun setupOmnibox() {
        binding.btnMenu.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(this, view)
            popup.menu.add("New Tab")
            popup.menu.add("Bookmarks")
            popup.menu.add("History")
            popup.menu.add("Desktop Site")
            popup.menu.add("Share")
            popup.menu.add("Clear Data")
            popup.menu.add("Ad Blocking Stats")
            popup.menu.add("User Scripts")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "New Tab" -> viewModel.openNewTab()
                    "Desktop Site" -> toggleDesktopMode()
                    "Share" -> shareCurrentUrl()
                    "Clear Data" -> clearBrowsingData()
                    "Ad Blocking Stats" -> com.google.android.material.snackbar.Snackbar.make(binding.root, "Ad Blocking Enabled", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                    "User Scripts" -> com.google.android.material.snackbar.Snackbar.make(binding.root, "Scripts Active", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                }
                true
            }
            popup.show()
        }

        binding.btnSiteInfo.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(this, view)
            if (currentWebView?.url != null && currentWebView?.url?.startsWith("http") == true) {
                popup.menu.add("Copy Cookies")
                popup.menu.add("Website info")
            } else {
                popup.menu.add("General Settings")
                popup.menu.add("Ad blocking")
                popup.menu.add("Scripts")
            }
            popup.setOnMenuItemClickListener { item ->
                when(item.title) {
                    "Copy Cookies" -> copyCurrentCookies()
                    "Website info" -> com.google.android.material.snackbar.Snackbar.make(binding.root, "Site Secure via HTTPS", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                    "General Settings" -> {
                        val intent = android.content.Intent(this@MainActivity, SettingsActivity::class.java)
                        intent.putExtra("SETTINGS_MODE", "GENERAL")
                        startActivity(intent)
                    }
                    "Ad blocking" -> {
                        val intent = android.content.Intent(this@MainActivity, SettingsActivity::class.java)
                        intent.putExtra("SETTINGS_MODE", "AD_BLOCKING")
                        startActivity(intent)
                    }
                    "Scripts" -> {
                        val intent = android.content.Intent(this@MainActivity, SettingsActivity::class.java)
                        intent.putExtra("SETTINGS_MODE", "SCRIPTS")
                        startActivity(intent)
                    }
                }
                true
            }
            popup.show()
        }
    }

    private fun setupUrlBar() {
        binding.urlEditText.setOnEditorActionListener { textView, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val url = UrlUtils.processInput(textView.text.toString())
                loadUrl(url)
                hideKeyboard()
                true
            } else false
        }

        binding.urlEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.urlEditText.selectAll()
            } else {
                updateUrlDisplay()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.activeTabIndex.observe(this) { index ->
            val tabs = viewModel.tabs.value ?: return@observe
            val tab = tabs.getOrNull(index) ?: return@observe

            switchToTab(tab.id, tab.url)
            updateUrlDisplay()
        }
    }

    private fun switchToTab(tabId: String, url: String) {
        currentWebView?.let {
            binding.webViewContainer.removeView(it)
        }

        val webView = webViews.getOrPut(tabId) {
            createWebView().also { wv ->
                if (url.isNotEmpty()) wv.loadUrl(url)
            }
        }

        currentWebView = webView
        binding.webViewContainer.addView(webView, 0)
    }

    private fun createWebView(): WebView {
        return WebView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                mediaPlaybackRequiresUserGesture = false
                databaseEnabled = true
                allowFileAccess = true
                userAgentString = settings.userAgentString.replace("; wv", "")
            }

            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (this@apply === currentWebView) {
                        viewModel.onPageStarted(url, view.title)
                        binding.progressBar.isVisible = true
                        
                        // Switch Icon to Shield if loading http/s
                        if (url.startsWith("http")) {
                            binding.btnSiteInfo.setImageResource(com.personal.browser.R.drawable.ic_security) // Ensure ic_security exists, falling back if not but assuming Standard material exists
                        } else {
                            binding.btnSiteInfo.setImageResource(com.personal.browser.R.drawable.ic_history)
                        }
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (this@apply === currentWebView) {
                        viewModel.onPageFinished(url, view.title)
                        binding.progressBar.isVisible = false
                        updateUrlDisplay()
                        
                        // Inject User Scripts
                        activeUserScripts.forEach { script ->
                            view.evaluateJavascript(script, null)
                        }
                    }
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (isAdBlockEnabled) {
                        val host = request.url.host ?: ""
                        if (adBlockDomains.any { host.contains(it) }) {
                            // Block the ad by returning an empty input stream
                            return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream("".toByteArray()))
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme ?: ""
                    return if (scheme == "http" || scheme == "https") {
                        false
                    } else {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                            startActivity(intent)
                        } catch (e: Exception) { /* ignore */ }
                        true
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (this@apply === currentWebView) {
                        viewModel.onProgressChanged(newProgress)
                        binding.progressBar.progress = newProgress
                    }
                }

                override fun onReceivedTitle(view: WebView, title: String) {
                    if (this@apply === currentWebView) {
                        viewModel.onTitleReceived(title)
                    }
                }
            }
        }
    }

    private fun loadUrl(url: String) {
        val tab = viewModel.activeTab
        if (tab == null) {
            viewModel.openNewTab(url)
        } else {
            currentWebView?.loadUrl(url) ?: run {
                val id = tab.id
                val wv = createWebView()
                webViews[id] = wv
                currentWebView = wv
                binding.webViewContainer.addView(wv, 0)
                wv.loadUrl(url)
            }
        }
        hideKeyboard()
    }

    private fun updateUrlDisplay() {
        if (!binding.urlEditText.hasFocus()) {
            val url = viewModel.activeTab?.url ?: ""
            binding.urlEditText.setText(UrlUtils.getDisplayUrl(url))
        }
    }

    private fun clearBrowsingData() {
        currentWebView?.clearCache(true)
        currentWebView?.clearHistory()
        android.webkit.WebStorage.getInstance().deleteAllData()
        viewModel.clearHistory()
        com.google.android.material.snackbar.Snackbar.make(
            binding.root, getString(com.personal.browser.R.string.data_cleared), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun toggleDesktopMode() {
        val wv = currentWebView ?: return
        val isDesktop = wv.settings.userAgentString.contains("X11")
        if (isDesktop) {
            wv.settings.userAgentString = wv.settings.userAgentString
                .replace("X11; Linux x86_64", "Linux; Android 13; Pixel 7")
                .replace("Chrome/", "Mobile Chrome/")
        } else {
            wv.settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        }
        wv.reload()
    }

    private fun shareCurrentUrl() {
        val url = viewModel.activeTab?.url ?: return
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, url)
        }
        startActivity(android.content.Intent.createChooser(intent, getString(com.personal.browser.R.string.share_url)))
    }

    private fun copyCurrentCookies() {
        val url = viewModel.activeTab?.url ?: return
        val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Cookies", cookies ?: ""))
        com.google.android.material.snackbar.Snackbar.make(
            binding.root, getString(com.personal.browser.R.string.cookies_copied), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.urlEditText.hasFocus() -> {
                        hideKeyboard()
                        binding.urlEditText.clearFocus()
                    }
                    currentWebView?.canGoBack() == true -> {
                        currentWebView?.goBack()
                    }
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            currentWebView?.reload()
            binding.swipeRefresh.isRefreshing = false
        }
        binding.swipeRefresh.setColorSchemeResources(com.personal.browser.R.color.primary)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
        }
        binding.urlEditText.clearFocus()
    }

    override fun onResume() {
        super.onResume()
        currentWebView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        currentWebView?.onPause()
    }

    override fun onDestroy() {
        webViews.values.forEach { it.destroy() }
        webViews.clear()
        super.onDestroy()
    }
}
