package com.personal.browser.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.personal.browser.data.model.Tab
import com.personal.browser.data.repository.BookmarkRepository
import com.personal.browser.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _tabs = MutableLiveData<List<Tab>>(emptyList())
    val tabs: LiveData<List<Tab>> = _tabs

    private val _activeTabIndex = MutableLiveData(0)
    val activeTabIndex: LiveData<Int> = _activeTabIndex

    private val _isBookmarked = MutableLiveData(false)
    val isBookmarked: LiveData<Boolean> = _isBookmarked

    val bookmarks = bookmarkRepository.getAllBookmarks().asLiveData()
    val history   = historyRepository.getHistory().asLiveData()

    val activeTab: Tab?
        get() = _tabs.value?.getOrNull(_activeTabIndex.value ?: 0)

    /** Call this once from MainActivity.onCreate with the homepage URL from SharedPreferences. */
    fun initWithHomepage(homepageUrl: String) {
        if (_tabs.value.isNullOrEmpty()) {
            openNewTab(homepageUrl)
        }
    }

    fun openNewTab(url: String = "") {
        val list = _tabs.value?.toMutableList() ?: mutableListOf()
        list.add(Tab(url = url))
        _tabs.value = list
        _activeTabIndex.value = list.size - 1
        checkBookmarkStatus()
    }

    fun closeTab(index: Int) {
        val list = _tabs.value?.toMutableList() ?: return
        if (list.size <= 1) {
            // Keep at least one tab, reset it instead of removing
            list[0] = Tab()
            _tabs.value = list
            return
        }
        list.removeAt(index)
        _tabs.value = list
        val current = _activeTabIndex.value ?: 0
        if (current >= list.size) {
            _activeTabIndex.value = list.size - 1
        }
        checkBookmarkStatus()
    }

    fun switchTab(index: Int) {
        val list = _tabs.value ?: return
        if (index in list.indices) {
            _activeTabIndex.value = index
            checkBookmarkStatus()
        }
    }

    fun updateActiveTab(update: Tab.() -> Unit) {
        val list = _tabs.value?.toMutableList() ?: return
        val idx  = _activeTabIndex.value ?: 0
        if (idx in list.indices) {
            list[idx] = list[idx].copy().apply(update)
            _tabs.value = list
        }
    }

    fun onPageStarted(url: String, title: String?) {
        updateActiveTab {
            this.url      = url
            this.title    = title ?: "Loading…"
            this.isLoading = true
            this.progress  = 10
        }
        viewModelScope.launch {
            _isBookmarked.value = bookmarkRepository.isBookmarked(url)
        }
    }

    fun onPageFinished(url: String, title: String?) {
        updateActiveTab {
            this.url       = url
            this.title     = title ?: url
            this.isLoading = false
            this.progress  = 100
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
            this.canGoBack    = canGoBack
            this.canGoForward = canGoForward
        }
    }

    fun onTitleReceived(title: String?) {
        if (!title.isNullOrEmpty()) {
            updateActiveTab { this.title = title }
        }
    }

    fun onReceivedIcon(tabId: String, icon: Bitmap) {
        val list = _tabs.value?.toMutableList() ?: return
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
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }

    /**
     * Suspend version of clearHistory for use with runBlocking in onDestroy.
     */
    suspend fun clearHistorySync() {
        historyRepository.clearHistory()
    }

    /** Called when user swipes / deletes a bookmark from the bookmarks sheet. */
    fun deleteBookmarkItem(item: com.personal.browser.ui.adapter.BookmarkHistoryItem) {
        if (item is com.personal.browser.ui.adapter.BookmarkHistoryItem.BookmarkItem) {
            viewModelScope.launch {
                bookmarkRepository.removeBookmark(item.bookmark.url)
                val activeUrl = activeTab?.url ?: ""
                if (activeUrl.isNotEmpty()) {
                    _isBookmarked.value = bookmarkRepository.isBookmarked(activeUrl)
                }
            }
        }
    }

    /** Called when user swipes / deletes a history entry from the history sheet. */
    fun deleteHistoryItem(item: com.personal.browser.ui.adapter.BookmarkHistoryItem) {
        if (item is com.personal.browser.ui.adapter.BookmarkHistoryItem.HistoryItem) {
            viewModelScope.launch {
                historyRepository.deleteEntry(item.entry.id)
            }
        }
    }

    private fun checkBookmarkStatus() {
        val url = activeTab?.url ?: return
        viewModelScope.launch {
            _isBookmarked.value = bookmarkRepository.isBookmarked(url)
        }
    }
}
