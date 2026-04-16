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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.personal.browser.R
import com.personal.browser.databinding.ActivityMainBinding
import com.personal.browser.ui.adapter.BookmarkHistoryAdapter
import com.personal.browser.ui.adapter.TabsAdapter
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
        setupNavButtons()
        observeViewModel()
        setupBackHandler()
        setupSwipeRefresh()
    }

    // ── Omnibox / top-bar menu ────────────────────────────────────────────────

    private fun setupOmnibox() {
        binding.btnMenu.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(this, view)
            popup.menu.add("New Tab")
            popup.menu.add("Bookmarks")
            popup.menu.add("History")
            popup.menu.add("Desktop Site")
            popup.menu.add("Share")
            popup.menu.add("Copy URL")
            popup.menu.add("Clear Data")
            popup.menu.add("Settings")
            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "New Tab"      -> viewModel.openNewTab()
                    "Bookmarks"    -> showBookmarksSheet()
                    "History"      -> showHistorySheet()
                    "Desktop Site" -> toggleDesktopMode()
                    "Share"        -> shareCurrentUrl()
                    "Copy URL"     -> copyCurrentUrl()
                    "Clear Data"   -> clearBrowsingData()
                    "Settings"     -> {
                        startActivity(android.content.Intent(this, SettingsActivity::class.java)
                            .putExtra("SETTINGS_MODE", "GENERAL"))
                    }
                }
                true
            }
            popup.show()
        }

        binding.btnSiteInfo.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(this, view)
            val hasUrl = currentWebView?.url?.startsWith("http") == true
            if (hasUrl) {
                popup.menu.add("Website Info")
                popup.menu.add("Copy Cookies")
            } else {
                popup.menu.add("General Settings")
                popup.menu.add("Ad Blocking")
                popup.menu.add("User Scripts")
            }
            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Website Info" -> {
                        val url = currentWebView?.url ?: ""
                        val scheme = if (url.startsWith("https")) "🔒 Secure (HTTPS)" else "⚠️ Not Secure (HTTP)"
                        com.google.android.material.snackbar.Snackbar
                            .make(binding.root, scheme, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                    }
                    "Copy Cookies"     -> copyCurrentCookies()
                    "General Settings" -> startActivity(android.content.Intent(this, SettingsActivity::class.java)
                        .putExtra("SETTINGS_MODE", "GENERAL"))
                    "Ad Blocking"      -> startActivity(android.content.Intent(this, SettingsActivity::class.java)
                        .putExtra("SETTINGS_MODE", "AD_BLOCKING"))
                    "User Scripts"     -> startActivity(android.content.Intent(this, SettingsActivity::class.java)
                        .putExtra("SETTINGS_MODE", "SCRIPTS"))
                }
                true
            }
            popup.show()
        }
    }

    // ── URL bar ───────────────────────────────────────────────────────────────

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

    // ── Bottom navigation buttons ─────────────────────────────────────────────

    private fun setupNavButtons() {
        binding.btnBack.setOnClickListener {
            currentWebView?.goBack()
        }
        binding.btnForward.setOnClickListener {
            currentWebView?.goForward()
        }
        binding.btnRefresh.setOnClickListener {
            currentWebView?.reload()
        }
        binding.btnBookmark.setOnClickListener {
            viewModel.toggleBookmark()
        }
        binding.btnTabs.setOnClickListener {
            showTabsSheet()
        }
    }

    private fun updateNavState() {
        val canBack = currentWebView?.canGoBack() == true
        val canForward = currentWebView?.canGoForward() == true
        binding.btnBack.alpha = if (canBack) 1f else 0.4f
        binding.btnForward.alpha = if (canForward) 1f else 0.4f
        binding.btnBack.isEnabled = canBack
        binding.btnForward.isEnabled = canForward
        viewModel.onNavigationStateChanged(canBack, canForward)
    }

    // ── ViewModel observers ───────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.activeTabIndex.observe(this) { index ->
            val tabs = viewModel.tabs.value ?: return@observe
            val tab = tabs.getOrNull(index) ?: return@observe
            switchToTab(tab.id, tab.url)
            updateUrlDisplay()
            updateNavState()
        }

        viewModel.tabs.observe(this) { tabs ->
            binding.tvTabCount.text = tabs.size.toString()
        }

        viewModel.isBookmarked.observe(this) { bookmarked ->
            binding.btnBookmark.alpha = if (bookmarked) 1f else 0.5f
        }
    }

    // ── Tab management ────────────────────────────────────────────────────────

    private fun switchToTab(tabId: String, url: String) {
        currentWebView?.let { binding.webViewContainer.removeView(it) }

        val webView = webViews.getOrPut(tabId) {
            createWebView().also { wv ->
                if (url.isNotEmpty()) wv.loadUrl(url)
            }
        }

        currentWebView = webView
        binding.webViewContainer.addView(webView, 0)
        updateNavState()
    }

    private fun showTabsSheet() {
        val sheet = BottomSheetDialog(this)
        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setPadding(0, 16, 0, 16)
        }
        val adapter = TabsAdapter(
            onTabClick = { index ->
                viewModel.switchTab(index)
                sheet.dismiss()
            },
            onTabClose = { index ->
                viewModel.closeTab(index)
                if ((viewModel.tabs.value?.size ?: 0) == 0) sheet.dismiss()
            }
        )
        rv.adapter = adapter
        adapter.submitList(viewModel.tabs.value?.toList() ?: emptyList())

        // Add a "New Tab" button header
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        val headerBtn = android.widget.Button(this).apply {
            text = "+ New Tab"
            setOnClickListener { viewModel.openNewTab(); sheet.dismiss() }
        }
        container.addView(headerBtn, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.setMargins(32, 16, 32, 0) })
        container.addView(rv)
        sheet.setContentView(container)
        sheet.show()
    }

    // ── Bookmarks / History sheets ────────────────────────────────────────────

    private fun showBookmarksSheet() {
        val sheet = BottomSheetDialog(this)
        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setPadding(0, 16, 0, 32)
        }
        val adapter = BookmarkHistoryAdapter(
            onItemClick = { url -> loadUrl(url); sheet.dismiss() },
            onItemDelete = { /* future: delete bookmark */ }
        )
        rv.adapter = adapter
        viewModel.bookmarks.observe(this) { list ->
            adapter.submitBookmarks(list)
        }
        sheet.setContentView(rv)
        sheet.show()
    }

    private fun showHistorySheet() {
        val sheet = BottomSheetDialog(this)
        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setPadding(0, 16, 0, 32)
        }
        val adapter = BookmarkHistoryAdapter(
            onItemClick = { url -> loadUrl(url); sheet.dismiss() },
            onItemDelete = { /* future: delete entry */ }
        )
        rv.adapter = adapter
        viewModel.history.observe(this) { list ->
            adapter.submitHistory(list)
        }
        sheet.setContentView(rv)
        sheet.show()
    }

    // ── WebView factory ───────────────────────────────────────────────────────

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
                        binding.btnSiteInfo.setImageResource(
                            if (url.startsWith("https")) R.drawable.ic_security else R.drawable.ic_history
                        )
                        updateNavState()
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (this@apply === currentWebView) {
                        viewModel.onPageFinished(url, view.title)
                        binding.progressBar.isVisible = false
                        updateUrlDisplay()
                        updateNavState()
                        activeUserScripts.forEach { script ->
                            view.evaluateJavascript(script, null)
                        }
                    }
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (isAdBlockEnabled) {
                        val host = request.url.host ?: ""
                        if (adBlockDomains.any { host.contains(it) }) {
                            return WebResourceResponse("text/plain", "UTF-8",
                                java.io.ByteArrayInputStream("".toByteArray()))
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
                            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url))
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

    // ── URL loading & display ─────────────────────────────────────────────────

    private fun loadUrl(url: String) {
        val tab = viewModel.activeTab
        if (tab == null) {
            viewModel.openNewTab(url)
        } else {
            currentWebView?.loadUrl(url) ?: run {
                val wv = createWebView()
                webViews[tab.id] = wv
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

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun clearBrowsingData() {
        currentWebView?.clearCache(true)
        currentWebView?.clearHistory()
        android.webkit.WebStorage.getInstance().deleteAllData()
        viewModel.clearHistory()
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, getString(R.string.data_cleared), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
        updateNavState()
    }

    private fun toggleDesktopMode() {
        val wv = currentWebView ?: return
        val isDesktop = wv.settings.userAgentString.contains("X11")
        wv.settings.userAgentString = if (isDesktop) {
            wv.settings.userAgentString
                .replace("X11; Linux x86_64", "Linux; Android 13; Pixel 7")
                .replace("Chrome/", "Mobile Chrome/")
        } else {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        }
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, if (isDesktop) "Mobile mode" else "Desktop mode", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
        wv.reload()
    }

    private fun shareCurrentUrl() {
        val url = viewModel.activeTab?.url ?: return
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, url)
        }
        startActivity(android.content.Intent.createChooser(intent, getString(R.string.share_url)))
    }

    private fun copyCurrentUrl() {
        val url = viewModel.activeTab?.url ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("URL", url))
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, getString(R.string.url_copied), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
    }

    private fun copyCurrentCookies() {
        val url = viewModel.activeTab?.url ?: return
        val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Cookies", cookies ?: ""))
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, getString(R.string.cookies_copied), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
    }

    // ── Back press & lifecycle ────────────────────────────────────────────────

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.urlEditText.hasFocus() -> {
                        hideKeyboard()
                        binding.urlEditText.clearFocus()
                    }
                    currentWebView?.canGoBack() == true -> currentWebView?.goBack()
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
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
        binding.urlEditText.clearFocus()
    }

    override fun onResume() { super.onResume(); currentWebView?.onResume() }
    override fun onPause() { super.onPause(); currentWebView?.onPause() }
    override fun onDestroy() { webViews.values.forEach { it.destroy() }; webViews.clear(); super.onDestroy() }
}
