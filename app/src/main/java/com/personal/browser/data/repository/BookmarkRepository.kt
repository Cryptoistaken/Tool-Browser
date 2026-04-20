package com.personal.browser.data.repository

import com.personal.browser.data.database.BookmarkDao
import com.personal.browser.data.model.Bookmark
import kotlinx.coroutines.flow.Flow

class BookmarkRepository(
    private val bookmarkDao: BookmarkDao
) {
    fun getAllBookmarks(): Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()

    suspend fun isBookmarked(url: String): Boolean =
        bookmarkDao.getBookmarkByUrl(url) != null

    suspend fun addBookmark(title: String, url: String) {
        bookmarkDao.insert(Bookmark(title = title, url = url))
    }

    suspend fun removeBookmark(url: String) {
        bookmarkDao.deleteByUrl(url)
    }

    suspend fun toggleBookmark(title: String, url: String): Boolean {
        return if (isBookmarked(url)) {
            removeBookmark(url)
            false
        } else {
            addBookmark(title, url)
            true
        }
    }
}
