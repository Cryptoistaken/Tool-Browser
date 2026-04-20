package com.personal.browser.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.browser.data.database.BrowserDatabase
import com.personal.browser.data.model.Bookmark
import com.personal.browser.data.model.HistoryEntry
import com.personal.browser.data.model.Tab
import com.personal.browser.data.repository.BookmarkRepository
import com.personal.browser.data.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BrowserDatabase.getInstance(application)
    private val bookmarkRepository = BookmarkRepository(db.bookmarkDao())
    private val historyRepository = HistoryRepository(db.historyDao())

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkRepository.getAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val history: StateFlow<List<HistoryEntry>> = historyRepository.getHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeTab: Tab?
        get() = _tabs.value.getOrNull(_activeTabIndex.value)

    fun initWithHomepage(homepageUrl: String) {
        if (_tabs.value.isEmpty()) {
            openNewTab(homepageUrl)
        }
    }

    fun openNewTab(url: String = "") {
        val list = _tabs.value.toMutableList()
        list.add(Tab(url = url))
        _tabs.value = list
        _activeTabIndex.value = list.size - 1
        checkBookmarkStatus()
    }

    fun closeTab(index: Int) {
        val list = _tabs.value.toMutableList()
        if (list.size <= 1) {
            list[0] = Tab()
            _tabs.value = list
            return
        }
        list.removeAt(index)
        _tabs.value = list
        val current = _activeTabIndex.value
        if (current >= list.size) {
            _activeTabIndex.value = list.size - 1
        }
        checkBookmarkStatus()
    }

    fun switchTab(index: Int) {
        val list = _tabs.value
        if (index in list.indices) {
            _activeTabIndex.value = index
            checkBookmarkStatus()
        }
    }

    fun updateActiveTab(update: Tab.() -> Unit) {
        val list = _tabs.value.toMutableList()
        val idx = _activeTabIndex.value
        if (idx in list.indices) {
            list[idx] = list[idx].copy().apply(update)
            _tabs.value = list
        }
    }

    fun onPageStarted(url: String, title: String?) {
        updateActiveTab {
            this.url = url
            this.title = title ?: "Loading…"
            this.isLoading = true
            this.progress = 10
        }
        viewModelScope.launch {
            _isBookmarked.value = bookmarkRepository.isBookmarked(url)
        }
    }

    fun onPageFinished(url: String, title: String?) {
        updateActiveTab {
            this.url = url
            this.title = title ?: url
            this.isLoading = false
            this.progress = 100
        }
        if (url.isNotEmpty() && !url.startsWith("about:")) {
            viewModelScope.launch {
                historyRepository.addEntry(title ?: url, url)
                _isBookmarked.value = bookmarkRepository.isBookmarked(url)
            }
        }
    }

    fun onProgressChanged(progress: Int) {
        updateActiveTab { this.progress = progress }
    }

    fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean) {
        updateActiveTab {
            this.canGoBack = canGoBack
            this.canGoForward = canGoForward
        }
    }

    fun onTitleReceived(title: String?) {
        if (!title.isNullOrEmpty()) {
            updateActiveTab { this.title = title }
        }
    }

    fun onReceivedIcon(tabId: String, icon: Bitmap) {
        val list = _tabs.value.toMutableList()
        val idx = list.indexOfFirst { it.id == tabId }
        if (idx >= 0) {
            list[idx] = list[idx].copy(favicon = icon)
            _tabs.value = list
        }
    }

    fun toggleBookmark() {
        val tab = activeTab ?: return
        if (tab.url.isEmpty()) return
        viewModelScope.launch {
            val added = bookmarkRepository.toggleBookmark(tab.title, tab.url)
            _isBookmarked.value = added
        }
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepository.clearHistory() }
    }

    suspend fun clearHistorySync() {
        historyRepository.clearHistory()
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkRepository.removeBookmark(bookmark.url)
            val activeUrl = activeTab?.url ?: ""
            if (activeUrl.isNotEmpty()) {
                _isBookmarked.value = bookmarkRepository.isBookmarked(activeUrl)
            }
        }
    }

    fun deleteHistoryEntry(entry: HistoryEntry) {
        viewModelScope.launch { historyRepository.deleteEntry(entry.id) }
    }

    private fun checkBookmarkStatus() {
        val url = activeTab?.url ?: return
        viewModelScope.launch {
            _isBookmarked.value = bookmarkRepository.isBookmarked(url)
        }
    }
}
