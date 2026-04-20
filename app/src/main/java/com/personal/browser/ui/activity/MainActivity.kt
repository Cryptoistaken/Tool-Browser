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
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.snackbar.Snackbar
import com.personal.browser.R
import com.personal.browser.data.model.Bookmark
import com.personal.browser.data.model.HistoryEntry
import com.personal.browser.ui.screens.BookmarksSheet
import com.personal.browser.ui.screens.HistorySheet
import com.personal.browser.ui.screens.TabsSheet
import com.personal.browser.ui.viewmodel.BrowserViewModel
import com.personal.browser.utils.UrlUtils
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private val viewModel: BrowserViewModel by viewModels()
    private lateinit var prefs: SharedPreferences

    // WebView management — unchanged logic
    private val webViews = mutableMapOf<String, WebView>()
    private var currentWebView: WebView? = null

    // Root FrameLayout for WebViews (sits beneath the Compose overlay)
    private lateinit var webViewContainer: FrameLayout

    private var isAdBlockEnabled = true

    companion object {
        private const val TAG = "MainActivity"
    }

    private val adBlockDomains = setOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "adservice.google.com", "amazon-adsystem.com", "criteo.com", "adnxs.com",
        "pagead2.googlesyndication.com", "ads.google.com", "adtago.s3.amazonaws.com"
    )

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (result.data?.getStringExtra("ACTION") == "CLEAR_DATA") clearBrowsingData()
        }
        applyThemeFromPrefs()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeFromPrefs()
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("browser_prefs", MODE_PRIVATE)

        // WebView container lives in the window's decor, behind Compose
        webViewContainer = FrameLayout(this)
        setContentView(webViewContainer)

        if (prefs.getBoolean(SettingsActivity.PREF_CLEAR_ON_EXIT, false)) {
            performClearDataSync()
        }

        setupBackHandler()

        // Compose overlay rendered on top via setContent
        setContent {
            MaterialTheme {
                BrowserUi()
            }
        }

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
    }

    override fun onDestroy() {
        webViews.values.forEach { it.destroy() }
        webViews.clear()
        super.onDestroy()
    }

    // ── Compose UI ────────────────────────────────────────────────────────────

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun BrowserUi() {
        val tabs by viewModel.tabs.collectAsStateWithLifecycle()
        val activeTabIndex by viewModel.activeTabIndex.collectAsStateWithLifecycle()
        val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
        val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
        val history by viewModel.history.collectAsStateWithLifecycle()

        var showTabs by remember { mutableStateOf(false) }
        var showBookmarks by remember { mutableStateOf(false) }
        var showHistory by remember { mutableStateOf(false) }

        // Drive WebView switching from Compose state
        LaunchedEffect(activeTabIndex, tabs) {
            val tab = tabs.getOrNull(activeTabIndex) ?: return@LaunchedEffect
            switchToTab(tab.id, tab.url)
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            // The actual WebView is rendered in the FrameLayout behind Compose.
            // We use AndroidView as a transparent bridge to keep the Compose tree valid.
            AndroidView(
                factory = { webViewContainer },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Tabs bottom sheet ─────────────────────────────────────────────────
        if (showTabs) {
            ModalBottomSheet(
                onDismissRequest = { showTabs = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                TabsSheet(
                    tabs = tabs,
                    activeTabIndex = activeTabIndex,
                    onTabClick = { index -> viewModel.switchTab(index); showTabs = false },
                    onTabClose = { index -> viewModel.closeTab(index) },
                    onNewTab = { viewModel.openNewTab(getHomepageUrl()) }
                )
            }
        }

        // ── Bookmarks bottom sheet ────────────────────────────────────────────
        if (showBookmarks) {
            ModalBottomSheet(
                onDismissRequest = { showBookmarks = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                BookmarksSheet(
                    bookmarks = bookmarks,
                    onItemClick = { url -> loadUrl(url); showBookmarks = false },
                    onItemDelete = { bm: Bookmark -> viewModel.deleteBookmark(bm) }
                )
            }
        }

        // ── History bottom sheet ──────────────────────────────────────────────
        if (showHistory) {
            ModalBottomSheet(
                onDismissRequest = { showHistory = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                HistorySheet(
                    history = history,
                    onItemClick = { url -> loadUrl(url); showHistory = false },
                    onItemDelete = { entry: HistoryEntry -> viewModel.deleteHistoryEntry(entry) }
                )
            }
        }
    }

    // ── Tab management ────────────────────────────────────────────────────────

    private fun switchToTab(tabId: String, url: String) {
        currentWebView?.let { webViewContainer.removeView(it) }
        val webView = webViews.getOrPut(tabId) {
            createWebView().also { wv -> if (url.isNotEmpty()) wv.loadUrl(url) }
        }
        currentWebView = webView
        webViewContainer.addView(
            webView, 0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    // ── WebView factory ───────────────────────────────────────────────────────

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
                    }
                }
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    injectSpaNavigationBridge(view)
                    injectScripts(view)
                    if (this@apply === currentWebView) {
                        viewModel.onPageFinished(url, view.title)
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
                    }
                }
                override fun onReceivedTitle(view: WebView, title: String) {
                    if (this@apply === currentWebView) viewModel.onTitleReceived(title)
                }
                override fun onReceivedIcon(view: WebView, icon: Bitmap) {
                    super.onReceivedIcon(view, icon)
                    val tabId = webViews.entries.firstOrNull { it.value === view }?.key ?: return
                    viewModel.onReceivedIcon(tabId, icon)
                }
            }
        }
    }

    // ── Script injection (logic unchanged) ────────────────────────────────────

    private fun injectScripts(view: WebView) {
        if (!prefs.getBoolean(SettingsActivity.PREF_SCRIPTS_ENABLED, true)) return
        val scripts = loadActiveScripts()
        if (scripts.isEmpty()) return
        val scriptBlocks = scripts.joinToString("\n") { code ->
            val safe = code
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("\${", "\\\${")
            "(function(){\n  try{\n$safe\n  }catch(e){ console.error('UserScript Error:',e); }\n})();"
        }
        val js = """
            (function() {
              if (window.__toolBrowserScriptsInjected) return;
              window.__toolBrowserScriptsInjected = true;
              function __runAll() { $scriptBlocks }
              window.__toolBrowserRunScripts = __runAll;
              if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', __runAll, {once:true});
              } else { __runAll(); }
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    private fun injectSpaNavigationBridge(view: WebView) {
        val js = """
            (function(){
              if(window.__toolBrowserBridgeInstalled) return;
              window.__toolBrowserBridgeInstalled = true;
              var _push    = history.pushState.bind(history);
              var _replace = history.replaceState.bind(history);
              function notify(){ console.log('SPA Navigation detected'); }
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
                if (obj.optBoolean("enabled", true))
                    obj.optString("code").takeIf { it.isNotBlank() }
                else null
            }
        } catch (_: Exception) { emptyList() }
    }

    // ── URL loading ───────────────────────────────────────────────────────────

    private fun loadUrl(url: String) {
        val tab = viewModel.activeTab
        if (tab == null) {
            viewModel.openNewTab(url)
        } else {
            val wv = webViews.getOrPut(tab.id) {
                createWebView().also { newWv ->
                    currentWebView?.let { webViewContainer.removeView(it) }
                    currentWebView = newWv
                    webViewContainer.addView(newWv, 0,
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            }
            currentWebView = wv
            wv.loadUrl(url)
        }
    }

    // ── Homepage ──────────────────────────────────────────────────────────────

    private fun getHomepageUrl(): String =
        prefs.getString(SettingsActivity.PREF_HOMEPAGE_URL, SettingsActivity.DEFAULT_HOMEPAGE)
            ?: SettingsActivity.DEFAULT_HOMEPAGE

    // ── Clear data ────────────────────────────────────────────────────────────

    private fun clearBrowsingData() = performClearData(showToast = true)

    private fun performClearData(showToast: Boolean) {
        webViews.values.forEach { wv ->
            wv.clearCache(true); wv.clearHistory()
            wv.clearFormData(); wv.clearSslPreferences()
        }
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().also { it.removeAllCookies(null); it.flush() }
        WebViewDatabase.getInstance(this).also { it.clearFormData(); it.clearHttpAuthUsernamePassword() }
        viewModel.clearHistory()
    }

    private fun performClearDataSync() {
        if (webViews.isEmpty()) {
            try { WebView(this).clearCache(true) } catch (_: Exception) {}
        } else {
            webViews.values.forEach { wv ->
                wv.clearCache(true); wv.clearHistory()
                wv.clearFormData(); wv.clearSslPreferences()
            }
        }
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().also { it.removeAllCookies(null); it.flush() }
        WebViewDatabase.getInstance(this).also { it.clearFormData(); it.clearHttpAuthUsernamePassword() }
        runBlocking { viewModel.clearHistorySync() }
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    private fun applyThemeFromPrefs() {
        val p = getSharedPreferences("browser_prefs", MODE_PRIVATE)
        val dark = p.getBoolean(SettingsActivity.PREF_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    // ── Back press ────────────────────────────────────────────────────────────

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    currentWebView?.canGoBack() == true -> currentWebView?.goBack()
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
    }

    // ── Vibration ─────────────────────────────────────────────────────────────

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
}
