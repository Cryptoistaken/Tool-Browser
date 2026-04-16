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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.personal.browser.databinding.ActivityMainBinding
import com.personal.browser.ui.adapter.TabsAdapter
import com.personal.browser.ui.adapter.BookmarkHistoryAdapter
import com.personal.browser.ui.viewmodel.BrowserViewModel
import com.personal.browser.utils.UrlUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BrowserViewModel by viewModels()

    private val webViews = mutableMapOf<String, WebView>()
    private var currentWebView: WebView? = null

    private lateinit var tabsAdapter: TabsAdapter
    private lateinit var bookmarkHistoryAdapter: BookmarkHistoryAdapter

    private lateinit var panelBehavior: BottomSheetBehavior<View>
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

        setupBottomPanel()
        setupUrlBar()
        setupNavigationButtons()
        setupTabsRecycler()
        setupBookmarkHistoryRecycler()
        observeViewModel()
        setupBackHandler()
        setupSwipeRefresh()
    }

    private fun setupBottomPanel() {
        panelBehavior = BottomSheetBehavior.from(binding.bottomPanel)
        panelBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        panelBehavior.skipCollapsed = true

        binding.btnNewTab.setOnClickListener {
            viewModel.openNewTab()
            hidePanels()
        }

        binding.btnBookmark.setOnClickListener {
            viewModel.toggleBookmark()
        }

        binding.btnTabs.setOnClickListener {
            if (binding.tabsPanel.isVisible) {
                hidePanels()
            } else {
                hidePanels()
                showTabsPanel()
            }
        }

        binding.btnMenu.setOnClickListener {
            if (panelBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                panelBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            } else {
                binding.tabsPanel.isVisible = false
                panelBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        binding.menuItemBookmarks.setOnClickListener {
            panelBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            showBookmarksPanel()
        }

        binding.menuItemHistory.setOnClickListener {
            panelBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            showHistoryPanel()
        }

        binding.menuItemClearData.setOnClickListener {
            panelBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            clearBrowsingData()
        }

        binding.menuItemDesktop.setOnClickListener {
            panelBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            toggleDesktopMode()
        }

        binding.menuItemShare.setOnClickListener {
            panelBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            shareCurrentUrl()
        }

        binding.menuItemCopyUrl.setOnClickListener {
            panelBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            copyCurrentUrl()
        }

        binding.menuItemCopyCookie?.setOnClickListener {
            panelBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            copyCurrentCookies()
        }

        binding.menuItemToggleAdBlock?.setOnClickListener {
            panelBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            isAdBlockEnabled = !isAdBlockEnabled
            val msg = if (isAdBlockEnabled) getString(com.personal.browser.R.string.ad_block_enabled) else getString(com.personal.browser.R.string.ad_block_disabled)
            com.google.android.material.snackbar.Snackbar.make(binding.root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
        }

        binding.menuItemUserScripts?.setOnClickListener {
            panelBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            com.google.android.material.snackbar.Snackbar.make(binding.root, "User Scripts Manager (Coming Soon)", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
        }

        binding.menuItemSettings?.setOnClickListener {
            panelBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            com.google.android.material.snackbar.Snackbar.make(binding.root, "Settings (Coming Soon)", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
        }

        binding.overlayDismiss.setOnClickListener { hidePanels() }
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
                hidePanels()
            } else {
                updateUrlDisplay()
            }
        }

        binding.btnRefresh.setOnClickListener {
            val tab = viewModel.activeTab
            if (tab?.isLoading == true) {
                currentWebView?.stopLoading()
            } else {
                currentWebView?.reload()
            }
        }
    }

    private fun setupNavigationButtons() {
        binding.btnBack.setOnClickListener {
            currentWebView?.let { if (it.canGoBack()) it.goBack() }
        }
        binding.btnForward.setOnClickListener {
            currentWebView?.let { if (it.canGoForward()) it.goForward() }
        }
    }

    private fun setupTabsRecycler() {
        tabsAdapter = TabsAdapter(
            onTabClick = { index ->
                viewModel.switchTab(index)
                hidePanels()
            },
            onTabClose = { index ->
                val tabId = viewModel.tabs.value?.getOrNull(index)?.id
                tabId?.let { webViews.remove(it)?.destroy() }
                viewModel.closeTab(index)
            }
        )
        binding.tabsRecycler.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = tabsAdapter
        }
    }

    private fun setupBookmarkHistoryRecycler() {
        bookmarkHistoryAdapter = BookmarkHistoryAdapter(
            onItemClick = { url ->
                loadUrl(url)
                hidePanels()
            },
            onItemDelete = { item ->
                // handled via ViewModel
            }
        )
        binding.bookmarkHistoryRecycler.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = bookmarkHistoryAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.tabs.observe(this) { tabs ->
            tabsAdapter.submitList(tabs.toList())
            binding.tabCountText.text = tabs.size.toString()
        }

        viewModel.activeTabIndex.observe(this) { index ->
            val tabs = viewModel.tabs.value ?: return@observe
            val tab = tabs.getOrNull(index) ?: return@observe

            switchToTab(tab.id, tab.url)
            updateUrlDisplay()
            updateNavigationState()
        }

        viewModel.isBookmarked.observe(this) { bookmarked ->
            binding.btnBookmark.isSelected = bookmarked
        }

        viewModel.bookmarks.observe(this) { bookmarks ->
            if (binding.bookmarkHistoryPanel.isVisible && binding.panelTitle.text == getString(com.personal.browser.R.string.bookmarks)) {
                bookmarkHistoryAdapter.submitBookmarks(bookmarks)
            }
        }

        viewModel.history.observe(this) { history ->
            if (binding.bookmarkHistoryPanel.isVisible && binding.panelTitle.text == getString(com.personal.browser.R.string.history)) {
                bookmarkHistoryAdapter.submitHistory(history)
            }
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
                        binding.btnRefresh.setImageResource(com.personal.browser.R.drawable.ic_close)
                        binding.progressBar.isVisible = true
                        updateNavigationState()
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (this@apply === currentWebView) {
                        viewModel.onPageFinished(url, view.title)
                        binding.btnRefresh.setImageResource(com.personal.browser.R.drawable.ic_refresh)
                        binding.progressBar.isVisible = false
                        updateNavigationState()
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

    private fun updateNavigationState() {
        val wv = currentWebView
        val canBack = wv?.canGoBack() == true
        val canForward = wv?.canGoForward() == true
        viewModel.onNavigationStateChanged(canBack, canForward)
        binding.btnBack.isEnabled = canBack
        binding.btnForward.isEnabled = canForward
        binding.btnBack.alpha = if (canBack) 1f else 0.38f
        binding.btnForward.alpha = if (canForward) 1f else 0.38f
    }

    private fun showTabsPanel() {
        hidePanels()
        binding.tabsPanel.isVisible = true
        binding.overlayDismiss.isVisible = true
    }

    private fun showBookmarksPanel() {
        hidePanels()
        binding.panelTitle.setText(com.personal.browser.R.string.bookmarks)
        viewModel.bookmarks.value?.let { bookmarkHistoryAdapter.submitBookmarks(it) }
        binding.bookmarkHistoryPanel.isVisible = true
        binding.overlayDismiss.isVisible = true
    }

    private fun showHistoryPanel() {
        hidePanels()
        binding.panelTitle.setText(com.personal.browser.R.string.history)
        viewModel.history.value?.let { bookmarkHistoryAdapter.submitHistory(it) }
        binding.bookmarkHistoryPanel.isVisible = true
        binding.overlayDismiss.isVisible = true
    }

    private fun hidePanels() {
        binding.tabsPanel.isVisible = false
        binding.bookmarkHistoryPanel.isVisible = false
        binding.overlayDismiss.isVisible = false
        panelBehavior.state = BottomSheetBehavior.STATE_HIDDEN
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

    private fun copyCurrentUrl() {
        val url = viewModel.activeTab?.url ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("URL", url))
        com.google.android.material.snackbar.Snackbar.make(
            binding.root, getString(com.personal.browser.R.string.url_copied), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).show()
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
                    panelBehavior.state == BottomSheetBehavior.STATE_EXPANDED -> {
                        panelBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    }
                    binding.tabsPanel.isVisible || binding.bookmarkHistoryPanel.isVisible -> {
                        hidePanels()
                    }
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
        imm.hideSoftInputFromWindow(binding.urlEditText.windowToken, 0)
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
