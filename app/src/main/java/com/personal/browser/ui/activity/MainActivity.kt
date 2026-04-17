package com.personal.browser.ui.activity

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.MenuItem
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import android.widget.PopupMenu
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.personal.browser.R
import com.personal.browser.databinding.ActivityMainBinding
import com.personal.browser.ui.adapter.BookmarkHistoryAdapter
import com.personal.browser.ui.adapter.TabsAdapter
import com.personal.browser.ui.viewmodel.BrowserViewModel
import com.personal.browser.utils.UrlUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BrowserViewModel by viewModels()
    private lateinit var prefs: SharedPreferences

    private val webViews = mutableMapOf<String, WebView>()
    private var currentWebView: WebView? = null

    // AdBlock state (refreshed on resume)
    private var isAdBlockEnabled = true
    companion object {
        private const val TAG = "MainActivity"
    }

    private val adBlockDomains = setOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "adservice.google.com", "amazon-adsystem.com", "criteo.com", "adnxs.com",
        "pagead2.googlesyndication.com", "ads.google.com", "adtago.s3.amazonaws.com"
    )

    // Settings result launcher
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (result.data?.getStringExtra("ACTION") == "CLEAR_DATA") {
                clearBrowsingData()
            }
        }
        applyThemeFromPrefs()
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeFromPrefs()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("browser_prefs", MODE_PRIVATE)

        // If "clear on exit" is enabled, clear all data now (at startup).
        // This is more reliable than clearing on exit because Android doesn't 
        // guarantee onStop/onDestroy will run. Every launch starts fresh.
        if (prefs.getBoolean(SettingsActivity.PREF_CLEAR_ON_EXIT, false)) {
            performClearDataSync()
        }

        setupCopyCookieButton()
        setupUrlBar()
        setupNavButtons()
        setupTopBarButtons()
        setupMenuButton()
        observeViewModel()
        setupBackHandler()
        setupSwipeRefresh()

        viewModel.initWithHomepage(getHomepageUrl())
    }

    override fun onResume() {
        super.onResume()
        currentWebView?.onResume()
        isAdBlockEnabled = prefs.getBoolean(SettingsActivity.PREF_AD_BLOCKING, true)
    }

    override fun onPause() {
        super.onPause()
        currentWebView?.onPause()
        binding.urlEditText.clearFocus()
    }

    override fun onStop() {
        super.onStop()
        // No-op: "Clear on exit" is now handled on start for reliability.
    }

    override fun onDestroy() {
        webViews.values.forEach { it.destroy() }
        webViews.clear()
        super.onDestroy()
    }

    // ── Theme ────────────────────────────────────────────────────────────────

    private fun applyThemeFromPrefs() {
        val p = getSharedPreferences("browser_prefs", MODE_PRIVATE)
        val dark = p.getBoolean(SettingsActivity.PREF_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    // ── Cookie button ────────────────────────────────────────────────────────

    private fun setupCopyCookieButton() {
        binding.btnCopyCookie.setOnClickListener { copyCurrentCookies() }
    }

    private fun updateCopyCookieVisibility() {
        val url = currentWebView?.url ?: ""
        val onWebPage = url.startsWith("http://") || url.startsWith("https://")
        binding.btnCopyCookie.isInvisible = !onWebPage
    }

    // ── Top-bar extra buttons ────────────────────────────────────────────────

    private fun setupTopBarButtons() {
        binding.btnNewTab.setOnClickListener {
            viewModel.openNewTab(getHomepageUrl())
        }
        binding.btnTabs.setOnClickListener { showTabsSheet() }
    }

    // ── Menu button (... overflow) ───────────────────────────────────────────

    private fun setupMenuButton() {
        binding.btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            try {
                val f = PopupMenu::class.java.getDeclaredField("mPopup")
                f.isAccessible = true
                val helper = f.get(popup)
                val m = helper.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                m.isAccessible = true
                m.invoke(helper, true)
            } catch (_: Exception) {}

            val menu = popup.menu
            menu.add(0, R.id.menu_new_tab,    0, getString(R.string.new_tab))
                .setIcon(R.drawable.ic_new_tab)
            menu.add(0, R.id.menu_bookmarks,  1, getString(R.string.bookmarks))
                .setIcon(R.drawable.ic_bookmark)
            menu.add(0, R.id.menu_history,    2, getString(R.string.history))
                .setIcon(R.drawable.ic_history)
            menu.add(0, R.id.menu_desktop,    3, getString(R.string.desktop_site))
                .setIcon(R.drawable.ic_desktop)
            menu.add(0, R.id.menu_share,      4, getString(R.string.share))
                .setIcon(R.drawable.ic_share)
            menu.add(0, R.id.menu_copy_url,   5, getString(R.string.copy_url))
                .setIcon(R.drawable.ic_copy)
            menu.add(0, R.id.menu_clear_data, 6, getString(R.string.clear_data))
                .setIcon(R.drawable.ic_delete)
            menu.add(0, R.id.menu_settings,   7, getString(R.string.settings))
                .setIcon(R.drawable.ic_settings)

            popup.setOnMenuItemClickListener { item -> onMenuItemSelected(item) }
            popup.show()
        }
    }

    private fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_new_tab    -> viewModel.openNewTab(getHomepageUrl())
            R.id.menu_bookmarks  -> showBookmarksSheet()
            R.id.menu_history    -> showHistorySheet()
            R.id.menu_desktop    -> toggleDesktopMode()
            R.id.menu_share      -> shareCurrentUrl()
            R.id.menu_copy_url   -> copyCurrentUrl()
            R.id.menu_clear_data -> clearBrowsingData()
            R.id.menu_settings   -> launchSettings("GENERAL")
        }
        return true
    }

    private fun launchSettings(mode: String) {
        settingsLauncher.launch(
            Intent(this, SettingsActivity::class.java).putExtra("SETTINGS_MODE", mode)
        )
    }

    // ── URL bar ──────────────────────────────────────────────────────────────

    private fun setupUrlBar() {
        binding.urlEditText.setOnEditorActionListener { tv, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                loadUrl(UrlUtils.processInput(tv.text.toString()))
                hideKeyboard()
                true
            } else false
        }
        binding.urlEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.urlEditText.selectAll() else updateUrlDisplay()
        }
    }

    // ── Bottom nav buttons ───────────────────────────────────────────────────

    private fun setupNavButtons() {
        binding.btnBack.setOnClickListener    { currentWebView?.goBack() }
        binding.btnForward.setOnClickListener { currentWebView?.goForward() }
        binding.btnRefresh.setOnClickListener { currentWebView?.reload() }
        binding.btnBookmark.setOnClickListener { viewModel.toggleBookmark() }
        binding.btnLogo.setOnClickListener    { launchSettings("GENERAL") }
    }

    private fun updateNavState() {
        val canBack    = currentWebView?.canGoBack()    == true
        val canForward = currentWebView?.canGoForward() == true
        binding.btnBack.alpha        = if (canBack)    1f else 0.4f
        binding.btnForward.alpha     = if (canForward) 1f else 0.4f
        binding.btnBack.isEnabled    = canBack
        binding.btnForward.isEnabled = canForward
        viewModel.onNavigationStateChanged(canBack, canForward)
        updateCopyCookieVisibility()
    }

    // ── ViewModel observers ──────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.activeTabIndex.observe(this) { index ->
            val tabs = viewModel.tabs.value ?: return@observe
            val tab  = tabs.getOrNull(index) ?: return@observe
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

    // ── Tab management ───────────────────────────────────────────────────────

    private fun switchToTab(tabId: String, url: String) {
        currentWebView?.let { binding.webViewContainer.removeView(it) }
        val webView = webViews.getOrPut(tabId) {
            createWebView().also { wv -> if (url.isNotEmpty()) wv.loadUrl(url) }
        }
        currentWebView = webView
        binding.webViewContainer.addView(
            webView, 0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        updateNavState()
        updateCopyCookieVisibility()
    }

    private fun showTabsSheet() {
        val sheet = BottomSheetDialog(this)
        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setPadding(0, 16, 0, 16)
        }
        val tabsAdapter = TabsAdapter(
            onTabClick = { index -> viewModel.switchTab(index); sheet.dismiss() },
            onTabClose = { index ->
                viewModel.closeTab(index)
                if ((viewModel.tabs.value?.size ?: 0) == 0) {
                    // All tabs closed? Let ViewModel handle it (it resets the last tab)
                    // If we want to dismiss the sheet, we should do it here if needed.
                }
            }
        )
        rv.adapter = tabsAdapter
        viewModel.tabs.observe(this) { tabs ->
            tabsAdapter.submitList(tabs)
            if (tabs.isEmpty()) sheet.dismiss()
        }
        
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        val headerBtn = android.widget.Button(this).apply {
            text = "+ New Tab"
            setOnClickListener { viewModel.openNewTab(getHomepageUrl()) }
        }
        container.addView(headerBtn, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.setMargins(32, 16, 32, 0) })
        container.addView(rv)
        sheet.setContentView(container)
        sheet.show()
    }

    // ── Bookmarks / History sheets ───────────────────────────────────────────

    private fun showBookmarksSheet() {
        val sheet   = BottomSheetDialog(this)
        val owner   = SheetLifecycleOwner()
        val rv      = buildSheetRecycler()
        val adapter = BookmarkHistoryAdapter(
            onItemClick  = { url -> loadUrl(url); sheet.dismiss() },
            onItemDelete = { item -> viewModel.deleteBookmarkItem(item) }
        )
        rv.adapter = adapter
        owner.start()
        viewModel.bookmarks.observe(owner) { adapter.submitBookmarks(it) }
        sheet.setOnDismissListener { owner.destroy() }
        sheet.setContentView(rv)
        sheet.show()
    }

    private fun showHistorySheet() {
        val sheet   = BottomSheetDialog(this)
        val owner   = SheetLifecycleOwner()
        val rv      = buildSheetRecycler()
        val adapter = BookmarkHistoryAdapter(
            onItemClick  = { url -> loadUrl(url); sheet.dismiss() },
            onItemDelete = { item -> viewModel.deleteHistoryItem(item) }
        )
        rv.adapter = adapter
        owner.start()
        viewModel.history.observe(owner) { adapter.submitHistory(it) }
        sheet.setOnDismissListener { owner.destroy() }
        sheet.setContentView(rv)
        sheet.show()
    }

    private fun buildSheetRecycler() = RecyclerView(this).apply {
        layoutManager = LinearLayoutManager(this@MainActivity)
        setPadding(0, 16, 0, 32)
    }

    private class SheetLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        fun start()   { registry.currentState = Lifecycle.State.STARTED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }

    // ── WebView factory ──────────────────────────────────────────────────────

    private fun createWebView(): WebView {
        return WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled    = true
                domStorageEnabled    = true
                loadWithOverviewMode = true
                useWideViewPort      = true
                setSupportZoom(true)
                builtInZoomControls  = true
                displayZoomControls  = false
                cacheMode            = WebSettings.LOAD_DEFAULT
                mixedContentMode     = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                mediaPlaybackRequiresUserGesture = false
                databaseEnabled      = true
                allowFileAccess      = true
                userAgentString      = settings.userAgentString.replace("; wv", "")
            }

            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (this@apply === currentWebView) {
                        viewModel.onPageStarted(url, view.title)
                        binding.progressBar.isVisible = true
                        updateNavState()
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)

                    // Patch pushState/replaceState FIRST so the SPA bridge is ready
                    // before any in-page JS navigates, then run user scripts.
                    injectSpaNavigationBridge(view)
                    injectScripts(view)

                    if (this@apply === currentWebView) {
                        viewModel.onPageFinished(url, view.title)
                        binding.progressBar.isInvisible = true
                        binding.swipeRefresh.isRefreshing = false
                        updateUrlDisplay()
                        updateNavState()
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest
                ): WebResourceResponse? {
                    if (isAdBlockEnabled) {
                        val host = request.url.host ?: ""
                        if (adBlockDomains.any { host.contains(it) }) {
                            return WebResourceResponse(
                                "text/plain", "UTF-8",
                                java.io.ByteArrayInputStream("".toByteArray())
                            )
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean {
                    val scheme = request.url.scheme ?: ""
                    return if (scheme == "http" || scheme == "https") false
                    else {
                        try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        catch (_: Exception) {}
                        true
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (this@apply === currentWebView) {
                        viewModel.onProgressChanged(newProgress)
                        binding.progressBar.progress = newProgress
                        binding.progressBar.isInvisible = (newProgress == 100)
                    }
                }

                override fun onReceivedTitle(view: WebView, title: String) {
                    if (this@apply === currentWebView) {
                        viewModel.onTitleReceived(title)
                    }
                }

                override fun onReceivedIcon(view: WebView, icon: Bitmap) {
                    super.onReceivedIcon(view, icon)
                    val tabId = webViews.entries.firstOrNull { it.value === view }?.key ?: return
                    viewModel.onReceivedIcon(tabId, icon)
                }
            }
        }
    }

    // ── User script injection ─────────────────────────────────────────────────

    /**
     * Inject all active user scripts into [view].
     * Uses a flag to ensure scripts only run once per document load.
     */
    private fun injectScripts(view: WebView) {
        if (!prefs.getBoolean(SettingsActivity.PREF_SCRIPTS_ENABLED, true)) return
        val scripts = loadActiveScripts()
        if (scripts.isEmpty()) return

        val scriptBlocks = scripts.joinToString("\n") { code ->
            val safe = code
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("\${", "\\\${")
            "(function(){\n" +
            "  try{\n" +
            safe + "\n" +
            "  }catch(e){ console.error('UserScript Error:',e); }\n" +
            "})();"
        }

        val js = """
            (function() {
              if (window.__toolBrowserScriptsInjected) return;
              window.__toolBrowserScriptsInjected = true;
              function __runAll() {
                $scriptBlocks
              }
              window.__toolBrowserRunScripts = __runAll;
              if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', __runAll, {once:true});
              } else {
                __runAll();
              }
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    /**
     * Inject a one-time bridge that monkey-patches pushState/replaceState and
     * listens for popstate. Note: automatic re-running of scripts on SPA navigation
     * is disabled by default to prevent double-injection, but the bridge remains
     * for manual use via window.__toolBrowserRunScripts().
     */
    private fun injectSpaNavigationBridge(view: WebView) {
        val js = """
            (function(){
              if(window.__toolBrowserBridgeInstalled) return;
              window.__toolBrowserBridgeInstalled = true;
              var _push    = history.pushState.bind(history);
              var _replace = history.replaceState.bind(history);
              function notify(){
                // Re-run is now manual or handled by individual scripts to avoid 
                // injecting multiple times on each interaction.
                console.log('SPA Navigation detected');
              }
              history.pushState    = function(){ _push.apply(history,arguments);    notify(); };
              history.replaceState = function(){ _replace.apply(history,arguments); notify(); };
              window.addEventListener('popstate', notify);
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    private fun loadActiveScripts(): List<String> {
        val json = prefs.getString(SettingsActivity.PREF_SCRIPTS_JSON, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                if (obj.optBoolean("enabled", true)) obj.optString("code").takeIf { it.isNotBlank() } else null
            }
        } catch (_: Exception) { emptyList() }
    }

    // ── URL loading & display ────────────────────────────────────────────────

    private fun loadUrl(url: String) {
        val tab = viewModel.activeTab
        if (tab == null) {
            viewModel.openNewTab(url)
        } else {
            val wv = webViews.getOrPut(tab.id) {
                createWebView().also { newWv ->
                    currentWebView?.let { binding.webViewContainer.removeView(it) }
                    currentWebView = newWv
                    binding.webViewContainer.addView(
                        newWv, 0,
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    )
                }
            }
            currentWebView = wv
            wv.loadUrl(url)
        }
        hideKeyboard()
    }

    private fun updateUrlDisplay() {
        if (!binding.urlEditText.hasFocus()) {
            val url = currentWebView?.url ?: viewModel.activeTab?.url ?: ""
            binding.urlEditText.setText(UrlUtils.getDisplayUrl(url))
        }
    }

    // ── Homepage URL ─────────────────────────────────────────────────────────

    private fun getHomepageUrl(): String =
        prefs.getString(SettingsActivity.PREF_HOMEPAGE_URL, SettingsActivity.DEFAULT_HOMEPAGE)
            ?: SettingsActivity.DEFAULT_HOMEPAGE

    // ── Clear data ───────────────────────────────────────────────────────────

    private fun clearBrowsingData() = performClearData(showToast = true)

    private fun performClearData(showToast: Boolean) {
        webViews.values.forEach { wv ->
            wv.clearCache(true)
            wv.clearHistory()
            wv.clearFormData()
            wv.clearSslPreferences()
        }
        WebStorage.getInstance().deleteAllData()
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies { cookieManager.flush() }
        val webViewDb = WebViewDatabase.getInstance(this)
        webViewDb.clearFormData()
        webViewDb.clearHttpAuthUsernamePassword()
        viewModel.clearHistory()
        if (showToast) showSnackbar(getString(R.string.data_cleared))
        updateNavState()
    }

    private fun performClearDataSync() {
        if (webViews.isEmpty()) {
            // Clear global disk cache via a temporary instance if no tabs are open yet
            try { WebView(this).clearCache(true) } catch (_: Exception) {}
        } else {
            webViews.values.forEach { wv ->
                wv.clearCache(true)
                wv.clearHistory()
                wv.clearFormData()
                wv.clearSslPreferences()
            }
        }
        WebStorage.getInstance().deleteAllData()
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
        WebViewDatabase.getInstance(this).apply {
            clearFormData()
            clearHttpAuthUsernamePassword()
        }
        runBlocking { viewModel.clearHistorySync() }
    }

    // ── Desktop mode ─────────────────────────────────────────────────────────

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
        showSnackbar(if (isDesktop) "Mobile mode" else "Desktop mode")
        wv.reload()
    }

    // ── Share & copy ─────────────────────────────────────────────────────────

    private fun shareCurrentUrl() {
        val url = viewModel.activeTab?.url ?: return
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) },
            getString(R.string.share_url)
        ))
    }

    private fun copyCurrentUrl() {
        val url = viewModel.activeTab?.url ?: return
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("URL", url))
        showSnackbar(getString(R.string.url_copied))
    }

    private fun copyCurrentCookies() {
        val url     = currentWebView?.url ?: viewModel.activeTab?.url ?: return
        val cookies = CookieManager.getInstance().getCookie(url)
        if (cookies.isNullOrBlank()) { showSnackbar("No cookies found for this page"); return }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Cookies", cookies))
        vibrate()
        showSnackbar(getString(R.string.cookies_copied))
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                .vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(50)
        }
    }

    // ── Snackbar ─────────────────────────────────────────────────────────────

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    // ── Back press ───────────────────────────────────────────────────────────

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.urlEditText.hasFocus() -> { hideKeyboard(); binding.urlEditText.clearFocus() }
                    currentWebView?.canGoBack() == true -> currentWebView?.goBack()
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
    }

    // ── SwipeRefresh ─────────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            currentWebView?.canScrollVertically(-1) ?: false
        }
        binding.swipeRefresh.setOnRefreshListener {
            currentWebView?.reload()
        }
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
    }

    // ── Keyboard ─────────────────────────────────────────────────────────────

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        binding.urlEditText.clearFocus()
    }
}
